package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.commands.AlertsCommand;
import org.gw.optimizationlagmanager.commands.CommandsHandler;
import org.gw.optimizationlagmanager.utils.HexColors;

import java.util.*;

public class PhysicsManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<String, Integer> trapdoorUpdates = new HashMap<>();
    private final Map<String, Long> trapdoorCooldowns = new HashMap<>();
    private final Map<String, Long> lastNotificationTime = new HashMap<>();
    private boolean isPhysicsGloballyDisabled = false;
    private static final long NOTIFICATION_COOLDOWN = 600_000L;
    private final Map<Chunk, Boolean> playerNearbyCache = new HashMap<>();
    private final Map<Chunk, Long> playerNearbyCacheTime = new HashMap<>();
    private final Map<Chunk, Integer> sandCache = new HashMap<>();
    private final Map<Chunk, Long> sandCacheTime = new HashMap<>();
    private final Map<Chunk, Integer> cobwebCache = new HashMap<>();
    private final Map<Chunk, Long> cobwebCacheTime = new HashMap<>();
    private static final long PLAYER_CACHE_DURATION = 500L;
    private final boolean consoleLoggingEnabled;
    private double cachedTps = 20.0;
    private long lastTpsCheck = 0;
    private static final long SAND_CACHE_DURATION = 60_000L;
    private static final long COBWEB_CACHE_DURATION = 60_000L;

    public PhysicsManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.consoleLoggingEnabled = configManager.isConsoleLoggingEnabled();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTpsMonitor();
        startFallingBlockMonitor();
        startMinecartMonitor();
        clearCaches();
    }

    private boolean hasPlayersNearby(Chunk chunk, int radius) {
        long currentTime = System.currentTimeMillis();
        Long lastChecked = playerNearbyCacheTime.get(chunk);
        if (lastChecked != null && (currentTime - lastChecked) < PLAYER_CACHE_DURATION) {
            return playerNearbyCache.getOrDefault(chunk, false);
        }

        Location center = chunk.getBlock(8, 64, 8).getLocation();
        boolean hasPlayers = false;
        for (Player player : chunk.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radius * radius) {
                hasPlayers = true;
                break;
            }
        }

        playerNearbyCache.put(chunk, hasPlayers);
        playerNearbyCacheTime.put(chunk, currentTime);
        if (consoleLoggingEnabled) {
            plugin.getLogger().info("Проверка игроков в радиусе " + radius + " от чанка (" + chunk.getX() + ", " + chunk.getZ() + "): " + (hasPlayers ? "игроки найдены" : "игроки не найдены"));
        }
        return hasPlayers;
    }

    private double getCachedTps() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTpsCheck >= 1000) {
            cachedTps = Bukkit.getTPS()[0];
            lastTpsCheck = currentTime;
        }
        return cachedTps;
    }

    private void startTpsMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double tps = getCachedTps();
                if (tps < configManager.getCriticalTpsThreshold() && !isPhysicsGloballyDisabled) {
                    isPhysicsGloballyDisabled = true;
                    trapdoorCooldowns.clear();
                    lastNotificationTime.clear();
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().warning("Критически низкий TPS (" + String.format("%.2f", tps) + "), физика люков временно отключена глобально!");
                    }
                    notifyAdmins(configManager.getMessages("tps-critical"), tps);
                } else if (tps >= configManager.getRedstoneTpsThreshold() && isPhysicsGloballyDisabled) {
                    isPhysicsGloballyDisabled = false;
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("TPS восстановлен (" + String.format("%.2f", tps) + "), физика люков снова включена");
                    }
                    notifyAdmins(configManager.getMessages("tps-restored"), tps);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void notifyAdmins(List<String> messages, double tps) {
        AlertsCommand alertsCommand = ((CommandsHandler) plugin.getCommand("olm").getExecutor()).getAlertsCommand();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("olm.redstonelag") && alertsCommand.isAlertsEnabled(player.getName())) {
                for (String message : messages) {
                    player.sendMessage(message.replace("{tps}", String.format("%.2f", tps)));
                }
            }
        }
        if (configManager.isConsoleNotificationsEnabled()) {
            for (String message : messages) {
                plugin.getLogger().info(HexColors.colorize(message.replace("{tps}", String.format("%.2f", tps)), true, plugin.getLogger()));
            }
        }
    }

    private void startFallingBlockMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isFallingBlockLimitEnabled() || Bukkit.getOnlinePlayers().isEmpty()) {
                    if (consoleLoggingEnabled) plugin.getLogger().info("Мониторинг падающих блоков приостановлен: нет игроков или отключен");
                    return;
                }

                long startTime = System.nanoTime();
                int scannedChunks = 0;
                List<Chunk> chunksToCheck = new ArrayList<>();

                for (World world : Bukkit.getWorlds()) {
                    if (configManager.getExcludedWorlds().contains(world.getName())) continue;
                    for (Chunk chunk : world.getLoadedChunks()) {
                        if (!hasPlayersNearby(chunk, configManager.getRedstonePlayerRadius())) continue;
                        chunksToCheck.add(chunk);
                        if (++scannedChunks >= 2) break;
                    }
                    if (scannedChunks >= 2) break;
                }

                if (!chunksToCheck.isEmpty()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (Chunk chunk : chunksToCheck) {
                                String chunkKey = chunk.getX() + "," + chunk.getZ();
                                int fallingBlockCount = 0;
                                List<Entity> fallingBlocks = new ArrayList<>();
                                for (Entity entity : chunk.getEntities()) {
                                    if (entity.getType() == EntityType.FALLING_BLOCK) {
                                        fallingBlocks.add(entity);
                                        fallingBlockCount++;
                                    }
                                }
                                int sandCount = countStaticSandBlocks(chunk);
                                int cobwebCount = countCobwebs(chunk);
                                if (fallingBlockCount > configManager.getMaxFallingBlocksPerChunk() ||
                                        sandCount > configManager.getMaxStaticSandPerChunk() ||
                                        cobwebCount > configManager.getMaxCobwebsPerChunk()) {
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            destroyFallingBlockLagMachine(chunkKey, chunk.getWorld().getName(), chunk, fallingBlocks, sandCount, cobwebCount));
                                }
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                }

                long duration = (System.nanoTime() - startTime) / 1_000_000;
                if (duration > 20 && consoleLoggingEnabled) {
                    plugin.getLogger().warning("Мониторинг падающих блоков занял " + duration + " мс, обработано чанков: " + scannedChunks);
                }
            }
        }.runTaskTimer(plugin, 0L, 600L);
    }

    private int countStaticSandBlocks(Chunk chunk) {
        long currentTime = System.currentTimeMillis();
        Long lastChecked = sandCacheTime.get(chunk);
        if (lastChecked != null && (currentTime - lastChecked) < SAND_CACHE_DURATION) {
            return sandCache.getOrDefault(chunk, 0);
        }

        int sandCount = 0;
        int minY = Math.max(chunk.getWorld().getMinHeight(), 0);
        int maxY = Math.min(chunk.getWorld().getMaxHeight(), 128);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    Block block = chunk.getBlock(bx, by, bz);
                    String blockType = block.getType().name();
                    if (blockType.equals("SAND") || blockType.equals("GRAVEL")) {
                        boolean isInWater = false;
                        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                            if (block.getRelative(face).getType().name().contains("WATER")) {
                                isInWater = true;
                                break;
                            }
                        }
                        if (!isInWater) {
                            sandCount++;
                        }
                    }
                }
            }
        }
        sandCache.put(chunk, sandCount);
        sandCacheTime.put(chunk, currentTime);
        return sandCount;
    }

    private int countCobwebs(Chunk chunk) {
        long currentTime = System.currentTimeMillis();
        Long lastChecked = cobwebCacheTime.get(chunk);
        if (lastChecked != null && (currentTime - lastChecked) < COBWEB_CACHE_DURATION) {
            return cobwebCache.getOrDefault(chunk, 0);
        }

        int cobwebCount = 0;
        int minY = Math.max(chunk.getWorld().getMinHeight(), 0);
        int maxY = Math.min(chunk.getWorld().getMaxHeight(), 128);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    Block block = chunk.getBlock(bx, by, bz);
                    if (block.getType().name().equals("COBWEB")) {
                        cobwebCount++;
                    }
                }
            }
        }
        cobwebCache.put(chunk, cobwebCount);
        cobwebCacheTime.put(chunk, currentTime);
        return cobwebCount;
    }

    private void clearCaches() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                playerNearbyCache.entrySet().removeIf(entry -> currentTime - playerNearbyCacheTime.get(entry.getKey()) > 60_000L);
                sandCache.entrySet().removeIf(entry -> currentTime - sandCacheTime.get(entry.getKey()) > 60_000L);
                cobwebCache.entrySet().removeIf(entry -> currentTime - cobwebCacheTime.get(entry.getKey()) > 60_000L);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Очищены кэши физики: " + playerNearbyCache.size() + " чанков в playerNearbyCache, " +
                            sandCache.size() + " чанков в sandCache, " + cobwebCache.size() + " чанков в cobwebCache");
                }
            }
        }.runTaskTimer(plugin, 0L, 1200L);
    }

    private int destroyFallingBlockLagMachine(String chunkKey, String world, Chunk chunk, List<Entity> fallingBlocks, int sandCount, int cobwebCount) {
        int destroyedCount = 0;
        int maxDestroyed = Math.min(configManager.getMaxDestroyedComponents(), 10);

        // Удаляем падающие блоки
        List<Entity> entitiesToRemove = new ArrayList<>(fallingBlocks);
        destroyedCount += destroyEntitiesInBatches(chunkKey, chunk, entitiesToRemove, maxDestroyed);

        // Удаляем песок/гравий
        if (configManager.isDestroyStaticSandEnabled() && sandCount > configManager.getMaxStaticSandPerChunk()) {
            List<Block> sandBlocks = new ArrayList<>();
            List<String> monitoredBlocks = configManager.getMonitoredBlocks();
            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int by = chunk.getWorld().getMinHeight(); by <= chunk.getWorld().getMaxHeight(); by++) {
                        Block block = chunk.getBlock(bx, by, bz);
                        String blockType = block.getType().name();
                        if (blockType.equals("SAND") || blockType.equals("GRAVEL")) {
                            boolean isInWater = block.getRelative(BlockFace.UP).getType().name().contains("WATER") ||
                                    block.getRelative(BlockFace.NORTH).getType().name().contains("WATER") ||
                                    block.getRelative(BlockFace.SOUTH).getType().name().contains("WATER") ||
                                    block.getRelative(BlockFace.EAST).getType().name().contains("WATER") ||
                                    block.getRelative(BlockFace.WEST).getType().name().contains("WATER");
                            if (!isInWater && hasNearbyLagComponents(block, monitoredBlocks)) {
                                sandBlocks.add(block);
                            }
                        }
                    }
                }
            }
            Collections.shuffle(sandBlocks);
            destroyedCount += destroyBlocksInBatches(chunkKey, chunk, sandBlocks, maxDestroyed - destroyedCount);
        }

        if (configManager.isCobwebDetectionEnabled() && cobwebCount > configManager.getMaxCobwebsPerChunk() && configManager.isDestroyCobwebsEnabled()) {
            List<Block> cobwebs = new ArrayList<>();
            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int by = chunk.getWorld().getMinHeight(); by <= chunk.getWorld().getMaxHeight(); by++) {
                        Block block = chunk.getBlock(bx, by, bz);
                        if (block.getType().name().equals("COBWEB")) {
                            cobwebs.add(block);
                        }
                    }
                }
            }
            Collections.shuffle(cobwebs);
            destroyedCount += destroyBlocksInBatches(chunkKey, chunk, cobwebs, maxDestroyed - destroyedCount);
        }

        return destroyedCount;
    }

    private int destroyEntitiesInBatches(String chunkKey, Chunk chunk, List<Entity> entities, int maxDestroyed) {
        final int[] destroyedCount = {0};
        if (entities.isEmpty()) return destroyedCount[0];

        int batchSize = Math.min(3, entities.size());
        List<Entity> batch = entities.subList(0, Math.min(batchSize, entities.size()));

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity entity : batch) {
                if (destroyedCount[0] >= maxDestroyed) return;
                entity.remove();
                destroyedCount[0]++;
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Удалён падающий блок в чанке (" + chunk.getX() + ", " + chunk.getZ() +
                            ") на координатах (" + entity.getLocation().getX() + ", " + entity.getLocation().getY() + ", " + entity.getLocation().getZ() + ")");
                }
            }
        });

        if (batchSize < entities.size()) {
            int finalDestroyedCount = destroyedCount[0];
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    destroyEntitiesInBatches(chunkKey, chunk, entities.subList(batchSize, entities.size()), maxDestroyed - finalDestroyedCount), 5L);
        }
        return destroyedCount[0];
    }

    private int destroyBlocksInBatches(String chunkKey, Chunk chunk, List<Block> blocks, int maxDestroyed) {
        final int[] destroyedCount = {0};
        if (blocks.isEmpty()) return destroyedCount[0];

        int batchSize = Math.min(3, blocks.size());
        List<Block> batch = blocks.subList(0, Math.min(batchSize, blocks.size()));

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Block block : batch) {
                if (destroyedCount[0] >= maxDestroyed) return;
                block.setType(org.bukkit.Material.AIR);
                destroyedCount[0]++;
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Удалён блок " + block.getType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() +
                            ") на координатах (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
                }
            }
        });

        if (batchSize < blocks.size()) {
            int finalDestroyedCount = destroyedCount[0];
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    destroyBlocksInBatches(chunkKey, chunk, blocks.subList(batchSize, blocks.size()), maxDestroyed - finalDestroyedCount), 5L);
        }
        return destroyedCount[0];
    }

    private void notifyLagMachineDetected(String chunkKey, String world, int x, int z, int destroyedCount, String type) {
        if (destroyedCount == 0) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Лаг-машина (" + type + ") в чанке (" + chunkKey + ") обнаружена, но компоненты не удалены");
            }
            return;
        }

        if (type.equals("падающие блоки/паутины")) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Уведомление о лаг-машине (" + type + ") в чанке (" + chunkKey + ") пропущено: тип связан с падающими блоками");
            }
            return;
        }

        Long lastNotified = lastNotificationTime.get(chunkKey);
        long currentTime = System.currentTimeMillis();
        if (lastNotified != null && (currentTime - lastNotified) < NOTIFICATION_COOLDOWN) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Уведомление о лаг-машине (" + type + ") в чанке (" + chunkKey + ") пропущено: на кулдауне");
            }
            return;
        }
        lastNotificationTime.put(chunkKey, currentTime);

        AlertsCommand alertsCommand = ((CommandsHandler) plugin.getCommand("olm").getExecutor()).getAlertsCommand();
        configManager.getMessages("alerts.lag-machine-detected").forEach(message -> {
            String formattedMessage = message
                    .replace("{world}", world)
                    .replace("{x}", String.valueOf(x * 16))
                    .replace("{y}", String.valueOf(64))
                    .replace("{z}", String.valueOf(z * 16))
                    .replace("{count}", String.valueOf(destroyedCount))
                    .replace("{type}", type);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("olm.redstonelag") && alertsCommand.isAlertsEnabled(player.getName())) {
                    player.sendMessage(formattedMessage);
                }
            }
            if (configManager.isConsoleNotificationsEnabled()) {
                plugin.getLogger().info(HexColors.colorize(formattedMessage, true, plugin.getLogger()));
            }
        });

        if (consoleLoggingEnabled) {
            plugin.getLogger().warning("Обнаружена лаг-машина (" + type + ") в чанке (" + chunkKey + ") в мире " + world +
                    ", удалено " + destroyedCount + " компонентов");
        }
    }

    private boolean hasNearbyLagComponents(Block block, List<String> monitoredBlocks) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block adjacent = block.getRelative(face);
            String adjacentType = adjacent.getType().name();
            if (monitoredBlocks.contains(adjacentType) || adjacentType.equals("COBWEB")) {
                return true;
            }
        }
        return false;
    }

    private void startMinecartMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isMinecartLimitEnabled() || Bukkit.getOnlinePlayers().isEmpty()) {
                    if (consoleLoggingEnabled) plugin.getLogger().info("Мониторинг вагонеток приостановлен: нет игроков или отключен");
                    return;
                }

                long startTime = System.nanoTime();
                int scannedChunks = 0;
                List<Chunk> chunksToCheck = new ArrayList<>();

                for (World world : Bukkit.getWorlds()) {
                    if (configManager.getExcludedWorlds().contains(world.getName())) continue;
                    for (Chunk chunk : world.getLoadedChunks()) {
                        if (!hasPlayersNearby(chunk, configManager.getRedstonePlayerRadius())) continue;
                        chunksToCheck.add(chunk);
                        if (++scannedChunks >= 2) break;
                    }
                    if (scannedChunks >= 2) break;
                }

                if (!chunksToCheck.isEmpty()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (Chunk chunk : chunksToCheck) {
                                int minecartCount = 0;
                                List<Entity> minecarts = new ArrayList<>();
                                for (Entity entity : chunk.getEntities()) {
                                    if (entity.getType() == EntityType.MINECART) {
                                        minecarts.add(entity);
                                        minecartCount++;
                                    }
                                }
                                if (minecartCount > configManager.getMaxMinecartsPerChunk()) {
                                    String chunkKey = chunk.getX() + "," + chunk.getZ();
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            destroyMinecartLagMachine(chunkKey, chunk.getWorld().getName(), chunk, minecarts));
                                }
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                }

                long duration = (System.nanoTime() - startTime) / 1_000_000;
                if (duration > 20 && consoleLoggingEnabled) {
                    plugin.getLogger().warning("Мониторинг вагонеток занял " + duration + " мс, обработано чанков: " + scannedChunks);
                }
            }
        }.runTaskTimer(plugin, 0L, 600L);
    }

    private int destroyMinecartLagMachine(String chunkKey, String world, Chunk chunk, List<Entity> minecarts) {
        int destroyedCount = 0;
        int maxDestroyed = Math.min(configManager.getMaxDestroyedComponents(), 10);

        List<Entity> entitiesToRemove = new ArrayList<>(minecarts);
        destroyedCount += destroyEntitiesInBatches(chunkKey, chunk, entitiesToRemove, maxDestroyed);

        if (configManager.isRailDetectionEnabled()) {
            List<Block> rails = new ArrayList<>();
            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    for (int by = chunk.getWorld().getMinHeight(); by <= chunk.getWorld().getMaxHeight(); by++) {
                        Block block = chunk.getBlock(bx, by, bz);
                        String blockType = block.getType().name();
                        if (blockType.equals("POWERED_RAIL") || blockType.equals("RAIL") || blockType.equals("ACTIVATOR_RAIL") || blockType.equals("DETECTOR_RAIL")) {
                            rails.add(block);
                        }
                    }
                }
            }
            if (rails.size() > configManager.getMaxRailsPerChunk() && configManager.isDestroyRailsEnabled()) {
                Collections.shuffle(rails);
                destroyedCount += destroyBlocksInBatches(chunkKey, chunk, rails, maxDestroyed - destroyedCount);
            }
        }

        notifyLagMachineDetected(chunkKey, world, chunk.getX(), chunk.getZ(), destroyedCount, "вагонетки/рельсы");
        return destroyedCount;
    }

    public boolean isFallingBlocksDisabled(World world) {
        if (configManager.getExcludedWorlds().contains(world.getName())) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Физика падающих блоков разрешена в мире " + world.getName() + " (в исключениях)");
            }
            return false;
        }
        boolean disabled = configManager.isDisableFallingBlocks();
        if (disabled && consoleLoggingEnabled) {
            plugin.getLogger().info("Физика падающих блоков отключена в мире " + world.getName());
        }
        return disabled;
    }

    public boolean isWaterFlowDisabled(World world) {
        if (configManager.getExcludedWorlds().contains(world.getName())) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Течение воды разрешено в мире " + world.getName() + " (в исключениях)");
            }
            return false;
        }
        boolean disabled = configManager.isDisableWaterFlow();
        if (disabled && consoleLoggingEnabled) {
            plugin.getLogger().info("Течение воды отключено в мире " + world.getName());
        }
        return disabled;
    }

    public boolean isLavaFlowDisabled(World world) {
        if (configManager.getExcludedWorlds().contains(world.getName())) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Течение лавы разрешено в мире " + world.getName() + " (в исключениях)");
            }
            return false;
        }
        boolean disabled = configManager.isDisableLavaFlow();
        if (disabled && consoleLoggingEnabled) {
            plugin.getLogger().info("Течение лавы отключено в мире " + world.getName());
        }
        return disabled;
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!configManager.isPhysicsOptimizationEnabled()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Оптимизация физики отключена в конфигурации");
            }
            return;
        }
        try {
            World world = event.getBlock().getWorld();
            if (isWaterFlowDisabled(world) && event.getBlock().getType().name().contains("WATER")) {
                event.setCancelled(true);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Отменено течение воды в блоке " + event.getBlock().getType().name() + " в мире " + world.getName() + " на координатах " + event.getBlock().getLocation().toString());
                }
            }
            if (isLavaFlowDisabled(world) && event.getBlock().getType().name().contains("LAVA")) {
                event.setCancelled(true);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Отменено течение лавы в блоке " + event.getBlock().getType().name() + " в мире " + world.getName() + " на координатах " + event.getBlock().getLocation().toString());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки события течения блока: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!configManager.isPhysicsOptimizationEnabled()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Оптимизация физики отключена в конфигурации");
            }
            return;
        }
        try {
            if (isFallingBlocksDisabled(event.getEntity().getWorld()) && event.getEntity().getType() == EntityType.FALLING_BLOCK) {
                event.setCancelled(true);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Отменено падение блока " + event.getEntity().getType().name() + " в мире " + event.getEntity().getWorld().getName() + " на координатах " + event.getEntity().getLocation().toString());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки события падения блока: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (!configManager.isPhysicsOptimizationEnabled() || isPhysicsGloballyDisabled) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Оптимизация физики отключена или физика глобально заблокирована, обновление блока " +
                        event.getBlock().getType().name() + " отменено");
            }
            event.setCancelled(true);
            return;
        }

        String blockType = event.getBlock().getType().name();
        // Проверяем, является ли блок потенциально падающим
        if (isFallingBlocksDisabled(event.getBlock().getWorld()) &&
                (blockType.equals("SAND") || blockType.equals("GRAVEL") ||
                        blockType.equals("ANVIL") || blockType.contains("CONCRETE_POWDER"))) {
            event.setCancelled(true);
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Отменено событие физики для падающего блока " + blockType +
                        " в мире " + event.getBlock().getWorld().getName() +
                        " на координатах " + event.getBlock().getLocation().toString());
            }
            return;
        }

        if (blockType.equals("LEVER") || blockType.equals("BUTTON") || blockType.equals("REDSTONE_WIRE") ||
                blockType.equals("REDSTONE_TORCH") || blockType.equals("REPEATER") || blockType.equals("COMPARATOR") ||
                blockType.equals("PISTON") || blockType.equals("STICKY_PISTON") || blockType.equals("OBSERVER")) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Событие физики для блока " + blockType + " в чанке (" +
                        event.getBlock().getChunk().getX() + ", " + event.getBlock().getChunk().getZ() + ") пропущено");
            }
            return;
        }

        try {
            if (configManager.isTrapdoorLimitEnabled() && blockType.equals("TRAPDOOR")) {
                Chunk chunk = event.getBlock().getChunk();
                String chunkKey = chunk.getX() + "," + chunk.getZ();
                Long cooldown = trapdoorCooldowns.get(chunkKey);
                if (cooldown != null && System.currentTimeMillis() < cooldown) {
                    event.setCancelled(true);
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("Физика люка в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") на кулдауне");
                    }
                    return;
                }

                int updates = trapdoorUpdates.getOrDefault(chunkKey, 0) + 1;
                trapdoorUpdates.put(chunkKey, updates);
                if (updates > configManager.getMaxTrapdoorUpdatesPerTick()) {
                    event.setCancelled(true);
                    long cooldownMillis = configManager.getLagDetectionCooldown() * 50L;
                    trapdoorCooldowns.put(chunkKey, System.currentTimeMillis() + cooldownMillis);
                    notifyLagMachineDetected(chunkKey, event.getBlock().getWorld().getName(),
                            chunk.getX(), chunk.getZ(),
                            destroyLagMachineComponents(chunkKey, event.getBlock().getWorld().getName(),
                                    chunk.getX(), chunk.getZ()), "люки");
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().warning("Превышен лимит обновлений физики для люка в чанке (" +
                                chunk.getX() + ", " + chunk.getZ() +
                                "), кулдаун на " + (cooldownMillis / 1000) + " секунд");
                    }
                }
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    trapdoorUpdates.remove(chunkKey);
                }, 1L);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки события физики: " +
                    (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private int destroyLagMachineComponents(String chunkKey, String world, int x, int z) {
        if (!configManager.isDestroyComponentsEnabled()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Разрушение компонентов отключено в конфиге для чанка (" + chunkKey + ")");
            }
            return 0;
        }

        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            plugin.getLogger().warning("Мир " + world + " не найден для чанка (" + chunkKey + ")");
            return 0;
        }

        Chunk chunk = bukkitWorld.getChunkAt(x, z);
        List<Block> components = new ArrayList<>();
        List<String> monitoredBlocks = configManager.getMonitoredBlocks();

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                for (int by = bukkitWorld.getMinHeight(); by <= bukkitWorld.getMaxHeight(); by++) {
                    Block block = chunk.getBlock(bx, by, bz);
                    String blockType = block.getType().name();
                    if (blockType.equals("TRAPDOOR") && monitoredBlocks.contains(blockType)) {
                        components.add(block);
                    }
                }
            }
        }

        if (components.isEmpty()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Не найдено люков для удаления в чанке (" + chunkKey + ")");
            }
            return 0;
        }

        int maxDestroyed = Math.min(configManager.getMaxDestroyedComponents(), 10);
        Collections.shuffle(components);
        return destroyBlocksInBatches(chunkKey, chunk, components, maxDestroyed);
    }
}