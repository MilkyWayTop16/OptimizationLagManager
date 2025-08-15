package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ChunkManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<Player, Set<Chunk>> playerChunkCache = new HashMap<>();
    private final Map<String, Set<ChunkCoord>> preloadedChunkCache = new HashMap<>();
    private final Map<Player, Long> playerNetworkUsage = new HashMap<>();
    private final boolean isModernVersion;
    private final boolean isPurpur;
    private final Method loadChunkAsyncMethod;

    public ChunkManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        String version = Bukkit.getBukkitVersion().split("-")[0];
        isModernVersion = isVersionAtLeast(version, "1.19");
        isPurpur = Bukkit.getServer().getName().toLowerCase().contains("purpur");
        loadChunkAsyncMethod = getLoadChunkAsyncMethod();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startChunkOptimization();
        startChunkPreloading();
        loadPreloadedChunkCache();
        startPeriodicChunkCacheSave();
    }

    private boolean isVersionAtLeast(String currentVersion, String targetVersion) {
        try {
            String[] currentParts = currentVersion.split("\\.");
            String[] targetParts = targetVersion.split("\\.");
            int currentMajor = Integer.parseInt(currentParts[0]);
            int currentMinor = Integer.parseInt(currentParts[1]);
            int targetMajor = Integer.parseInt(targetParts[0]);
            int targetMinor = Integer.parseInt(targetParts[1]);
            return currentMajor > targetMajor || (currentMajor == targetMajor && currentMinor >= targetMinor);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка проверки версии сервера: " + e.getMessage());
            return false;
        }
    }

    private Method getLoadChunkAsyncMethod() {
        if (!isPurpur) return null;
        try {
            return World.class.getMethod("loadChunkAsync", int.class, int.class, boolean.class);
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("Метод loadChunkAsync недоступен в Purpur");
            return null;
        }
    }

    private void loadPreloadedChunkCache() {
        File file = new File(plugin.getDataFolder(), "preloaded-chunks.yml");
        if (!file.exists()) return;
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(new java.io.FileReader(file));
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String worldName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    List<String> coords = (List<String>) entry.getValue();
                    Set<ChunkCoord> chunkCoords = coords.stream()
                            .map(coord -> {
                                String[] parts = coord.split(",");
                                return new ChunkCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                            })
                            .collect(Collectors.toSet());
                    preloadedChunkCache.put(worldName, chunkCoords);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка загрузки кэша чанков: " + e.getMessage());
        }
    }

    public void savePreloadedChunkCache() {
        new BukkitRunnable() {
            @Override
            public void run() {
                File file = new File(plugin.getDataFolder(), "preloaded-chunks.yml");
                try {
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = new HashMap<>();
                    for (Map.Entry<String, Set<ChunkCoord>> entry : preloadedChunkCache.entrySet()) {
                        List<String> coords = entry.getValue().stream()
                                .map(coord -> coord.x + "," + coord.z)
                                .collect(Collectors.toList());
                        data.put(entry.getKey(), coords);
                    }
                    try (FileWriter writer = new FileWriter(file)) {
                        yaml.dump(data, writer);
                    }
                    if (configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().info("Кэш предзагруженных чанков сохранён в файл.");
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Ошибка сохранения кэша чанков: " + e.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void startPeriodicChunkCacheSave() {
        new BukkitRunnable() {
            @Override
            public void run() {
                savePreloadedChunkCache();
            }
        }.runTaskTimerAsynchronously(plugin, configManager.getChunkCacheSaveInterval(), configManager.getChunkCacheSaveInterval());
    }

    private void startChunkOptimization() {
        if (!configManager.isChunkOptimizationEnabled()) {
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Оптимизация чанков отключена в конфигурации");
            }
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    double tps = Bukkit.getTPS()[0];
                    int loadDistance = Math.min(configManager.getChunkLoadDistance(), configManager.getMaxViewDistance());
                    if (configManager.isDynamicLoadDistanceEnabled() && tps < configManager.getDynamicTpsThreshold()) {
                        loadDistance = Math.max(configManager.getMinLoadDistance(), loadDistance - 2);
                        if (configManager.isConsoleLoggingEnabled()) {
                            plugin.getLogger().info("TPS " + String.format("%.2f", tps) + " ниже порога, уменьшена дистанция прогрузки до " + loadDistance);
                        }
                    }

                    int playerCount = Bukkit.getOnlinePlayers().size();
                    int chunksPerTick = Math.max(10, Math.min(30, 100 / Math.max(1, playerCount / 10)));
                    int totalProcessed = 0;

                    for (World world : Bukkit.getWorlds()) {
                        if (world == null || world.getPlayers().isEmpty()) continue;
                        int serverViewDistance = world.getViewDistance();
                        int targetViewDistance = Math.min(loadDistance, serverViewDistance);
                        if (world.getViewDistance() != targetViewDistance) {
                            world.setViewDistance(targetViewDistance);
                            if (configManager.isConsoleLoggingEnabled()) {
                                plugin.getLogger().info("Установлена дистанция прогрузки для мира " + world.getName() + ": " + targetViewDistance);
                            }
                        }

                        int processed = 0;
                        for (Player player : world.getPlayers()) {
                            if (player == null) continue;
                            Set<Chunk> playerChunks = playerChunkCache.computeIfAbsent(player, k -> new HashSet<>());
                            for (Chunk chunk : playerChunks.toArray(new Chunk[0])) {
                                if (processed >= chunksPerTick) break;
                                if (chunk == null || !chunk.isLoaded()) {
                                    playerChunks.remove(chunk);
                                    if (configManager.isConsoleLoggingEnabled()) {
                                        plugin.getLogger().info("Удалён из кэша невалидный или выгруженный чанк для игрока " + player.getName());
                                    }
                                    continue;
                                }

                                int chunkX = player.getLocation().getBlockX() >> 4;
                                int chunkZ = player.getLocation().getBlockZ() >> 4;
                                int chunkDistance = Math.max(Math.abs(chunkX - chunk.getX()), Math.abs(chunkZ - chunk.getZ()));
                                if (chunkDistance > loadDistance) {
                                    if (isModernVersion) {
                                        try {
                                            Object loadLevel = chunk.getClass().getMethod("getLoadLevel").invoke(chunk);
                                            Class<?> loadLevelClass = Class.forName("org.bukkit.Chunk$LoadLevel");
                                            Object entityTicking = loadLevelClass.getField("ENTITY_TICKING").get(null);
                                            if (loadLevel != entityTicking) {
                                                chunk.unload(true);
                                                playerChunks.remove(chunk);
                                                if (configManager.isConsoleLoggingEnabled()) {
                                                    plugin.getLogger().info("Выгружен чанк (" + chunk.getX() + ", " + chunk.getZ() + ") для игрока " + player.getName() + " в мире " + world.getName());
                                                }
                                            }
                                        } catch (Exception e) {
                                            chunk.unload(true);
                                            playerChunks.remove(chunk);
                                            if (configManager.isConsoleLoggingEnabled()) {
                                                plugin.getLogger().info("Выгружен чанк (" + chunk.getX() + ", " + chunk.getZ() + ") для игрока " + player.getName() + " в мире " + world.getName());
                                            }
                                        }
                                    } else {
                                        chunk.unload(true);
                                        playerChunks.remove(chunk);
                                        if (configManager.isConsoleLoggingEnabled()) {
                                            plugin.getLogger().info("Выгружен чанк (" + chunk.getX() + ", " + chunk.getZ() + ") для игрока " + player.getName() + " в мире " + world.getName());
                                        }
                                    }
                                }
                                processed++;
                                totalProcessed++;
                            }
                        }
                    }
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    if (duration > 50 && configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().warning("Задача оптимизации чанков заняла " + duration + " мс, обработано чанков: " + totalProcessed);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка в задаче оптимизации чанков: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        }.runTaskTimer(plugin, 0L, configManager.getChunkUnloadDelay());
    }

    private void startChunkPreloading() {
        if (!configManager.isChunkOptimizationEnabled()) {
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Предзагрузка чанков отключена в конфигурации");
            }
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.nanoTime();
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    int chunksPerPlayer = Math.max(2, Math.min(10, 50 / Math.max(1, playerCount)));
                    int totalProcessed = 0;

                    for (World world : Bukkit.getWorlds()) {
                        if (world == null || world.getPlayers().isEmpty()) continue;
                        for (Player player : world.getPlayers()) {
                            if (player == null) continue;
                            int chunkX = player.getLocation().getBlockX() >> 4;
                            int chunkZ = player.getLocation().getBlockZ() >> 4;
                            int loadDistance = configManager.getMaxViewDistance();
                            Set<Chunk> playerChunks = playerChunkCache.computeIfAbsent(player, k -> new HashSet<>());
                            Set<ChunkCoord> preloadedChunks = preloadedChunkCache.computeIfAbsent(world.getName(), k -> new HashSet<>());

                            int processed = 0;
                            int maxDistance = loadDistance + 1;
                            for (int d = 0; d <= maxDistance && processed < chunksPerPlayer; d++) {
                                for (int x = -d; x <= d && processed < chunksPerPlayer; x++) {
                                    for (int z = -d; z <= d && processed < chunksPerPlayer; z++) {
                                        if (Math.abs(x) != d && Math.abs(z) != d) continue;
                                        int targetX = chunkX + x;
                                        int targetZ = chunkZ + z;
                                        if (Math.max(Math.abs(x), Math.abs(z)) <= loadDistance) {
                                            ChunkCoord coord = new ChunkCoord(targetX, targetZ);
                                            if (preloadedChunks.contains(coord) && world.isChunkLoaded(targetX, targetZ)) {
                                                Chunk chunk = world.getChunkAt(targetX, targetZ);
                                                playerChunks.add(chunk);
                                                if (configManager.isConsoleLoggingEnabled()) {
                                                    plugin.getLogger().info("Чанк (" + targetX + ", " + targetZ + ") из кэша добавлен для игрока " + player.getName() + " в мире " + world.getName());
                                                }
                                            } else if (configManager.isAsyncChunkLoadingEnabled()) {
                                                loadChunkSafely(world, targetX, targetZ, playerChunks, preloadedChunks, player);
                                            }
                                            processed++;
                                            totalProcessed++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    if (duration > 50 && configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().warning("Предзагрузка чанков заняла " + duration + " мс, обработано чанков: " + totalProcessed);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка предзагрузки чанков: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        }.runTaskTimer(plugin, 0L, configManager.getSendDelayTicks());
    }

    private void loadChunkSafely(World world, int x, int z, Set<Chunk> playerChunks, Set<ChunkCoord> preloadedChunks, Player player) {
        if (isPurpur && loadChunkAsyncMethod != null && configManager.isAsyncChunkLoadingEnabled()) {
            try {
                CompletableFuture<?> future = (CompletableFuture<?>) loadChunkAsyncMethod.invoke(world, x, z, false);
                future.thenAcceptAsync(chunk -> {
                    if (chunk != null) {
                        playerChunks.add((Chunk) chunk);
                        preloadedChunks.add(new ChunkCoord(x, z));
                        playerNetworkUsage.merge(player, 1024L * 1024L, Long::sum);
                        if (configManager.isConsoleLoggingEnabled()) {
                            plugin.getLogger().info("Асинхронно предзагружен чанк (" + x + ", " + z + ") для игрока " + player.getName() + " в мире " + world.getName());
                        }
                    }
                }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка асинхронной загрузки чанка (" + x + ", " + z + "): " + e.getMessage());
                loadChunkSynchronously(world, x, z, playerChunks, preloadedChunks, player);
            }
        } else {
            loadChunkSynchronously(world, x, z, playerChunks, preloadedChunks, player);
        }
    }

    private void loadChunkSynchronously(World world, int x, int z, Set<Chunk> playerChunks, Set<ChunkCoord> preloadedChunks, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    world.loadChunk(x, z, false);
                    Chunk chunk = world.getChunkAt(x, z);
                    playerChunks.add(chunk);
                    preloadedChunks.add(new ChunkCoord(x, z));
                    playerNetworkUsage.merge(player, 1024L * 1024L, Long::sum);
                    if (configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().info("Синхронно загружен чанк (" + x + ", " + z + ") для игрока " + player.getName() + " в мире " + world.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка синхронной загрузки чанка (" + x + ", " + z + "): " + e.getMessage());
                }
            }
        }.runTask(plugin);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!configManager.isChunkOptimizationEnabled()) return;
        try {
            Chunk chunk = event.getChunk();
            if (chunk == null || chunk.getWorld() == null) return;
            Set<ChunkCoord> preloadedChunks = preloadedChunkCache.computeIfAbsent(chunk.getWorld().getName(), k -> new HashSet<>());
            preloadedChunks.add(new ChunkCoord(chunk.getX(), chunk.getZ()));
            for (Player player : chunk.getWorld().getPlayers()) {
                if (player == null) continue;
                int chunkX = player.getLocation().getBlockX() >> 4;
                int chunkZ = player.getLocation().getBlockZ() >> 4;
                int chunkDistance = Math.max(Math.abs(chunkX - chunk.getX()), Math.abs(chunkZ - chunk.getZ()));
                if (chunkDistance <= configManager.getMaxViewDistance()) {
                    playerChunkCache.computeIfAbsent(player, k -> new HashSet<>()).add(chunk);
                    if (configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().info("Чанк (" + chunk.getX() + ", " + chunk.getZ() + ") добавлен в кэш игрока " + player.getName() + " в мире " + chunk.getWorld().getName());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки загрузки чанка: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!configManager.isTeleportChunkLoadingEnabled()) return;
        try {
            Player player = event.getPlayer();
            World world = event.getTo().getWorld();
            if (world == null) return;

            int chunkX = event.getTo().getBlockX() >> 4;
            int chunkZ = event.getTo().getBlockZ() >> 4;
            int maxRadius = configManager.getMaxViewDistance();
            final int[] chunksPerTick = {Math.max(1, configManager.getChunksPerTickTeleport())};
            long networkLimit = configManager.getPlayerSendMaxBytes();
            Set<Chunk> playerChunks = playerChunkCache.computeIfAbsent(player, k -> new HashSet<>());
            Set<ChunkCoord> preloadedChunks = preloadedChunkCache.computeIfAbsent(world.getName(), k -> new HashSet<>());

            new BukkitRunnable() {
                int currentTick = 0;
                int processedChunks = 0;

                @Override
                public void run() {
                    try {
                        double tps = Bukkit.getTPS()[0];
                        if (tps < configManager.getDynamicTpsThreshold()) {
                            chunksPerTick[0] = Math.max(1, chunksPerTick[0] / 2);
                            if (configManager.isConsoleLoggingEnabled()) {
                                plugin.getLogger().info("TPS " + String.format("%.2f", tps) + " ниже порога, уменьшено кол-во чанков за тик до " + chunksPerTick[0]);
                            }
                        }

                        long networkUsage = playerNetworkUsage.getOrDefault(player, 0L);
                        if (networkUsage > networkLimit) {
                            if (configManager.isConsoleLoggingEnabled()) {
                                plugin.getLogger().info("Превышен лимит сети для игрока " + player.getName() + ": " + networkUsage + " байт");
                            }
                            return;
                        }

                        for (int d = 0; d <= maxRadius && processedChunks < chunksPerTick[0]; d++) {
                            for (int x = -d; x <= d && processedChunks < chunksPerTick[0]; x++) {
                                for (int z = -d; z <= d && processedChunks < chunksPerTick[0]; z++) {
                                    if (Math.abs(x) != d && Math.abs(z) != d) continue;
                                    int targetX = chunkX + x;
                                    int targetZ = chunkZ + z;
                                    if (Math.max(Math.abs(x), Math.abs(z)) <= maxRadius) {
                                        ChunkCoord coord = new ChunkCoord(targetX, targetZ);
                                        if (preloadedChunks.contains(coord) && world.isChunkLoaded(targetX, targetZ)) {
                                            Chunk chunk = world.getChunkAt(targetX, targetZ);
                                            playerChunks.add(chunk);
                                            playerNetworkUsage.merge(player, 1024L * 1024L, Long::sum);
                                            if (configManager.isConsoleLoggingEnabled()) {
                                                plugin.getLogger().info("Чанк (" + targetX + ", " + targetZ + ") из кэша добавлен для игрока " + player.getName() + " в мире " + world.getName());
                                            }
                                        } else {
                                            loadChunkSafely(world, targetX, targetZ, playerChunks, preloadedChunks, player);
                                        }
                                        processedChunks++;
                                    }
                                }
                            }
                        }
                        currentTick++;
                        if (processedChunks >= chunksPerTick[0] * 10 || currentTick >= 20) {
                            cancel();
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Ошибка загрузки чанков при телепортации: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                    }
                }
            }.runTaskTimer(plugin, 0L, configManager.getSendDelayTicks());
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки телепортации: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private static class ChunkCoord {
        private final int x;
        private final int z;

        public ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }
}