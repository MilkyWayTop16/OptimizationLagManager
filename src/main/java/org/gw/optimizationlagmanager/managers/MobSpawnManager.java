package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MobSpawnManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<Chunk, Integer> mobCountCache = new HashMap<>();

    public MobSpawnManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        configManager.isConsoleLoggingEnabled();
        startMobCountUpdate();
    }

    private void startMobCountUpdate() {
        if (!configManager.isMobSpawningEnabled()) {
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Оптимизация спавна мобов отключена в конфигурации");
            }
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    int totalChunksUpdated = 0;
                    for (World world : Bukkit.getWorlds()) {
                        if (world == null) continue;
                        for (Chunk chunk : world.getLoadedChunks()) {
                            if (chunk == null) continue;
                            int mobCount = (int) Arrays.stream(chunk.getEntities())
                                    .filter(e -> e instanceof LivingEntity).count();
                            mobCountCache.put(chunk, mobCount);
                            totalChunksUpdated++;
                            if (configManager.isConsoleLoggingEnabled()) {
                                plugin.getLogger().info("Обновлён счётчик мобов в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + world.getName() + ": " + mobCount + " мобов");
                            }
                        }
                    }
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    if (duration > 50 && configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().warning("Обновление счётчика мобов заняло " + duration + " мс, обработано чанков: " + totalChunksUpdated);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка в задаче обновления счётчика мобов: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        }.runTaskTimer(plugin, 0L, 100L);
    }

    public boolean shouldReduceSpawnRate() {
        if (!configManager.isSpawnRateReductionEnabled()) return false;
        boolean reduce = Bukkit.getTPS()[0] < configManager.getTpsThreshold();
        if (reduce && configManager.isConsoleLoggingEnabled()) {
            plugin.getLogger().info("TPS " + String.format("%.2f", Bukkit.getTPS()[0]) + " ниже порога " + configManager.getTpsThreshold() + ", уменьшается частота спавна мобов");
        }
        return reduce;
    }

    public double getSpawnReductionMultiplier() {
        double multiplier = 1.0 + (configManager.getReductionPercentage() / 100.0);
        if (configManager.isConsoleLoggingEnabled()) {
            plugin.getLogger().info("Множитель уменьшения спавна: " + multiplier);
        }
        return multiplier;
    }

    public int getMaxMobsPerChunk() {
        return configManager.getMaxMobsPerChunk();
    }

    public int getMaxMobsPerSpawner() {
        return configManager.getMaxMobsPerSpawner();
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!configManager.isMobSpawningEnabled()) return;
        try {
            Chunk chunk = event.getLocation().getChunk();
            int mobCount = mobCountCache.getOrDefault(chunk, chunk.getEntities().length);

            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
                if (configManager.isSpawnerLimitEnabled()) {
                    int radius = configManager.getSpawnerRadiusCheck();
                    long nearbyMobs = event.getLocation().getWorld().getNearbyEntities(event.getLocation(), radius, radius, radius)
                            .stream().filter(e -> e instanceof LivingEntity).count();
                    if (nearbyMobs >= getMaxMobsPerSpawner()) {
                        event.setCancelled(true);
                        if (configManager.isConsoleLoggingEnabled()) {
                            plugin.getLogger().info("Отменён спавн моба " + event.getEntityType().name() + " из спавнера в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + chunk.getWorld().getName() + ": превышен лимит " + getMaxMobsPerSpawner());
                        }
                    }
                }
            } else if (mobCount >= getMaxMobsPerChunk()) {
                event.setCancelled(true);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Отменён спавн моба " + event.getEntityType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + chunk.getWorld().getName() + ": превышен лимит " + getMaxMobsPerChunk());
                }
            }

            if (shouldReduceSpawnRate() && Math.random() < (getSpawnReductionMultiplier() - 1.0)) {
                event.setCancelled(true);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Отменён спавн моба " + event.getEntityType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + chunk.getWorld().getName() + " из-за уменьшения частоты спавна");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки спавна моба: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}