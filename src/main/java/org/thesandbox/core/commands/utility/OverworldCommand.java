package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class OverworldCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public OverworldCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "This command can only be used in-game."));
            return true;
        }

        String worldName = "world";
        World world = Bukkit.getWorld(worldName);

        if (world == null)
        {
            // Try to load the world only if a folder exists
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists() && worldFolder.isDirectory())
            {
                try
                {
                    world = WorldCreator.name(worldName).createWorld();
                }
                catch (Throwable t)
                {
                    world = null;
                }
            }
        }

        if (world == null)
        {
            player.sendMessage(CommandMessages.error(ChatColor.RED + "Overworld is not loaded."));
            return true;
        }

        boolean success = player.teleport(world.getSpawnLocation());
        if (success)
        {
            player.sendMessage(CommandMessages.command(ChatColor.GRAY + "Teleporting you to the overworld."));
        }
        else
        {
            player.sendMessage(CommandMessages.error(ChatColor.RED + "Failed to teleport."));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}