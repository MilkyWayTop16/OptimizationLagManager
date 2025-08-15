package org.gw.optimizationlagmanager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.gw.optimizationlagmanager.managers.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandsTabCompleter implements TabCompleter {
    private final ConfigManager configManager;

    public CommandsTabCompleter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("olm.reload")) {
                suggestions.add("reload");
            }
            if (configManager.isRedstoneOptimizationEnabled()) {
                if (sender.hasPermission("olm.redstonelag")) {
                    suggestions.add("redstonelag");
                }
                if (sender.hasPermission("olm.alerts")) {
                    suggestions.add("alerts");
                }
            }
            if (configManager.isPhysicsOptimizationEnabled() && sender.hasPermission("olm.physics")) {
                suggestions.add("physics");
            }
            return suggestions.isEmpty() ? null : suggestions;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("redstonelag") && configManager.isRedstoneOptimizationEnabled() && sender.hasPermission("olm.redstonelag")) {
                return Arrays.asList("on", "off");
            }
            if (args[0].equalsIgnoreCase("alerts") && configManager.isRedstoneOptimizationEnabled() && sender.hasPermission("olm.alerts")) {
                return Arrays.asList("on", "off");
            }
            if (args[0].equalsIgnoreCase("physics") && configManager.isPhysicsOptimizationEnabled() && sender.hasPermission("olm.physics")) {
                return Arrays.asList("on", "off");
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("physics") && configManager.isPhysicsOptimizationEnabled() && sender.hasPermission("olm.physics")) {
            return Arrays.asList("fallingblocks", "water", "lava");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("alerts") && configManager.isRedstoneOptimizationEnabled() && sender.hasPermission("olm.alerts")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return null;
    }
}