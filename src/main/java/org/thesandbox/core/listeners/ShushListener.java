package org.thesandbox.core.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.thesandbox.core.TheSandboxCore;

public class ShushListener implements Listener {

    private final TheSandboxCore plugin;

    public ShushListener(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (plugin.getShushService() != null && plugin.getShushService().isEnabled(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Shush mode is enabled, you will not receive staff notifications.");
        }
    }
}