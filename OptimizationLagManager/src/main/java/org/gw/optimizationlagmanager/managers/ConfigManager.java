package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.utils.HexColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {
    private final OptimizationLagManager plugin;
    private FileConfiguration config;
    private boolean consoleLoggingEnabled;
    private boolean physicsOptimizationEnabled;
    private boolean trapdoorLimitEnabled;
    private boolean redstoneOptimizationEnabled;
    private boolean fallingBlockLimitEnabled;
    private boolean cobwebDetectionEnabled;
    private boolean destroyStaticSandEnabled;
    private boolean destroyCobwebsEnabled;
    private boolean minecartLimitEnabled;
    private boolean railDetectionEnabled;
    private boolean destroyRailsEnabled;
    private boolean disableFallingBlocks;
    private boolean disableWaterFlow;
    private boolean disableLavaFlow;
    private boolean lagDetectionEnabled;
    private boolean playerRadiusCheckEnabled;
    private boolean patternScanEnabled;
    private boolean destroyComponentsEnabled;
    private boolean disableRedstoneLowTps;
    private int maxTrapdoorUpdatesPerTick;
    private int maxFallingBlocksPerChunk;
    private int maxStaticSandPerChunk;
    private int maxCobwebsPerChunk;
    private int maxMinecartsPerChunk;
    private int maxRailsPerChunk;
    private int maxDestroyedComponents;
    private int maxRedstoneUpdatesPerTick;
    private int redstonePlayerRadius;
    private int patternScanInterval;
    private int lagDetectionThreshold;
    private int lagDetectionCooldown;
    private int minRedstoneComponents;
    private double redstoneTpsThreshold;
    private double criticalTpsThreshold;
    private List<String> excludedWorlds;
    private List<String> whitelistBlocks;
    private List<String> monitoredBlocks;
    private boolean teleportChunkLoadingEnabled;
    private boolean asyncChunkLoadingEnabled;
    private int chunksPerTickTeleport;
    private int maxChunkRadius;
    private boolean containerOptimizationEnabled;
    private int maxOpenContainersPerChunk;
    private int containerOpenDelayTicks;
    private boolean disableContainersLowTps;
    private double containerTpsThreshold;
    private boolean consoleNotificationsEnabled;
    private int maxViewDistance;
    private int sendDelayTicks;
    private long playerSendMaxBytes;
    private int chunkCacheSaveInterval;

    public ConfigManager(OptimizationLagManager plugin) {
        this.plugin = plugin;
        checkServerVersion();
        loadConfig();
    }

    private void checkServerVersion() {
        String version = Bukkit.getBukkitVersion().split("-")[0];
        if (!isVersionAtLeast(version, "1.16")) {
            plugin.getLogger().severe("Ошибка: Плагин требует Minecraft версии 1.16 или выше. Текущая версия: " + version);
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
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

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        cacheConfigValues();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        cacheConfigValues();
    }

    private void cacheConfigValues() {
        consoleLoggingEnabled = config.getBoolean("settings.logs-in-console.enabled", false);
        physicsOptimizationEnabled = config.getBoolean("optimization.physics.enabled", false);
        trapdoorLimitEnabled = config.getBoolean("optimization.physics.trapdoor-limit.enabled", true);
        redstoneOptimizationEnabled = config.getBoolean("optimization.redstone.enabled", true);
        fallingBlockLimitEnabled = config.getBoolean("optimization.physics.falling-block-limit.enabled", true);
        cobwebDetectionEnabled = config.getBoolean("optimization.physics.cobweb-detection.enabled", true);
        destroyStaticSandEnabled = config.getBoolean("optimization.physics.falling-block-limit.destroy-static-sand", true);
        destroyCobwebsEnabled = config.getBoolean("optimization.physics.cobweb-detection.destroy-cobwebs", true);
        minecartLimitEnabled = config.getBoolean("optimization.entities.minecart-limit.enabled", true);
        railDetectionEnabled = config.getBoolean("optimization.entities.rail-detection.enabled", true);
        destroyRailsEnabled = config.getBoolean("optimization.entities.rail-detection.destroy-rails", true);
        disableFallingBlocks = config.getBoolean("optimization.physics.disable-falling-blocks", false);
        disableWaterFlow = config.getBoolean("optimization.physics.disable-water-flow", false);
        disableLavaFlow = config.getBoolean("optimization.physics.disable-lava-flow", false);
        lagDetectionEnabled = config.getBoolean("optimization.redstone.lag-detection.enabled", true);
        playerRadiusCheckEnabled = config.getBoolean("optimization.redstone.player-radius-check.enabled", true);
        patternScanEnabled = config.getBoolean("optimization.redstone.pattern-scan.enabled", true);
        destroyComponentsEnabled = config.getBoolean("optimization.redstone.lag-detection.destroy-components.enabled", false);
        disableRedstoneLowTps = config.getBoolean("optimization.redstone.disable-during-low-tps.enabled", false);
        maxTrapdoorUpdatesPerTick = config.getInt("optimization.physics.trapdoor-limit.max-updates-per-tick", 100);
        maxFallingBlocksPerChunk = config.getInt("optimization.physics.falling-block-limit.max-falling-blocks-per-chunk", 25);
        maxStaticSandPerChunk = config.getInt("optimization.physics.falling-block-limit.max-static-sand-per-chunk", 100);
        maxCobwebsPerChunk = config.getInt("optimization.physics.cobweb-detection.max-cobwebs-per-chunk", 50);
        maxMinecartsPerChunk = config.getInt("optimization.entities.minecart-limit.max-minecarts-per-chunk", 10);
        maxRailsPerChunk = config.getInt("optimization.entities.rail-detection.max-rails-per-chunk", 50);
        maxDestroyedComponents = config.getInt("optimization.redstone.lag-detection.destroy-components.max-destroyed", 3);
        maxRedstoneUpdatesPerTick = config.getInt("optimization.redstone.max-redstone-updates-per-tick", 200);
        redstonePlayerRadius = config.getInt("optimization.redstone.player-radius-check.radius", 128);
        patternScanInterval = config.getInt("optimization.redstone.pattern-scan.scan-interval-ticks", 6000);
        lagDetectionThreshold = config.getInt("optimization.redstone.lag-detection.activity-threshold", 3600);
        lagDetectionCooldown = config.getInt("optimization.redstone.lag-detection.cooldown-seconds", 30) * 20;
        minRedstoneComponents = config.getInt("optimization.redstone.lag-detection.min-redstone-components", 10);
        redstoneTpsThreshold = config.getDouble("optimization.redstone.disable-during-low-tps.tps-threshold", 10.0);
        criticalTpsThreshold = config.getDouble("optimization.redstone.critical-tps-threshold", 5.0);
        excludedWorlds = config.getStringList("optimization.physics.excluded-worlds");
        whitelistBlocks = config.getStringList("optimization.redstone.whitelist-blocks");
        monitoredBlocks = config.getStringList("optimization.redstone.lag-detection.monitored-blocks");
        teleportChunkLoadingEnabled = config.getBoolean("optimization.chunks.teleport-chunk-loading.enabled", true);
        asyncChunkLoadingEnabled = config.getBoolean("optimization.chunks.teleport-chunk-loading.async-chunk-loading.enabled", true);
        chunksPerTickTeleport = config.getInt("optimization.chunks.teleport-chunk-loading.async-chunk-loading.chunks-per-tick", 2);
        maxChunkRadius = config.getInt("optimization.chunks.teleport-chunk-loading.max-chunk-radius", 4);
        containerOptimizationEnabled = config.getBoolean("optimization.containers.enabled", true);
        maxOpenContainersPerChunk = config.getInt("optimization.containers.max-open-containers-per-chunk", 10);
        containerOpenDelayTicks = config.getInt("optimization.containers.open-delay-ticks", 10);
        disableContainersLowTps = config.getBoolean("optimization.containers.disable-on-low-tps.enabled", true);
        containerTpsThreshold = config.getDouble("optimization.containers.disable-on-low-tps.tps-threshold", 14.0);
        consoleNotificationsEnabled = config.getBoolean("settings.console-notifications.enabled", true);
        maxViewDistance = config.getInt("optimization.chunks.extended-view-distance.max-view-distance", 10);
        sendDelayTicks = config.getInt("optimization.chunks.extended-view-distance.send-delay-ticks", 40);
        playerSendMaxBytes = config.getLong("optimization.chunks.extended-view-distance.player-send-max-bytes", 2097152);
        chunkCacheSaveInterval = config.getInt("optimization.chunks.cache-save-interval-ticks", 6000); // Загрузка новой настройки
    }

    public List<String> getMessages(String path) {
        Object message = config.get("messages." + path, "");
        if (message instanceof String) {
            return Collections.singletonList(HexColors.translate((String) message));
        } else if (message instanceof List) {
            return ((List<?>) message).stream()
                    .map(Object::toString)
                    .map(HexColors::translate)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public String getMessage(String path) {
        return HexColors.translate(config.getString("messages." + path, ""));
    }

    public int getChunkCacheSaveInterval() {
        return chunkCacheSaveInterval;
    }

    public boolean isConsoleLoggingEnabled() { return consoleLoggingEnabled; }
    public boolean isPhysicsOptimizationEnabled() { return physicsOptimizationEnabled; }
    public boolean isTrapdoorLimitEnabled() { return trapdoorLimitEnabled; }
    public boolean isRedstoneOptimizationEnabled() { return redstoneOptimizationEnabled; }
    public boolean isFallingBlockLimitEnabled() { return fallingBlockLimitEnabled; }
    public boolean isCobwebDetectionEnabled() { return cobwebDetectionEnabled; }
    public boolean isDestroyStaticSandEnabled() { return destroyStaticSandEnabled; }
    public boolean isDestroyCobwebsEnabled() { return destroyCobwebsEnabled; }
    public boolean isMinecartLimitEnabled() { return minecartLimitEnabled; }
    public boolean isRailDetectionEnabled() { return railDetectionEnabled; }
    public boolean isDestroyRailsEnabled() { return destroyRailsEnabled; }
    public boolean isDisableFallingBlocks() { return disableFallingBlocks; }
    public boolean isDisableWaterFlow() { return disableWaterFlow; }
    public boolean isDisableLavaFlow() { return disableLavaFlow; }
    public boolean isLagDetectionEnabled() { return lagDetectionEnabled; }
    public boolean isPlayerRadiusCheckEnabled() { return playerRadiusCheckEnabled; }
    public boolean isPatternScanEnabled() { return patternScanEnabled; }
    public boolean isDestroyComponentsEnabled() { return destroyComponentsEnabled; }
    public boolean isDisableRedstoneLowTps() { return disableRedstoneLowTps; }
    public int getMaxTrapdoorUpdatesPerTick() { return maxTrapdoorUpdatesPerTick; }
    public int getMaxFallingBlocksPerChunk() { return maxFallingBlocksPerChunk; }
    public int getMaxStaticSandPerChunk() { return maxStaticSandPerChunk; }
    public int getMaxCobwebsPerChunk() { return maxCobwebsPerChunk; }
    public int getMaxMinecartsPerChunk() { return maxMinecartsPerChunk; }
    public int getMaxRailsPerChunk() { return maxRailsPerChunk; }
    public int getMaxDestroyedComponents() { return maxDestroyedComponents; }
    public int getMaxRedstoneUpdatesPerTick() { return maxRedstoneUpdatesPerTick; }
    public int getRedstonePlayerRadius() { return redstonePlayerRadius; }
    public int getPatternScanInterval() { return patternScanInterval; }
    public int getLagDetectionThreshold() { return lagDetectionThreshold; }
    public int getLagDetectionCooldown() { return lagDetectionCooldown; }
    public int getMinRedstoneComponents() { return minRedstoneComponents; }
    public double getRedstoneTpsThreshold() { return redstoneTpsThreshold; }
    public double getCriticalTpsThreshold() { return criticalTpsThreshold; }
    public List<String> getExcludedWorlds() { return excludedWorlds; }
    public List<String> getWhitelistBlocks() { return whitelistBlocks; }
    public List<String> getMonitoredBlocks() { return monitoredBlocks; }
    public boolean isTeleportChunkLoadingEnabled() { return teleportChunkLoadingEnabled; }
    public boolean isAsyncChunkLoadingEnabled() { return asyncChunkLoadingEnabled; }
    public int getChunksPerTickTeleport() { return chunksPerTickTeleport; }
    public boolean isContainerOptimizationEnabled() { return containerOptimizationEnabled; }
    public int getMaxOpenContainersPerChunk() { return maxOpenContainersPerChunk; }
    public int getContainerOpenDelayTicks() { return containerOpenDelayTicks; }
    public boolean isDisableContainersLowTps() { return disableContainersLowTps; }
    public double getContainerTpsThreshold() { return containerTpsThreshold; }
    public boolean isConsoleNotificationsEnabled() { return consoleNotificationsEnabled; }
    public int getMaxViewDistance() { return maxViewDistance; }
    public int getSendDelayTicks() { return sendDelayTicks; }
    public long getPlayerSendMaxBytes() { return playerSendMaxBytes; }

    public void setLagDetectionEnabled(boolean enabled) {
        config.set("optimization.redstone.lag-detection.enabled", enabled);
        lagDetectionEnabled = enabled;
        plugin.saveConfig();
    }

    public void setDisableFallingBlocks(boolean enabled) {
        config.set("optimization.physics.disable-falling-blocks", enabled);
        disableFallingBlocks = enabled;
        plugin.saveConfig();
    }

    public void setDisableWaterFlow(boolean enabled) {
        config.set("optimization.physics.disable-water-flow", enabled);
        disableWaterFlow = enabled;
        plugin.saveConfig();
    }

    public void setDisableLavaFlow(boolean enabled) {
        config.set("optimization.physics.disable-lava-flow", enabled);
        disableLavaFlow = enabled;
        plugin.saveConfig();
    }

    public boolean isChunkOptimizationEnabled() {
        return config.getBoolean("optimization.chunks.enabled", true);
    }

    public int getChunkLoadDistance() {
        return config.getInt("optimization.chunks.load-distance", 5);
    }

    public int getChunkUnloadDelay() {
        return config.getInt("optimization.chunks.unload-delay", 800);
    }

    public boolean isDynamicLoadDistanceEnabled() {
        return config.getBoolean("optimization.chunks.dynamic-load-distance.enabled", true);
    }

    public double getDynamicTpsThreshold() {
        return config.getDouble("optimization.chunks.dynamic-load-distance.tps-threshold", 17.0);
    }

    public int getMinLoadDistance() {
        return config.getInt("optimization.chunks.dynamic-load-distance.min-distance", 3);
    }

    public boolean isMobSpawningEnabled() {
        return config.getBoolean("optimization.mob-spawning.enabled", true);
    }

    public int getMaxMobsPerChunk() {
        return config.getInt("optimization.mob-spawning.max-mobs-per-chunk", 50);
    }

    public boolean isSpawnRateReductionEnabled() {
        return config.getBoolean("optimization.mob-spawning.spawn-rate-reduction.enabled", true);
    }

    public double getTpsThreshold() {
        return config.getDouble("optimization.mob-spawning.spawn-rate-reduction.tps-threshold", 15.0);
    }

    public int getReductionPercentage() {
        return config.getInt("optimization.mob-spawning.spawn-rate-reduction.reduction-percentage", 50);
    }

    public boolean isSpawnerLimitEnabled() {
        return config.getBoolean("optimization.mob-spawning.spawner-limit.enabled", false);
    }

    public int getMaxMobsPerSpawner() {
        return config.getInt("optimization.mob-spawning.spawner-limit.max-mobs-per-spawner", 6);
    }

    public int getSpawnerDelay() {
        return config.getInt("optimization.mob-spawning.spawner-limit.spawn-delay", 200);
    }

    public int getSpawnerRadiusCheck() {
        return config.getInt("optimization.mob-spawning.spawner-limit.radius-check", 16);
    }

    public boolean isEntityOptimizationEnabled() {
        return config.getBoolean("optimization.entities.enabled", true);
    }

    public int getMaxEntitiesPerChunk() {
        return config.getInt("optimization.entities.max-entities-per-chunk", 100);
    }

    public int getRemoveDropsAfter() {
        return config.getInt("optimization.entities.remove-drops-after", 6000);
    }

    public boolean isDisableAiLowTps() {
        return config.getBoolean("optimization.entities.disable-ai-low-tps.enabled", true);
    }

    public double getEntityTpsThreshold() {
        return config.getDouble("optimization.entities.disable-ai-low-tps.tps-threshold", 12.0);
    }

    public boolean isWorldGenOptimizationEnabled() {
        return config.getBoolean("optimization.world-generation.enabled", true);
    }

    public boolean isPregenerateChunks() {
        return config.getBoolean("optimization.world-generation.pregenerate-chunks.enabled", false);
    }

    public int getPregenerateRadius() {
        return config.getInt("optimization.world-generation.pregenerate-radius", 500);
    }

    public int getChunksPerTick() {
        return config.getInt("optimization.world-generation.pregenerate-chunks.chunks-per-tick", 2);
    }
}