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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.commands.AlertsCommand;
import org.gw.optimizationlagmanager.commands.CommandsHandler;
import org.gw.optimizationlagmanager.utils.HexColors;

import java.util.*;

public class RedstoneManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final WeakHashMap<Chunk, Integer> redstoneUpdates = new WeakHashMap<>();
    private final WeakHashMap<Chunk, Integer> redstoneActivityCounter = new WeakHashMap<>();
    private final WeakHashMap<Chunk, Long> redstoneCooldown = new WeakHashMap<>();
    private final WeakHashMap<Chunk, Long> lastNotificationTime = new WeakHashMap<>();
    private final Set<Chunk> lagMachineChunks = new HashSet<>();
    private final Set<Chunk> protectedChunks = new HashSet<>();
    private final Map<Chunk, Boolean> playerNearbyCache = new HashMap<>();
    private final Map<Chunk, Long> playerNearbyCacheTime = new HashMap<>();
    private final Map<Chunk, RedstoneScanResult> redstoneScanCache = new HashMap<>();
    private boolean isLagDetectionActive = false;
    private boolean isRedstoneGloballyDisabled = false;
    private final boolean consoleLoggingEnabled;
    private static final long NOTIFICATION_COOLDOWN = 300_000;
    private static final long PLAYER_CACHE_DURATION = 200L;
    private static final long SCAN_CACHE_DURATION = 10_000L;
    private static final int MAX_BLOCKS_PER_TICK = 3;
    private double cachedTps = 20.0;
    private long lastTpsCheck = 0;
    private BukkitRunnable patternScanTask;

    private static class RedstoneScanResult {
        boolean hasComplexClock;
        int componentCount;
        long timestamp;

        RedstoneScanResult(boolean hasComplexClock, int componentCount, long timestamp) {
            this.hasComplexClock = hasComplexClock;
            this.componentCount = componentCount;
            this.timestamp = timestamp;
        }
    }

    public RedstoneManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.consoleLoggingEnabled = configManager.isConsoleLoggingEnabled();
        this.isLagDetectionActive = configManager.isLagDetectionEnabled();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startRedstonePatternScan();
        startTpsMonitor();
        clearCaches();
    }

    public boolean isLagDetectionActive() {
        return isLagDetectionActive;
    }

    public void setLagDetectionActive(boolean active) {
        this.isLagDetectionActive = active;
    }

    public boolean hasPlayersNearby(Chunk chunk, int radius) {
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
                double tps = Bukkit.getTPS()[0];
                if (tps < configManager.getCriticalTpsThreshold() && !isRedstoneGloballyDisabled) {
                    isRedstoneGloballyDisabled = true;
                    redstoneCooldown.clear();
                    lagMachineChunks.clear();
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().warning("Критически низкий TPS (" + String.format("%.2f", tps) + "), редстоун временно отключён глобально!");
                    }
                    notifyAdmins(configManager.getMessages("tps-critical"), tps);
                } else if (tps >= configManager.getRedstoneTpsThreshold() && isRedstoneGloballyDisabled) {
                    isRedstoneGloballyDisabled = false;
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("TPS восстановлен (" + String.format("%.2f", tps) + "), редстоун снова включён");
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
        // Отправка в консоль, если включено
        if (configManager.isConsoleNotificationsEnabled()) {
            for (String message : messages) {
                plugin.getLogger().info(HexColors.colorize(message.replace("{tps}", String.format("%.2f", tps)), true, plugin.getLogger()));
            }
        }
    }

    private void notifyLagMachineDetected(Chunk chunk, Location centerLocation) {
        long currentTime = System.currentTimeMillis();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk nearbyChunk = chunk.getWorld().getChunkAt(chunk.getX() + dx, chunk.getZ() + dz);
                Long lastNotified = lastNotificationTime.get(nearbyChunk);
                if (lastNotified != null && (currentTime - lastNotified) < NOTIFICATION_COOLDOWN) {
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("Уведомление о лаг-машине в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") пропущено: соседний чанк на кулдауне");
                    }
                    return;
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk nearbyChunk = chunk.getWorld().getChunkAt(chunk.getX() + dx, chunk.getZ() + dz);
                lastNotificationTime.put(nearbyChunk, currentTime);
            }
        }

        int destroyedCount = destroyLagMachineComponents(chunk);

        AlertsCommand alertsCommand = ((CommandsHandler) plugin.getCommand("olm").getExecutor()).getAlertsCommand();
        configManager.getMessages("alerts.lag-machine-detected").forEach(message -> {
            String formattedMessage = message
                    .replace("{world}", chunk.getWorld().getName())
                    .replace("{x}", String.valueOf(centerLocation.getBlockX()))
                    .replace("{y}", String.valueOf(centerLocation.getBlockY()))
                    .replace("{z}", String.valueOf(centerLocation.getBlockZ()))
                    .replace("{count}", String.valueOf(destroyedCount))
                    .replace("{type}", "редстоун/стойки для брони");
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
            plugin.getLogger().warning("Обнаружена лаг-машина (редстоун/стойки для брони) в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в мире " + chunk.getWorld().getName() +
                    " на координатах (" + centerLocation.getBlockX() + ", " + centerLocation.getBlockY() + ", " + centerLocation.getBlockZ() + "), удалено " + destroyedCount + " компонентов");
        }
    }

    private int destroyLagMachineComponents(Chunk chunk) {
        if (!configManager.isDestroyComponentsEnabled()) {
            return 0;
        }

        List<Block> components = new ArrayList<>();
        List<Entity> armorStands = new ArrayList<>();
        List<String> monitoredBlocks = configManager.getMonitoredBlocks();
        List<String> criticalBlocks = Arrays.asList("REDSTONE_TORCH", "REPEATER", "COMPARATOR", "OBSERVER", "PISTON", "STICKY_PISTON");

        int minY = Math.max(chunk.getWorld().getMinHeight(), 64 - 64);
        int maxY = Math.min(chunk.getWorld().getMaxHeight(), 64 + 64);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    String blockType = block.getType().name();
                    if (monitoredBlocks.contains(blockType) && criticalBlocks.contains(blockType)) {
                        components.add(block);
                    }
                }
            }
        }

        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                armorStands.add(entity);
            }
        }

        int maxDestroyed = Math.min(configManager.getMaxDestroyedComponents(), components.size() + armorStands.size());
        if (maxDestroyed == 0) {
            return 0;
        }

        List<Object> toDestroy = new ArrayList<>();
        toDestroy.addAll(components);
        toDestroy.addAll(armorStands);
        Collections.shuffle(toDestroy);

        int destroyedCount = 0;
        List<Block> blocksToDestroy = new ArrayList<>();
        List<Entity> entitiesToDestroy = new ArrayList<>();

        for (Object obj : toDestroy.subList(0, Math.min(maxDestroyed, toDestroy.size()))) {
            if (obj instanceof Block) {
                blocksToDestroy.add((Block) obj);
            } else if (obj instanceof Entity) {
                entitiesToDestroy.add((Entity) obj);
            }
            destroyedCount++;
        }

        if (!blocksToDestroy.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    destroyBlocksInBatches(blocksToDestroy, chunk, 0);
                }
            }.runTaskAsynchronously(plugin);
        }

        if (!entitiesToDestroy.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    destroyEntitiesInBatches(entitiesToDestroy, chunk);
                }
            }.runTaskAsynchronously(plugin);
        }

        return destroyedCount;
    }

    private void destroyBlocksInBatches(List<Block> blocks, Chunk chunk, int startIndex) {
        if (startIndex >= blocks.size()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Block block : blocks) {
                    updateNeighboringBlocks(block, false);
                }
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Завершено удаление блоков в чанке (" + chunk.getX() + ", " + chunk.getZ() + ")");
                }
            });
            return;
        }

        int endIndex = Math.min(startIndex + MAX_BLOCKS_PER_TICK, blocks.size());
        List<Block> batch = blocks.subList(startIndex, endIndex);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Block block : batch) {
                block.setType(org.bukkit.Material.AIR, false);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Удалён блок " + block.getType().name() + " в чанке (" + chunk.getX() + ", " + chunk.getZ() +
                            ") на координатах (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
                }
            }
        });

        if (endIndex < blocks.size()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> destroyBlocksInBatches(blocks, chunk, endIndex), 5L);
        }
    }

    private void destroyEntitiesInBatches(List<Entity> entities, Chunk chunk) {
        int batchSize = Math.min(MAX_BLOCKS_PER_TICK, entities.size());
        List<Entity> batch = entities.subList(0, batchSize);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity entity : batch) {
                entity.remove();
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Удалена стойка для брони в чанке (" + chunk.getX() + ", " + chunk.getZ() +
                            ") на координатах (" + entity.getLocation().getX() + ", " + entity.getLocation().getY() + ", " + entity.getLocation().getZ() + ")");
                }
            }
        });

        if (batchSize < entities.size()) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    destroyEntitiesInBatches(entities.subList(batchSize, entities.size()), chunk), 5L);
        }
    }

    private void clearCaches() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                playerNearbyCache.entrySet().removeIf(entry -> currentTime - playerNearbyCacheTime.get(entry.getKey()) > 60_000L);
                redstoneScanCache.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp > 60_000L);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Очищены кэши редстоуна: " + playerNearbyCache.size() + " чанков в playerNearbyCache, " +
                            redstoneScanCache.size() + " чанков в redstoneScanCache");
                }
            }
        }.runTaskTimer(plugin, 0L, 1200L);
    }

    private Location findLagMachineCenter(Chunk chunk) {
        Map<Location, Integer> activityScores = new HashMap<>();
        List<String> monitoredBlocks = configManager.getMonitoredBlocks();

        int minY = Math.max(chunk.getWorld().getMinHeight(), 64 - 64);
        int maxY = Math.min(chunk.getWorld().getMaxHeight(), 64 + 64);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    String blockType = block.getType().name();
                    if (monitoredBlocks.contains(blockType)) {
                        int score = 0;
                        if (blockType.equals("OBSERVER") || blockType.equals("REDSTONE_TORCH") || blockType.equals("REPEATER") || blockType.equals("PISTON") || blockType.equals("STICKY_PISTON")) {
                            score += 5;
                            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                                Block adjacent = block.getRelative(face);
                                if (monitoredBlocks.contains(adjacent.getType().name())) {
                                    score += 2;
                                }
                            }
                        }
                        activityScores.put(block.getLocation(), score);
                    }
                }
            }
        }

        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                int score = 3;
                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                    Block adjacent = entity.getLocation().getBlock().getRelative(face);
                    if (monitoredBlocks.contains(adjacent.getType().name())) {
                        score += 2;
                    }
                }
                activityScores.put(entity.getLocation(), score);
            }
        }

        Location center = chunk.getBlock(8, 64, 8).getLocation();
        int maxScore = -1;
        for (Map.Entry<Location, Integer> entry : activityScores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                center = entry.getKey();
            }
        }
        return center;
    }

    private boolean detectRedstoneClock(Chunk chunk) {
        long currentTime = System.currentTimeMillis();
        RedstoneScanResult cachedResult = redstoneScanCache.get(chunk);
        if (cachedResult != null && (currentTime - cachedResult.timestamp) < SCAN_CACHE_DURATION) {
            return cachedResult.hasComplexClock && cachedResult.componentCount >= configManager.getMinRedstoneComponents();
        }

        int redstoneComponentCount = 0;
        boolean hasComplexClock = false;
        List<String> monitoredBlocks = configManager.getMonitoredBlocks();

        int minY = Math.max(chunk.getWorld().getMinHeight(), 64 - 64);
        int maxY = Math.min(chunk.getWorld().getMaxHeight(), 64 + 64);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    String blockType = block.getType().name();
                    if (monitoredBlocks.contains(blockType)) {
                        redstoneComponentCount++;
                        if (blockType.equals("REDSTONE_TORCH") || blockType.equals("REPEATER") || blockType.equals("COMPARATOR") || blockType.equals("OBSERVER") ||
                                blockType.equals("PISTON") || blockType.equals("STICKY_PISTON")) {
                            int connections = 0;
                            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                                Block adjacent = block.getRelative(face);
                                String adjacentType = adjacent.getType().name();
                                if (monitoredBlocks.contains(adjacentType)) {
                                    connections++;
                                }
                            }
                            if (connections >= 2) {
                                hasComplexClock = true;
                            }
                        }
                    }
                }
            }
        }

        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
                    Block adjacent = entity.getLocation().getBlock().getRelative(face);
                    if (monitoredBlocks.contains(adjacent.getType().name())) {
                        hasComplexClock = true;
                        redstoneComponentCount++;
                        break;
                    }
                }
            }
        }

        redstoneScanCache.put(chunk, new RedstoneScanResult(hasComplexClock, redstoneComponentCount, currentTime));
        return hasComplexClock && redstoneComponentCount >= configManager.getMinRedstoneComponents();
    }

    private void disableRedstoneInChunk(Chunk chunk) {
        long cooldownMillis = configManager.getLagDetectionCooldown() * 50L;
        redstoneCooldown.put(chunk, System.currentTimeMillis() + cooldownMillis);
        lagMachineChunks.add(chunk);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk nearbyChunk = chunk.getWorld().getChunkAt(chunk.getX() + dx, chunk.getZ() + dz);
                redstoneCooldown.put(nearbyChunk, System.currentTimeMillis() + cooldownMillis);
                lagMachineChunks.add(nearbyChunk);
            }
        }
        if (consoleLoggingEnabled) {
            plugin.getLogger().info("Редстоун отключён в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") и соседних на " + (cooldownMillis / 1000) + " секунд");
        }
    }

    private void checkCooldownExpiration() {
        redstoneCooldown.entrySet().removeIf(entry -> {
            Chunk chunk = entry.getKey();
            long cooldownEnd = entry.getValue();
            if (System.currentTimeMillis() >= cooldownEnd) {
                if (!detectRedstoneClock(chunk)) {
                    lagMachineChunks.remove(chunk);
                    lastNotificationTime.remove(chunk);
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("Кулдаун редстоуна истёк в чанке (" + chunk.getX() + ", " + chunk.getZ() + "), лаг-машина не обнаружена, редстоун разрешён");
                    }
                    return true;
                } else {
                    long cooldownMillis = configManager.getLagDetectionCooldown() * 50L;
                    redstoneCooldown.put(chunk, System.currentTimeMillis() + cooldownMillis);
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("Лаг-машина всё ещё обнаружена в чанке (" + chunk.getX() + ", " + chunk.getZ() + "), кулдаун продлён на " + (cooldownMillis / 1000) + " секунд");
                    }
                    return false;
                }
            }
            return false;
        });
    }

    public void disableLagMachines() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int disabledChunks = 0;
                for (World world : Bukkit.getWorlds()) {
                    if (world == null) continue;
                    for (Chunk chunk : world.getLoadedChunks()) {
                        if (chunk == null || protectedChunks.contains(chunk)) continue;
                        if (detectRedstoneClock(chunk) || redstoneActivityCounter.getOrDefault(chunk, 0) >= configManager.getLagDetectionThreshold()) {
                            disableRedstoneInChunk(chunk);
                            lagMachineChunks.add(chunk);
                            notifyLagMachineDetected(chunk, findLagMachineCenter(chunk));
                            disabledChunks++;
                        }
                    }
                }
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Отключено лаг-машин в " + disabledChunks + " чанках");
                }
            }
        }.runTask(plugin);
    }

    public void enableLagMachines() {
        redstoneCooldown.clear();
        lagMachineChunks.clear();
        protectedChunks.clear();
        redstoneActivityCounter.clear();
        lastNotificationTime.clear();
        isLagDetectionActive = false;
        isRedstoneGloballyDisabled = false;
        if (consoleLoggingEnabled) {
            plugin.getLogger().info("Лаг-машины включены, все ограничения и кулдауны сброшены");
        }
    }

    public void disableProtectionForChunks() {
        protectedChunks.clear();
        isLagDetectionActive = true;
        if (consoleLoggingEnabled) {
            plugin.getLogger().info("Защита чанков снята, проверка лаг-машин возобновлена");
        }
    }

    public boolean allowRedstoneUpdate(Chunk chunk, Block block) {
        String blockType = block.getType().name();
        if (blockType.equals("LEVER") || blockType.equals("BUTTON") || blockType.equals("REDSTONE_TORCH")) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Обновление блока " + blockType + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") разрешено (источник сигнала)");
            }
            return true;
        }

        if (!configManager.isRedstoneOptimizationEnabled()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Оптимизация редстоуна отключена, обновление блока " + blockType + " разрешено");
            }
            return true;
        }

        if (isRedstoneGloballyDisabled) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Редстоун глобально заблокирован, обновление блока " + blockType + " отклонено");
            }
            return false;
        }

        if (!isLagDetectionActive || protectedChunks.contains(chunk)) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Чанк (" + chunk.getX() + ", " + chunk.getZ() + ") защищён или лаг-детекция отключена, обновление редстоуна разрешено");
            }
            return true;
        }

        if (configManager.isPlayerRadiusCheckEnabled() && !hasPlayersNearby(chunk, configManager.getRedstonePlayerRadius())) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Редстоун отключён в чанке (" + chunk.getX() + ", " + chunk.getZ() + "): нет игроков в радиусе " + configManager.getRedstonePlayerRadius());
            }
            return false;
        }

        if (redstoneCooldown.containsKey(chunk) && System.currentTimeMillis() < redstoneCooldown.get(chunk)) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Редстоун в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") на кулдауне");
            }
            return false;
        }

        int updates = redstoneUpdates.getOrDefault(chunk, 0);
        if (updates >= configManager.getMaxRedstoneUpdatesPerTick()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Превышен лимит обновлений редстоуна (" + updates + "/" + configManager.getMaxRedstoneUpdatesPerTick() + ") в чанке (" + chunk.getX() + ", " + chunk.getZ() + ")");
            }
            return false;
        }

        if (configManager.isDisableRedstoneLowTps() && getCachedTps() < configManager.getRedstoneTpsThreshold()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("TPS " + String.format("%.2f", getCachedTps()) + " ниже порога " + configManager.getRedstoneTpsThreshold() + ", редстоун отключён для блока " + blockType);
            }
            return false;
        }

        if (configManager.getWhitelistBlocks().contains(blockType)) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Блок " + blockType + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") в белом списке, обновление разрешено");
            }
            return true;
        }

        if (configManager.isLagDetectionEnabled()) {
            int activity = redstoneActivityCounter.getOrDefault(chunk, 0) + 1;
            redstoneActivityCounter.put(chunk, activity);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                redstoneActivityCounter.computeIfPresent(chunk, (k, v) -> v > 1 ? v - 1 : null);
            }, 200L);
            if (activity >= configManager.getLagDetectionThreshold()) {
                disableRedstoneInChunk(chunk);
                lagMachineChunks.add(chunk);
                notifyLagMachineDetected(chunk, findLagMachineCenter(chunk));
                return false;
            }
        }

        redstoneUpdates.put(chunk, updates + 1);
        if (consoleLoggingEnabled) {
            plugin.getLogger().info("Обновление редстоуна для блока " + blockType + " в чанке (" + chunk.getX() + ", " + chunk.getZ() + "), текущее количество: " + (updates + 1));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            redstoneUpdates.remove(chunk);
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Сброшено количество обновлений редстоуна для чанка (" + chunk.getX() + ", " + chunk.getZ() + ")");
            }
        }, 1L);
        return true;
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        if (!configManager.isRedstoneOptimizationEnabled()) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Оптимизация редстоуна отключена, событие редстоуна для блока " +
                        event.getBlock().getType().name() + " в чанке (" +
                        event.getBlock().getChunk().getX() + ", " + event.getBlock().getChunk().getZ() + ") пропущено");
            }
            return;
        }

        try {
            String blockType = event.getBlock().getType().name();
            if (blockType.equals("LEVER") || blockType.equals("BUTTON") || blockType.equals("REDSTONE_TORCH")) {
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Обновление блока " + blockType + " в чанке (" +
                            event.getBlock().getChunk().getX() + ", " + event.getBlock().getChunk().getZ() +
                            ") разрешено (источник сигнала)");
                }
                return;
            }

            if (isRedstoneGloballyDisabled || !allowRedstoneUpdate(event.getBlock().getChunk(), event.getBlock())) {
                event.setNewCurrent(0);
                if (consoleLoggingEnabled) {
                    plugin.getLogger().info("Отменено обновление редстоуна для блока " + blockType +
                            " в чанке (" + event.getBlock().getChunk().getX() + ", " +
                            event.getBlock().getChunk().getZ() + ") из-за " +
                            (isRedstoneGloballyDisabled ? "глобального отключения" : "ограничений"));
                }
                updateNeighboringBlocks(event.getBlock(), false);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки события редстоуна: " +
                    (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String blockType = block.getType().name();
        if (blockType.equals("LEVER") || blockType.equals("BUTTON") || blockType.equals("REDSTONE_TORCH")) {
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Разрушение блока-источника редстоуна " + blockType + " в чанке (" +
                        block.getChunk().getX() + ", " + block.getChunk().getZ() +
                        ") на координатах (" + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
            }
            updateNeighboringBlocks(block, true);
        }
    }

    private void updateNeighboringBlocks(Block block, boolean applyPhysics) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block adjacent = block.getRelative(face);
            String adjacentType = adjacent.getType().name();
            Bukkit.getScheduler().runTask(plugin, () -> {
                adjacent.getState().update(true, applyPhysics);
                if (adjacentType.equals("REDSTONE_WIRE") && !hasActiveRedstoneSource(adjacent)) {
                    adjacent.setType(adjacent.getType(), applyPhysics);
                    if (consoleLoggingEnabled) {
                        plugin.getLogger().info("Обновлён редстоун-провод в чанке (" + block.getChunk().getX() + ", " +
                                block.getChunk().getZ() + ") на координатах (" + adjacent.getX() + ", " +
                                adjacent.getY() + ", " + adjacent.getZ() + ")");
                    }
                }
            });
        }
    }

    private boolean hasActiveRedstoneSource(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block adjacent = block.getRelative(face);
            String adjacentType = adjacent.getType().name();
            if (adjacentType.equals("LEVER") || adjacentType.equals("BUTTON") || block.getType().name().equals("REDSTONE_TORCH")) {
                return true;
            }
        }
        return false;
    }

    private void startRedstonePatternScan() {
        if (!configManager.isPatternScanEnabled() || !configManager.isRedstoneOptimizationEnabled()) {
            if (consoleLoggingEnabled) plugin.getLogger().info("Сканирование редстоун-цепей отключено");
            return;
        }

        if (patternScanTask != null && !patternScanTask.isCancelled()) {
            patternScanTask.cancel();
        }

        patternScanTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!isLagDetectionActive || Bukkit.getOnlinePlayers().isEmpty()) {
                        if (consoleLoggingEnabled) plugin.getLogger().info("Сканирование редстоуна приостановлено: нет игроков или детекция отключена");
                        return;
                    }
                    long startTime = System.nanoTime();
                    int scannedChunks = 0;
                    List<Chunk> chunksToCheck = new ArrayList<>();

                    for (World world : Bukkit.getWorlds()) {
                        if (world == null || world.getPlayers().isEmpty()) continue;
                        for (Chunk chunk : world.getLoadedChunks()) {
                            if (chunk == null || protectedChunks.contains(chunk)) continue;
                            if (hasPlayersNearby(chunk, configManager.getRedstonePlayerRadius())) {
                                chunksToCheck.add(chunk);
                                if (++scannedChunks >= 2) break;
                            }
                        }
                        if (scannedChunks >= 2) break;
                    }

                    if (!chunksToCheck.isEmpty()) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                List<Chunk> lagChunks = new ArrayList<>();
                                for (Chunk chunk : chunksToCheck) {
                                    if (detectRedstoneClock(chunk)) {
                                        lagChunks.add(chunk);
                                    }
                                }
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    for (Chunk chunk : lagChunks) {
                                        disableRedstoneInChunk(chunk);
                                        lagMachineChunks.add(chunk);
                                        notifyLagMachineDetected(chunk, findLagMachineCenter(chunk));
                                    }
                                });
                            }
                        }.runTaskAsynchronously(plugin);
                    }

                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    if (duration > 20 && consoleLoggingEnabled) {
                        plugin.getLogger().warning("Сканирование редстоуна заняло " + duration + " мс, обработано чанков: " + scannedChunks);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Ошибка сканирования редстоуна: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        };
        patternScanTask.runTaskTimer(plugin, 0L, Math.max(configManager.getPatternScanInterval(), 12000L));
    }

    public void resetRedstoneCooldowns() {
        if (patternScanTask != null && !patternScanTask.isCancelled()) {
            patternScanTask.cancel();
            if (consoleLoggingEnabled) {
                plugin.getLogger().info("Задача сканирования редстоуна отменена");
            }
        }
        redstoneCooldown.clear();
        lagMachineChunks.clear();
        protectedChunks.clear();
        redstoneActivityCounter.clear();
        lastNotificationTime.clear();
        playerNearbyCache.clear();
        playerNearbyCacheTime.clear();
        redstoneScanCache.clear();
        isLagDetectionActive = false;
        isRedstoneGloballyDisabled = false;
        if (consoleLoggingEnabled) {
            plugin.getLogger().info("Все кулдауны редстоуна и защита чанков сброшены");
        }
    }
}