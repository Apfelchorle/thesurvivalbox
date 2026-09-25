package org.thesandbox.core.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.thesandbox.core.services.AutoTpService;

import java.util.Random;

public class AutoTpListener implements Listener {

    private final JavaPlugin plugin;
    private final AutoTpService service;
    private final Random rng = new Random();

    private static final int MAX_RADIUS = 5000; // max offset from player’s current location

    public AutoTpListener(JavaPlugin plugin, AutoTpService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // One-shot: remove on join; if not present, do nothing.
        if (!service.consume(p.getUniqueId())) return;

        new BukkitRunnable() {
            @Override public void run() {
                World w = p.getWorld();
                Location base = p.getLocation();

                // Random X/Z offset within a circle of radius 1000
                double angle = rng.nextDouble() * Math.PI * 2;
                double radius = rng.nextDouble() * MAX_RADIUS;
                int dx = (int) Math.round(radius * Math.cos(angle));
                int dz = (int) Math.round(radius * Math.sin(angle));

                int x = base.getBlockX() + dx;
                int z = base.getBlockZ() + dz;

                // Fixed Y = 50
                Location target = new Location(w, x + 0.5, 50, z + 0.5);

                // Teleport asynchronously (Paper API) and notify
                p.teleportAsync(target);
            }
        }.runTaskLater(plugin, 2L);
    }
}