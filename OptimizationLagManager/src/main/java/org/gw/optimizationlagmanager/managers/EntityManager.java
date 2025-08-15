package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<Chunk, List<Entity>> entityCache = new HashMap<>();

    public EntityManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        configManager.isConsoleLoggingEnabled();
        startEntityCleanup();
    }

    private void startEntityCleanup() {
        if (!configManager.isEntityOptimizationEnabled()) {
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Оптимизация сущностей отключена в конфигурации");
            }
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    int totalRemoved = 0;
                    for (World world : Bukkit.getWorlds()) {
                        if (world == null) continue;
                        for (Chunk chunk : world.getLoadedChunks()) {
                            if (chunk == null) continue;
                            List<Entity> entities = Arrays.asList(chunk.getEntities());
                            entityCache.put(chunk, entities);
                            if (entities.size() > configManager.getMaxEntitiesPerChunk()) {
                                for (Entity entity : entities) {
                                    if (entity instanceof Item && entity.getTicksLived() > configManager.getRemoveDropsAfter()) {
                                        if (configManager.getRedstonePlayerRadius() > 0 && !hasPlayersNearby(entity.getLocation())) {
                                            entity.remove();
                                            totalRemoved++;
                                            if (configManager.isConsoleLoggingEnabled()) {
                                                plugin.getLogger().info("Удалён предмет " + entity.getType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + world.getName() + ", возраст: " + entity.getTicksLived() + " тиков");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    if (duration > 50 && configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().warning("Очистка сущностей заняла " + duration + " мс, удалено сущностей: " + totalRemoved);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка в задаче очистки сущностей: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        }.runTaskTimer(plugin, 0L, 200L);
    }

    private boolean hasPlayersNearby(Location location) {
        int radius = configManager.getRedstonePlayerRadius();
        boolean hasPlayers = location.getWorld().getNearbyEntities(location, radius, radius, radius)
                .stream().anyMatch(e -> e instanceof Player);
        if (configManager.isConsoleLoggingEnabled()) {
            plugin.getLogger().info("Проверка игроков в радиусе " + radius + " от " + location.toString() + ": " + (hasPlayers ? "игроки найдены" : "игроки не найдены"));
        }
        return hasPlayers;
    }

    public boolean shouldDisableAI() {
        if (!configManager.isDisableAiLowTps()) return false;
        boolean disableAI = Bukkit.getTPS()[0] < configManager.getEntityTpsThreshold();
        if (disableAI && configManager.isConsoleLoggingEnabled()) {
            plugin.getLogger().info("TPS " + String.format("%.2f", Bukkit.getTPS()[0]) + " ниже порога " + configManager.getEntityTpsThreshold() + ", отключается ИИ мобов");
        }
        return disableAI;
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!configManager.isEntityOptimizationEnabled()) return;
        try {
            Chunk chunk = event.getLocation().getChunk();
            List<Entity> entities = entityCache.getOrDefault(chunk, Arrays.asList(chunk.getEntities()));
            if (entities.size() >= configManager.getMaxEntitiesPerChunk()) {
                event.setCancelled(true);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Отменён спавн сущности " + event.getEntityType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + chunk.getWorld().getName() + ": превышен лимит " + configManager.getMaxEntitiesPerChunk());
                }
            }
            if (event.getEntity() instanceof LivingEntity && shouldDisableAI()) {
                ((LivingEntity) event.getEntity()).setAI(false);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Отключён ИИ для сущности " + event.getEntityType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + chunk.getWorld().getName());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки спавна сущности: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}