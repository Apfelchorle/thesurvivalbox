package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;
import java.util.stream.Collectors;

public class WhoHasCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public WhoHasCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length < 1)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <item> [clear]"));
            return true;
        }

        final boolean doClear = args.length >= 2 && "clear".equalsIgnoreCase(args[1]);
        final String materialName = args[0];
        final Material material = Material.matchMaterial(materialName);

        if (material == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Invalid item: " + materialName));
            return true;
        }

        // CLEAR mode: broadcast &b and do not list users
        if (doClear)
        {
            if (!sender.hasPermission("sandbox.staff"))
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "You do not have permission to clear inventories."));
                return true;
            }

            final String actor = (sender instanceof Player p) ? p.getName() : "CONSOLE";
            Bukkit.broadcastMessage(CommandMessages.server(ChatColor.AQUA + actor + " - Clearing all inventories of " + material.name()));

            for (final Player target : Bukkit.getOnlinePlayers())
            {
                // Do not clear from staff
                if (target.hasPermission("sandbox.staff"))
                    continue;

                if (target.getInventory().contains(material))
                {
                    target.getInventory().remove(material);
                    target.updateInventory();
                }
            }
            return true; // do not list players when clearing
        }

        // LIST mode: show who has the item
        final List<String> players = new ArrayList<>();
        for (final Player player : Bukkit.getOnlinePlayers())
        {
            if (player.getInventory().contains(material))
            {
                players.add(player.getName());
            }
        }

        if (players.isEmpty())
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "There are no players with " + material.name()));
        }
        else
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Players with " + material.name() + ": "
                    + ChatColor.RED + String.join(", ", players)));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            final String partial = args[0].toUpperCase(Locale.ENGLISH);
            return Arrays.stream(Material.values())
                    .map(Enum::name)
                    .filter(n -> n.startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && sender.hasPermission("sandbox.staff"))
        {
            return Collections.singletonList("clear");
        }

        return Collections.emptyList();
    }
}