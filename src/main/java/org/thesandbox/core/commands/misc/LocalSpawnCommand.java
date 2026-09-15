package org.thesandbox.core.commands.misc;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class LocalSpawnCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public LocalSpawnCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "This command can only be used in-game."));
            return true;
        }

        Location spawn = player.getWorld().getSpawnLocation();

        // Teleport synchronously (safe on main thread)
        player.teleport(spawn);

        player.sendMessage(CommandMessages.command(ChatColor.YELLOW + "Teleported to spawnpoint for world \"" +
                player.getWorld().getName() + "\"."));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}