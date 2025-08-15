package org.gw.optimizationlagmanager.commands;

import org.bukkit.command.CommandSender;

public interface OptimizationCommand {
    void execute(CommandSender sender, String[] args);
}