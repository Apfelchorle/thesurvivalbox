package org.thesandbox.core.commands.misc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;
import java.util.stream.Collectors;

public class FreezeCommand implements ISubCommand, Listener
{
    // Global toggle (session only)
    private static boolean globalFreezeEnabled = false;

    // Frozen players (UUIDs)
    private static final Set<UUID> FROZEN = new HashSet<>();

    private final JavaPlugin plugin;

    public FreezeCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        final String who = (sender instanceof Player) ? sender.getName() : "CONSOLE";

        // /freeze  -> toggle global
        if (args.length == 0)
        {
            globalFreezeEnabled = !globalFreezeEnabled;
            if (globalFreezeEnabled)
            {
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Freezing all players"));
                // Freeze all non-mods online + notify them once
                for (Player p : Bukkit.getOnlinePlayers())
                {
                    if (isImmune(p)) continue;
                    freezePlayer(p, true);
                    p.sendMessage(CommandMessages.command(ChatColor.GRAY + "You have been frozen by a staff member."));
                }
            }
            else
            {
                // Unfreeze everyone
                FROZEN.clear();
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Unfreezing all players"));
                for (Player p : Bukkit.getOnlinePlayers())
                {
                    if (isImmune(p)) continue;
                    p.sendMessage(CommandMessages.command(ChatColor.GRAY + "You are free to move."));
                    
                }
            }
            return true;
        }

        // /freeze purge -> unfreeze everyone and disable global
        if (args.length == 1 && args[0].equalsIgnoreCase("purge"))
        {
            FROZEN.clear();
            globalFreezeEnabled = false;
            Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Purging all freezes"));
            return true;
        }

        // /freeze <player> -> toggle that player (respect mod immunity)
        if (args.length == 1)
        {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null)
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player not found: " + args[0]));
                return true;
            }

            if (isImmune(target))
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "You cannot freeze staff members."));
                return true;
            }

            UUID id = target.getUniqueId();
            if (FROZEN.contains(id))
            {
                FROZEN.remove(id);
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Unfreezing " + target.getName()));
                target.sendMessage(CommandMessages.command(ChatColor.GRAY + "You are free to move."));
            }
            else
            {
                freezePlayer(target, true);
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Freezing " + target.getName()));
                target.sendMessage(CommandMessages.command(ChatColor.GRAY + "You have been frozen by a staff member."));
            }
            return true;
        }

        sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " [target | purge]"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            list.add("purge");
            list.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            list.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return list;
        }
        return Collections.emptyList();
    }

    /* ===================== Listeners ===================== */

    // Auto-freeze joiners if global is enabled (and not immune), send one-time gray message
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event)
    {
        Player p = event.getPlayer();
        if (globalFreezeEnabled && !isImmune(p))
        {
            freezePlayer(p, true);
        }
    }

    // Hard-stop movement: cancel any movement where position changes (rotation allowed)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event)
    {
        Player p = event.getPlayer();
        if (!isFrozen(p)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // If coordinates change at all (even within same block), cancel
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())
        {
            // Cancel movement entirely. This also freezes rotation; if you want to allow head movement,
            // use event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
            event.setCancelled(true);
        }
    }

    // Block all teleports for frozen players
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event)
    {
        Player p = event.getPlayer();
        if (isFrozen(p))
        {
            event.setCancelled(true);
        }
    }

    /* ===================== Helpers ===================== */

    private boolean isImmune(Player p)
    {
        return p != null && p.hasPermission("sandbox.staff");
    }

    private boolean isFrozen(Player p)
    {
        return p != null && !isImmune(p) && FROZEN.contains(p.getUniqueId());
    }

    /** Freeze and optionally notify the player one time in gray. */
    private void freezePlayer(Player p, boolean notify)
    {
        FROZEN.add(p.getUniqueId());
    }
}