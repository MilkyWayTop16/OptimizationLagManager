package org.gw.optimizationlagmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.managers.ConfigManager;
import org.gw.optimizationlagmanager.managers.RedstoneManager;

public class RedstoneLagCommand implements OptimizationCommand {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final RedstoneManager redstoneManager;

    public RedstoneLagCommand(OptimizationLagManager plugin, ConfigManager configManager, RedstoneManager redstoneManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.redstoneManager = redstoneManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("olm.redstonelag")) {
            configManager.getMessages("no-permission").forEach(sender::sendMessage);
            return;
        }

        if (!configManager.isRedstoneOptimizationEnabled()) {
            configManager.getMessages("redstonelag.redstone-disabled").forEach(sender::sendMessage);
            return;
        }

        boolean toggle;
        if (args.length == 1) {
            toggle = !redstoneManager.isLagDetectionActive();
        } else if (args.length == 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            toggle = args[1].equalsIgnoreCase("on");
        } else {
            configManager.getMessages("redstonelag.usage").forEach(sender::sendMessage);
            return;
        }

        try {
            if (toggle) {
                if (redstoneManager.isLagDetectionActive()) {
                    configManager.getMessages("redstonelag.already-enabled").forEach(sender::sendMessage);
                    return;
                }
                redstoneManager.setLagDetectionActive(true);
                configManager.setLagDetectionEnabled(true);
                redstoneManager.disableProtectionForChunks();
                redstoneManager.disableLagMachines();
                configManager.getMessages("redstonelag.enabled").forEach(sender::sendMessage);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Лаг-машины отключены командой /olm redstonelag on");
                }
            } else {
                if (!redstoneManager.isLagDetectionActive()) {
                    configManager.getMessages("redstonelag.already-disabled").forEach(sender::sendMessage);
                    return;
                }
                redstoneManager.setLagDetectionActive(false);
                configManager.setLagDetectionEnabled(false);
                redstoneManager.enableLagMachines();
                for (World world : Bukkit.getWorlds()) {
                    if (world.getPlayers().isEmpty()) continue;
                    for (Chunk chunk : world.getLoadedChunks()) {
                        if (!redstoneManager.hasPlayersNearby(chunk, configManager.getRedstonePlayerRadius())) continue;
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = world.getMinHeight(); y <= world.getMaxHeight(); y++) {
                                    Block block = chunk.getBlock(x, y, z);
                                    if (block.getType().name().equals("REDSTONE_WIRE")) {
                                        block.setType(block.getType(), true);
                                    }
                                }
                            }
                        }
                    }
                }
                configManager.getMessages("redstonelag.disabled").forEach(sender::sendMessage);
                if (configManager.isConsoleLoggingEnabled()) {
                    plugin.getLogger().info("Лаг-машины включены командой /olm redstonelag off");
                }
            }
        } catch (Exception e) {
            configManager.getMessages("redstonelag.error").forEach(sender::sendMessage);
            plugin.getLogger().severe("Ошибка при выполнении команды redstonelag: " +
                    (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}