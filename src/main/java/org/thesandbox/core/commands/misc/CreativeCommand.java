package org.thesandbox.core.commands.misc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class CreativeCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public CreativeCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length == 0)
        {
            // /adventure
            if (!(sender instanceof Player player))
            {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7Only players can change their own gamemode."));
                return true;
            }

            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&7&lCommand &8» &7Your gamemode was set to CREATIVE."));
            return true;
        }

        if (args[0].equalsIgnoreCase("-a"))
        {
            // /adventure -a
            if (!sender.hasPermission("sandbox.staff"))
            {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cNo permission."));
                return true;
            }

            for (Player target : Bukkit.getOnlinePlayers())
            {
                target.setGameMode(GameMode.CREATIVE);
            }

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lServer &8» &c" + sender.getName() + " changed everyone's gamemode to CREATIVE."));
            return true;
        }

        // /adventure <player>
        if (!sender.hasPermission("sandbox.staff"))
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cNo permission."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null)
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cPlayer not found."));
            return true;
        }

        target.setGameMode(GameMode.CREATIVE);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lServer &8» &c" + sender.getName() + " changed " + target.getName() + "'s gamemode to CREATIVE."));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&7&lCommand &8» &7Set " + target.getName() + "'s gamemode to CREATIVE."));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            // Suggest "-a" and online player names
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();

            if (sender.hasPermission("sandbox.staff"))
            {
                return new java.util.ArrayList<>() {{
                    add("-a");
                    addAll(names);
                }};
            }
            else
            {
                return names;
            }
        }

        return Collections.emptyList();
    }
}