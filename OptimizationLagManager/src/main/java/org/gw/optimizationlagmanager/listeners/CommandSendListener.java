package org.gw.optimizationlagmanager.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

public class CommandSendListener implements Listener {
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        boolean hasAnyPermission = event.getPlayer().hasPermission("olm.reload") ||
                event.getPlayer().hasPermission("olm.redstonelag");

        if (!hasAnyPermission) {
            event.getCommands().remove("olm");
            event.getCommands().remove("optimizationlagmanager");
            event.getCommands().remove("olm:olm");
            event.getCommands().remove("optimizationlagmanager:olm");
        }
    }
}