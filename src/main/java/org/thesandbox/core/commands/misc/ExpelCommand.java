package org.thesandbox.core.commands.misc;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExpelCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public ExpelCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player playerSender))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "This command can only be used in-game."));
            return true;
        }

        // Defaults
        double radius = 20.0;
        double strength = 5.0;

        // Parse radius [1.0 .. 100.0]
        if (args.length >= 1)
        {
            try
            {
                radius = Math.max(1.0, Math.min(100.0, Double.parseDouble(args[0])));
            }
            catch (NumberFormatException ignored) { }
        }

        // Parse strength [0.0 .. 50.0]
        if (args.length >= 2)
        {
            try
            {
                strength = Math.max(0.0, Math.min(50.0, Double.parseDouble(args[1])));
            }
            catch (NumberFormatException ignored) { }
        }

        List<String> pushedPlayers = new ArrayList<>();

        final Vector senderPos = playerSender.getLocation().toVector();
        final World world = playerSender.getWorld();

        for (Player target : world.getPlayers())
        {
            if (target.equals(playerSender))
                continue;

            final Location targetLoc = target.getLocation();
            final Vector targetVec = targetLoc.toVector();

            boolean inRange = false;
            try
            {
                inRange = targetVec.distanceSquared(senderPos) <= (radius * radius);
            }
            catch (IllegalArgumentException ignored) { }

            if (!inRange)
                continue;

            // Harmless “pop” explosion (no fire, no block damage) — use best-available signature
            try {
                world.createExplosion(targetLoc, 0.0f, false, false);
            } catch (Throwable ignored) {
                try {
                    world.createExplosion(targetLoc, 0.0f, false);
                } catch (Throwable ignored2) {
                    try {
                        world.createExplosion(targetLoc, 0.0f);
                    } catch (Throwable ignored3) { }
                }
            }

            // Ensure not flying (replacement for FUtil.setFlying(..., false))
            try {
                target.setFlying(false);
            } catch (Throwable ignored) { }
            try {
                target.setAllowFlight(false);
            } catch (Throwable ignored) { }

            // Compute push vector away from sender
            Vector delta = targetVec.clone().subtract(senderPos);
            if (delta.lengthSquared() < 1.0e-6)
            {
                // If on the same exact spot, nudge slightly to avoid NaN from normalize()
                delta = new Vector(0, 0.1, 0);
            }
            Vector velocity = delta.normalize().multiply(strength);
            target.setVelocity(velocity);

            pushedPlayers.add(target.getName());
        }

        if (pushedPlayers.isEmpty())
        {
            playerSender.sendMessage(CommandMessages.error(ChatColor.RED + "No players pushed."));
        }
        else
        {
            playerSender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Successfully pushed " + pushedPlayers.size() + " players: " + String.join(", ", pushedPlayers)));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}