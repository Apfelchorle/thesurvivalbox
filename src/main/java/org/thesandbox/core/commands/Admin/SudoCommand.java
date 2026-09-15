package org.thesandbox.core.commands.Admin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SudoCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public SudoCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length < 2)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <player> <command without slash>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player not found: " + args[0]));
            return true;
        }

        // Permission hierarchy enforcement
        if (!canControl(sender, target))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "You cannot send commands as this player as they have higher permissions!"));
            return true;
        }

        // Build the command string (strip any leading slash if provided)
        String cmd = joinArgs(args, 1);
        while (cmd.startsWith("/"))
        {
            cmd = cmd.substring(1);
        }
        if (cmd.isEmpty())
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "You must specify a command to run."));
            return true;
        }

        boolean ok = Bukkit.dispatchCommand(target, cmd);
        if (ok)
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Successfully sent the following command: \"" + cmd + "\" as " + target.getName()));
        }
        else
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Failed to run command as " + target.getName() + "."));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
            {
                if (p.getName().toLowerCase().startsWith(prefix) && canControl(sender, p))
                {
                    out.add(p.getName());
                }
            }
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }

    /* ===================== helpers ===================== */

    private static String joinArgs(String[] arr, int startIdx)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < arr.length; i++)
        {
            if (i > startIdx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * Returns true if 'actor' is allowed to sudo 'target' according to the role rules.
     * Console is treated as the highest level.
     */
    private static boolean canControl(CommandSender actor, Player target)
    {
        int actorLevel = roleLevel(actor);
        int targetLevel = roleLevel(target);

        // Console (or non-player senders) can control anyone
        if (!(actor instanceof Player)) return true;

        // Must not try to control someone with a higher level
        return actorLevel >= targetLevel;
    }

    /**
     * Role levels:
     * superuser=4, sradmin=3, admin=2, mod=1, default=0
     */
    private static int roleLevel(CommandSender who)
    {
        // Non-player (console) gets the highest power
        if (!(who instanceof Player p)) return 5;

        if (p.hasPermission("sandbox.superuser")) return 4;
        if (p.hasPermission("sandbox.admin"))   return 3;
        if (p.hasPermission("sandbox.staff"))     return 2;
        if (p.hasPermission("sandbox.staff"))       return 1;
        return 0;
    }
}