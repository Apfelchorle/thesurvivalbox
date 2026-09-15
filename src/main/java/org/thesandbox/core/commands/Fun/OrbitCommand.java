package org.thesandbox.core.commands.Fun;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class OrbitCommand implements ISubCommand {
    private final TheSandboxCore plugin;

    // Track active orbits: target UUID -> repeating task
    private final Map<UUID, BukkitTask> orbitTasks = new HashMap<>();

    // Match TFM-style behavior: constant upward strength; no lateral motion
    private static final double STRENGTH = 10.0; // equivalent to "strength" in your snippet
    private static final long   TICK_INTERVAL = 2L; // re-apply every 2 ticks for smooth lift

    public OrbitCommand(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sandbox.staff")) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "You don’t have permission."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            // fallback: partial match
            List<Player> matches = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            if (matches.size() == 1) {
                target = matches.get(0);
            }
        }

        if (target == null) {
            sender.sendMessage(CommandMessages.error(ChatColor.GRAY + "Player not found."));
            return true;
        }

        UUID tid = target.getUniqueId();
        String actor = sender.getName();

        // Toggle OFF if already orbiting
        if (orbitTasks.containsKey(tid)) {
            stopOrbit(target);
            if (!sender.equals(target)) {
                target.sendMessage(CommandMessages.command(ChatColor.YELLOW + "You are now descending back into the world."));
                target.setGameMode(GameMode.CREATIVE);
                target.sendMessage(CommandMessages.command(ChatColor.GRAY + "Your gamemode was updated to CREATIVE."));
            }
            // Broadcast to all players
            Bukkit.broadcastMessage(CommandMessages.server(ChatColor.AQUA + actor + " - Stopped orbiting " + target.getName()));
            return true;
        }

        // Toggle ON: set to SURVIVAL and start vertical orbit (straight up)
        target.setGameMode(GameMode.SURVIVAL);
        target.sendMessage(CommandMessages.command(ChatColor.GRAY + "Your gamemode was updated to SURVIVAL."));
        startOrbit(target);

        // Broadcast to all players
        Bukkit.broadcastMessage(CommandMessages.server(ChatColor.AQUA + actor + " - Orbiting " + target.getName()));

        if (!sender.equals(target)) {
            target.sendMessage(CommandMessages.command(ChatColor.GOLD + "BLAST OFF!"));
        }
        return true;
    }

    private void startOrbit(Player p) {
        final UUID id = p.getUniqueId();

        // Immediate upward push (mirrors: player.setVelocity(new Vector(0, strength, 0)))
        p.setVelocity(new Vector(0, STRENGTH, 0));
        p.setFallDistance(0.0f);

        // Keep applying straight-up velocity (no lateral/tornado effect)
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || p.isDead()) {
                stopOrbit(p);
                return;
            }
            p.setVelocity(new Vector(0, STRENGTH, 0));
            p.setFallDistance(0.0f);
        }, TICK_INTERVAL, TICK_INTERVAL);

        orbitTasks.put(id, task);
    }

    private void stopOrbit(Player p) {
        UUID id = p.getUniqueId();
        BukkitTask t = orbitTasks.remove(id);
        if (t != null) t.cancel();
        // zero out velocity so they stop drifting
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0.0f);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}