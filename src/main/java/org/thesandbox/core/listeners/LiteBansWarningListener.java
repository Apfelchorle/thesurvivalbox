package org.thesandbox.core.listeners;

import litebans.api.Entry;
import litebans.api.Events;
import litebans.api.exception.MissingImplementationException;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;

import java.util.UUID;

public final class LiteBansWarningListener extends Events.Listener {
    private final TheSandboxCore plugin;
    private boolean registered;

    public LiteBansWarningListener(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (registered) {
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("LiteBans")) {
            plugin.getLogger().info("LiteBans was not found, so warning actions were not registered.");
            return;
        }

        try {
            Events.get().register(this);
            registered = true;
            plugin.getLogger().info("Registered LiteBans warning actions.");
        } catch (MissingImplementationException ex) {
            plugin.getLogger().warning("LiteBans API was not available, so warning actions were not registered: " + ex.getMessage());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register LiteBans warning actions: " + t.getMessage());
        }
    }

    public void unregister() {
        if (!registered) {
            return;
        }

        try {
            Events.get().unregister(this);
        } catch (Throwable ignored) {
            // Avoid reload/shutdown errors if LiteBans is already disabled.
        } finally {
            registered = false;
        }
    }

    @Override
    public void entryAdded(Entry entry) {
        if (entry == null || !"warn".equalsIgnoreCase(entry.getType())) {
            return;
        }

        UUID targetUuid = parseUuid(entry.getUuid());
        if (targetUuid == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> applyWarningActions(targetUuid));
    }

    private void applyWarningActions(UUID targetUuid) {
        Player player = Bukkit.getPlayer(targetUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        // Set gamemode to survival.
        player.setGameMode(GameMode.SURVIVAL);

        // Clear inventory.
        player.getInventory().clear();

        // Strike with lightning effect in a 3x3 around the player.
        final Location targetPos = player.getLocation();
        final World world = player.getWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                final Location strikePos = new Location(
                        world,
                        targetPos.getBlockX() + x,
                        targetPos.getBlockY(),
                        targetPos.getBlockZ() + z
                );
                world.strikeLightning(strikePos);
            }
        }

        // Kill.
        player.setHealth(0.0);
    }

    private UUID parseUuid(String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ignored) {
            // LiteBans can store UUID strings without dashes on some setups.
        }

        String compact = rawUuid.replace("-", "").trim();
        if (compact.length() != 32) {
            return null;
        }

        try {
            return UUID.fromString(
                    compact.substring(0, 8) + "-" +
                    compact.substring(8, 12) + "-" +
                    compact.substring(12, 16) + "-" +
                    compact.substring(16, 20) + "-" +
                    compact.substring(20)
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
