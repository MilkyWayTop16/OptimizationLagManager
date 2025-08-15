package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;

public class WorldGenManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;

    public WorldGenManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        configManager.isConsoleLoggingEnabled();
        startWorldGenOptimization();
    }

    private void startWorldGenOptimization() {
        if (!configManager.isWorldGenOptimizationEnabled() || !configManager.isPregenerateChunks()) {
            return;
        }

        new BukkitRunnable() {
            int x = -configManager.getPregenerateRadius() / 16;
            int z = -configManager.getPregenerateRadius() / 16;
            int chunksProcessed = 0;

            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    int chunksPerTick = Math.max(1, Math.min(configManager.getChunksPerTick(), 5 / Math.max(1, playerCount / 20))); // Уменьшено
                    int chunksThisTick = 0;

                    double tps = Bukkit.getTPS()[0];
                    if (tps < configManager.getDynamicTpsThreshold()) {
                        chunksPerTick = Math.max(1, chunksPerTick / 2);
                        if (configManager.isConsoleLoggingEnabled()) {
                            plugin.getLogger().info("TPS " + String.format("%.2f", tps) + " ниже порога, уменьшено кол-во чанков за тик до " + chunksPerTick);
                        }
                    }

                    for (World world : Bukkit.getWorlds()) {
                        if (world == null || world.getPlayers().isEmpty()) continue;
                        for (int i = 0; i < chunksPerTick && x <= configManager.getPregenerateRadius() / 16; i++) {
                            try {
                                if (!world.isChunkLoaded(x, z)) {
                                    world.loadChunk(x, z, true);
                                    chunksProcessed++;
                                    chunksThisTick++;
                                    if (configManager.isConsoleLoggingEnabled()) {
                                        plugin.getLogger().info("Сгенерирован чанк (" + x + ", " + z + ") в мире " + world.getName() + ", всего обработано: " + chunksProcessed);
                                    }
                                }
                                z++;
                                if (z > configManager.getPregenerateRadius() / 16) {
                                    z = -configManager.getPregenerateRadius() / 16;
                                    x++;
                                }
                                if (x > configManager.getPregenerateRadius() / 16) {
                                    plugin.getLogger().info("Предварительная генерация чанков завершена. Обработано: " + chunksProcessed);
                                    cancel();
                                }
                            } catch (Exception e) {
                                plugin.getLogger().warning("Ошибка генерации чанка (" + x + ", " + z + ") в мире " + world.getName() + ": " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                            }
                        }
                    }
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    if (duration > 50 && configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().warning("Генерация чанков заняла " + duration + " мс, обработано за тик: " + chunksThisTick);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка в задаче генерации мира: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }
}