package org.gw.optimizationlagmanager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class CommandsHandler implements CommandExecutor {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<String, OptimizationCommand> commands;
    private final AlertsCommand alertsCommand;

    public CommandsHandler(OptimizationLagManager plugin, ConfigManager configManager,
                           ReloadCommand reloadCommand, RedstoneLagCommand redstoneLagCommand,
                           AlertsCommand alertsCommand, PhysicsCommand physicsCommand) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.alertsCommand = alertsCommand;
        this.commands = new HashMap<>();
        commands.put("reload", reloadCommand);
        if (configManager.isPhysicsOptimizationEnabled()) {
            commands.put("physics", physicsCommand);
        }
        if (configManager.isRedstoneOptimizationEnabled()) {
            commands.put("redstonelag", redstoneLagCommand);
            commands.put("alerts", alertsCommand);
        }
    }

    public AlertsCommand getAlertsCommand() {
        return alertsCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission("olm.reload") ||
                    (configManager.isRedstoneOptimizationEnabled() && (sender.hasPermission("olm.redstonelag") || sender.hasPermission("olm.alerts"))) ||
                    (configManager.isPhysicsOptimizationEnabled() && sender.hasPermission("olm.physics"))) {
                configManager.getMessages("help").forEach(sender::sendMessage);
            } else {
                configManager.getMessages("no-permission").forEach(sender::sendMessage);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        OptimizationCommand cmd = commands.get(subCommand);
        if (cmd == null) {
            configManager.getMessages("no-permission").forEach(sender::sendMessage);
            return true;
        }
        cmd.execute(sender, args);
        return true;
    }
}