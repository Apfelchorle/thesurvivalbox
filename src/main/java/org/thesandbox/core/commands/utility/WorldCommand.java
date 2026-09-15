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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorldCommand implements ISubCommand
{
    private static final List<String> ALLOWED_WORLDS = List.of(
            "world",
            "world_nether",
            "world_the_end",
            "masterbuilderworld",
            "flatlands"
    );

    private final JavaPlugin plugin;

    public WorldCommand(JavaPlugin plugin)
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

        if (args.length < 1)
        {
            player.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <" + String.join("|", ALLOWED_WORLDS) + ">"));
            return true;
        }

        String requested = args[0].toLowerCase();
        if (!ALLOWED_WORLDS.contains(requested))
        {
            player.sendMessage(CommandMessages.error(ChatColor.RED + "Unknown world: " + requested));
            return true;
        }

        World world = Bukkit.getWorld(requested);
        if (world == null)
        {
            // Try to load the world if a folder exists (avoid generating accidental new worlds)
            File worldFolder = new File(Bukkit.getWorldContainer(), requested);
            if (worldFolder.exists() && worldFolder.isDirectory())
            {
                try
                {
                    world = WorldCreator.name(requested).createWorld();
                }
                catch (Throwable t)
                {
                    world = null;
                }
            }
        }

        if (world == null)
        {
            player.sendMessage(CommandMessages.error(ChatColor.RED + "That world does not exist."));
            return true;
        }

        // Teleport to world spawn
        boolean success = player.teleport(world.getSpawnLocation());
        if (success)
        {
            player.sendMessage(CommandMessages.command(ChatColor.GRAY + "Teleporting you to " + requested + "."));
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
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String w : ALLOWED_WORLDS)
            {
                if (w.startsWith(prefix))
                {
                    out.add(w);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}