package org.thesandbox.core.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.thesandbox.core.services.AutoClearService;

public class AutoClearListener implements Listener
{
    private final AutoClearService service;

    public AutoClearListener(AutoClearService service) { this.service = service; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        Player p = event.getPlayer();
        if (!service.contains(p.getUniqueId())) return;

        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);
        p.setItemOnCursor(null);
        p.updateInventory();

        p.sendMessage(ChatColor.RED + "Your inventory has been cleared automatically by a staff member.");
        Bukkit.getLogger().info("Cleared " + p.getName() + " automatically.");
        service.remove(p.getUniqueId());
    }
}