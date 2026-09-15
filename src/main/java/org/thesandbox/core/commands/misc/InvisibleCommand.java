package org.thesandbox.core.commands.misc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InvisibleCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public InvisibleCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // /invisible clear
        if (args.length == 1 && args[0].equalsIgnoreCase("clear"))
        {
            int cleared = 0;
            for (Player p : Bukkit.getOnlinePlayers())
            {
                if (p.hasPotionEffect(PotionEffectType.INVISIBILITY) && !p.hasPermission("sandbox.staff"))
                {
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    cleared++;
                }
            }

            String who = (sender instanceof Player) ? sender.getName() : "CONSOLE";
            Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Clearing all players of invisibility potion effects"));

            sender.sendMessage(CommandMessages.command(ChatColor.YELLOW + "Cleared invisibility from " + cleared + " player(s)."));
            return true;
        }

        // /invisible -> list all invisible players (+ mark staff)
        List<String> invisible = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
        {
            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY))
            {
                if (p.hasPermission("sandbox.staff")) {
                    invisible.add(p.getName() + ChatColor.RED + " (STAFF)" + ChatColor.GRAY);
                } else {
                    invisible.add(p.getName());
                }
            }
        }

        if (invisible.isEmpty())
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "There are no invisible players."));
        }
        else
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Invisible players: " + ChatColor.GRAY + String.join(", ", invisible)));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            return Collections.singletonList("clear");
        }
        return Collections.emptyList();
    }
}