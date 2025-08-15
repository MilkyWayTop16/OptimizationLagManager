package org.gw.optimizationlagmanager.commands;

import org.bukkit.command.CommandSender;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.managers.ConfigManager;

public class PhysicsCommand implements OptimizationCommand {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;

    public PhysicsCommand(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("olm.physics")) {
            configManager.getMessages("no-permission").forEach(sender::sendMessage);
            return;
        }

        if (!configManager.isPhysicsOptimizationEnabled()) {
            configManager.getMessages("physics.physics-disabled").forEach(sender::sendMessage);
            return;
        }

        boolean enable;
        String type = "all";

        if (args.length < 2) {
            enable = configManager.isDisableFallingBlocks() || configManager.isDisableWaterFlow() || configManager.isDisableLavaFlow();
        } else if (args[1].equalsIgnoreCase("on")) {
            enable = true;
            type = args.length > 2 ? args[2].toLowerCase() : "all";
        } else if (args[1].equalsIgnoreCase("off")) {
            enable = false;
            type = args.length > 2 ? args[2].toLowerCase() : "all";
        } else {
            configManager.getMessages("physics.usage").forEach(sender::sendMessage);
            return;
        }

        try {
            switch (type) {
                case "all":
                    handleAllPhysics(sender, enable);
                    break;
                case "fallingblocks":
                    handleFallingBlocks(sender, enable);
                    break;
                case "water":
                    handleWaterFlow(sender, enable);
                    break;
                case "lava":
                    handleLavaFlow(sender, enable);
                    break;
                default:
                    configManager.getMessages("physics.usage").forEach(sender::sendMessage);
                    return;
            }
        } catch (Exception e) {
            configManager.getMessages("physics.error").forEach(sender::sendMessage);
            plugin.getLogger().severe("Ошибка при выполнении команды physics: " +
                    (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private void handleAllPhysics(CommandSender sender, boolean enable) {
        if (enable) {
            if (!configManager.isDisableFallingBlocks() && !configManager.isDisableWaterFlow() && !configManager.isDisableLavaFlow()) {
                configManager.getMessages("physics.already-enabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "всех типов")));
                return;
            }
            configManager.setDisableFallingBlocks(false);
            configManager.setDisableWaterFlow(false);
            configManager.setDisableLavaFlow(false);
            configManager.getMessages("physics.enabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "всех типов")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика всех типов включена командой /olm physics" + (args.length > 1 ? " on" : ""));
            }
        } else {
            if (configManager.isDisableFallingBlocks() && configManager.isDisableWaterFlow() && configManager.isDisableLavaFlow()) {
                configManager.getMessages("physics.already-disabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "всех типов")));
                return;
            }
            configManager.setDisableFallingBlocks(true);
            configManager.setDisableWaterFlow(true);
            configManager.setDisableLavaFlow(true);
            configManager.getMessages("physics.disabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "всех типов")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика всех типов отключена командой /olm physics" + (args.length > 1 ? " off" : ""));
            }
        }
    }

    private void handleFallingBlocks(CommandSender sender, boolean enable) {
        if (enable) {
            if (!configManager.isDisableFallingBlocks()) {
                configManager.getMessages("physics.already-enabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "падающих блоков")));
                return;
            }
            configManager.setDisableFallingBlocks(false);
            configManager.getMessages("physics.enabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "падающих блоков")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика падающих блоков включена командой /olm physics on fallingblocks");
            }
        } else {
            if (configManager.isDisableFallingBlocks()) {
                configManager.getMessages("physics.already-disabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "падающих блоков")));
                return;
            }
            configManager.setDisableFallingBlocks(true);
            configManager.getMessages("physics.disabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "падающих блоков")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика падающих блоков отключена командой /olm physics off fallingblocks");
            }
        }
    }

    private void handleWaterFlow(CommandSender sender, boolean enable) {
        if (enable) {
            if (!configManager.isDisableWaterFlow()) {
                configManager.getMessages("physics.already-enabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "течения воды")));
                return;
            }
            configManager.setDisableWaterFlow(false);
            configManager.getMessages("physics.enabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "течения воды")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика течения воды включена командой /olm physics on water");
            }
        } else {
            if (configManager.isDisableWaterFlow()) {
                configManager.getMessages("physics.already-disabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "течения воды")));
                return;
            }
            configManager.setDisableWaterFlow(true);
            configManager.getMessages("physics.disabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "течения воды")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика течения воды отключена командой /olm physics off water");
            }
        }
    }

    private void handleLavaFlow(CommandSender sender, boolean enable) {
        if (enable) {
            if (!configManager.isDisableLavaFlow()) {
                configManager.getMessages("physics.already-enabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "течения лавы")));
                return;
            }
            configManager.setDisableLavaFlow(false);
            configManager.getMessages("physics.enabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "течения лавы")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика течения лавы включена командой /olm physics on lava");
            }
        } else {
            if (configManager.isDisableLavaFlow()) {
                configManager.getMessages("physics.already-disabled").forEach(msg ->
                        sender.sendMessage(msg.replace("{type}", "течения лавы")));
                return;
            }
            configManager.setDisableLavaFlow(true);
            configManager.getMessages("physics.disabled").forEach(msg ->
                    sender.sendMessage(msg.replace("{type}", "течения лавы")));
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Физика течения лавы отключена командой /olm physics off lava");
            }
        }
    }

    private String[] args;
}