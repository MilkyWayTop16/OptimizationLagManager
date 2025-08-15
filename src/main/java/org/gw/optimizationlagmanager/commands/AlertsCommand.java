package org.gw.optimizationlagmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.optimizationlagmanager.OptimizationLagManager;
import org.gw.optimizationlagmanager.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class AlertsCommand implements OptimizationCommand {
    private final OptimizationLagManager plugin;
    private final ConfigManager configManager;
    private final Map<String, Boolean> alertSettings;

    public AlertsCommand(OptimizationLagManager plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.alertSettings = new HashMap<>();
    }

    public boolean isAlertsEnabled(String playerName) {
        return alertSettings.getOrDefault(playerName, true);
    }

    public void setAlertsEnabled(String playerName, boolean enabled) {
        alertSettings.put(playerName, enabled);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("olm.alerts")) {
            configManager.getMessages("no-permission").forEach(sender::sendMessage);
            return;
        }

        String targetPlayer = sender.getName();
        boolean toggle = !isAlertsEnabled(targetPlayer);
        int argIndex = 1;

        if (args.length >= 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            toggle = args[1].equalsIgnoreCase("on");
            argIndex = 2;
        }

        if (args.length > argIndex) {
            targetPlayer = args[argIndex];
            Player target = Bukkit.getPlayerExact(targetPlayer);
            if (target == null) {
                sender.sendMessage(configManager.getMessage("alerts.player-not-found").replace("{player}", targetPlayer));
                return;
            }
        }

        try {
            boolean currentState = isAlertsEnabled(targetPlayer);
            if (currentState == toggle) {
                String messageKey = toggle ? "alerts.already-enabled" : "alerts.already-disabled";
                sender.sendMessage(configManager.getMessage(messageKey).replace("{player}", targetPlayer));
                return;
            }

            setAlertsEnabled(targetPlayer, toggle);
            String statusMessage = toggle ? configManager.getMessage("alerts.enabled") : configManager.getMessage("alerts.disabled");
            statusMessage = statusMessage.replace("{player}", targetPlayer);
            sender.sendMessage(statusMessage);
            if (configManager.isConsoleLoggingEnabled()) {
                plugin.getLogger().info("Уведомления о лаг-машинах для игрока " + targetPlayer + " " + (toggle ? "включены" : "выключены") + " командой /olm alerts");
            }
        } catch (Exception e) {
            configManager.getMessages("alerts.error").forEach(sender::sendMessage);
            plugin.getLogger().severe("Ошибка при выполнении команды alerts: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}