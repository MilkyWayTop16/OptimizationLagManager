package org.gw.optimizationlagmanager.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.optimizationlagmanager.OptimizationLagManager;

import java.util.*;

public class ContainerManager implements Listener {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<Chunk, Integer> openContainers = new HashMap<>();
    private final Map<Chunk, Long> lastOpenTime = new HashMap<>();
    private boolean isContainersGloballyDisabled = false;
    private double cachedTps = 20.0;
    private long lastTpsCheck = 0;

    private static final Set<Material> CONTAINER_BLOCKS = new HashSet<>(Arrays.asList(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BARREL,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.BREWING_STAND,
            Material.ENDER_CHEST,
            Material.SHULKER_BOX,
            Material.BLACK_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX
    ));

    public ContainerManager(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTpsMonitor();
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
                if (configManager.isDisableContainersLowTps() && tps < configManager.getContainerTpsThreshold() && !isContainersGloballyDisabled) {
                    isContainersGloballyDisabled = true;
                    if (configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().warning("Критически низкий TPS (" + String.format("%.2f", tps) + "), обновления контейнеров временно отключены!");
                    }
                } else if (tps >= configManager.getContainerTpsThreshold() && isContainersGloballyDisabled) {
                    isContainersGloballyDisabled = false;
                    if (configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().info("TPS восстановлен (" + String.format("%.2f", tps) + "), обновления контейнеров снова включены");
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!configManager.isContainerOptimizationEnabled() || isContainersGloballyDisabled) {
            if (isContainersGloballyDisabled && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                    && CONTAINER_BLOCKS.contains(event.getClickedBlock().getType())) {
                event.setCancelled(true);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Взаимодействие с контейнером в чанке (" + event.getClickedBlock().getChunk().getX() + ", " +
                            event.getClickedBlock().getChunk().getZ() + ") отменено: обновления контейнеров глобально отключены");
                }
                Player player = event.getPlayer();
                Block block = event.getClickedBlock();
                player.sendBlockChange(block.getLocation(), block.getBlockData());
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (!CONTAINER_BLOCKS.contains(block.getType())) return;

        Chunk chunk = block.getChunk();
        long currentTime = System.currentTimeMillis();
        Long lastOpen = lastOpenTime.getOrDefault(chunk, 0L);

        if (currentTime - lastOpen < configManager.getContainerOpenDelayTicks() * 50L) {
            event.setCancelled(true);
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Взаимодействие с контейнером в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") отменено: на кулдауне");
            }
            Player player = event.getPlayer();
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            return;
        }

        int openCount = openContainers.getOrDefault(chunk, 0) + 1;
        if (openCount > configManager.getMaxOpenContainersPerChunk()) {
            event.setCancelled(true);
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Взаимодействие с контейнером в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") отменено: превышен лимит " +
                        configManager.getMaxOpenContainersPerChunk());
            }
            Player player = event.getPlayer();
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!configManager.isContainerOptimizationEnabled() || isContainersGloballyDisabled) {
            if (isContainersGloballyDisabled && configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Открытие контейнера отменено: обновления контейнеров глобально отключены");
            }
            event.setCancelled(isContainersGloballyDisabled);
            return;
        }

        try {
            if (!(event.getInventory().getHolder() instanceof InventoryHolder)) return;
            InventoryHolder holder = (InventoryHolder) event.getInventory().getHolder();
            if (holder.getInventory().getLocation() == null) return;

            Chunk chunk = holder.getInventory().getLocation().getChunk();
            long currentTime = System.currentTimeMillis();
            Long lastOpen = lastOpenTime.getOrDefault(chunk, 0L);

            if (currentTime - lastOpen < configManager.getContainerOpenDelayTicks() * 50L) {
                event.setCancelled(true);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Открытие контейнера в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") отменено: на кулдауне");
                }
                return;
            }

            int openCount = openContainers.getOrDefault(chunk, 0) + 1;
            if (openCount > configManager.getMaxOpenContainersPerChunk()) {
                event.setCancelled(true);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Открытие контейнера в чанке (" + chunk.getX() + ", " + chunk.getZ() + ") отменено: превышен лимит " +
                            configManager.getMaxOpenContainersPerChunk());
                }
                return;
            }

            openContainers.put(chunk, openCount);
            lastOpenTime.put(chunk, currentTime);
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Контейнер открыт в чанке (" + chunk.getX() + ", " + chunk.getZ() + "), текущих открытых: " + openCount);
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    openContainers.put(chunk, openContainers.getOrDefault(chunk, 1) - 1);
                    if (configManager.isConsoleLoggingEnabled()) {
                        plugin.getLogger().info("Контейнер закрыт в чанке (" + chunk.getX() + ", " + chunk.getZ() + "), текущих открытых: " +
                                openContainers.getOrDefault(chunk, 0));
                    }
                }
            }.runTaskLater(plugin, 20L);
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки открытия контейнера: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @EventHandler
    public void onInventoryInteract(InventoryInteractEvent event) {
        if (!configManager.isContainerOptimizationEnabled() || isContainersGloballyDisabled) {
            if (isContainersGloballyDisabled && configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Взаимодействие с контейнером отменено: обновления контейнеров глобально отключены");
            }
            event.setCancelled(isContainersGloballyDisabled);
        }
    }
}