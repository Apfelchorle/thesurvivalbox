package org.thesandbox.core.commands.Fun;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class GravityCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public GravityCommand(JavaPlugin plugin)
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

        // Toggle gravity
        boolean newState = !player.hasGravity();
        player.setGravity(newState);

        player.sendMessage(CommandMessages.command((newState ? ChatColor.GREEN : ChatColor.RED) +
                (newState ? "Enabled your gravity!" : "Disabled your gravity!")));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}