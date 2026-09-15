package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class RawSayCommand implements ISubCommand
{
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length == 0)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <message>"));
            return true;
        }

        // Support & color codes
        String message = ChatColor.translateAlternateColorCodes('&', String.join(" ", args));

        // Broadcast to all players (console will also see it in server log)
        Bukkit.broadcastMessage(CommandMessages.server("") + message);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // No special completions for a free-form message
        return Collections.emptyList();
    }
}