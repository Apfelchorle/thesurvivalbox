package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LoopCommand implements ISubCommand
{
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // Permission: only superusers
        if (!sender.hasPermission("sandbox.superuser"))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only superusers can use /" + label + "."));
            return true;
        }

        if (args.length < 2)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <amount> <command>"));
            return true;
        }

        // Parse amount (no cap as requested)
        final long amount;
        try
        {
            amount = Long.parseLong(args[0]);
            if (amount < 1)
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Amount must be a positive integer."));
                return true;
            }
        }
        catch (NumberFormatException ex)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Invalid amount: " + args[0]));
            return true;
        }

        // Build the command to execute (strip leading slash if present)
        String toRun = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (toRun.isEmpty())
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Please provide a command to run."));
            return true;
        }
        if (toRun.startsWith("/"))
        {
            toRun = toRun.substring(1);
        }

        // Basic recursion guard: don't allow looping /loop
        String base = toRun.split("\\s+", 2)[0].toLowerCase(Locale.ENGLISH);
        if (base.equalsIgnoreCase(label) || base.equalsIgnoreCase(command.getName()) || base.equals("loop"))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Refusing to loop the /loop command."));
            return true;
        }

        sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Looping " + ChatColor.RED + amount + ChatColor.GRAY + "x: " + ChatColor.RED + "/" + toRun));

        // Execute synchronously on the main thread
        for (long i = 0; i < amount; i++)
        {
            // We intentionally ignore the boolean result to keep firing even if one fails
            Bukkit.getServer().dispatchCommand(sender, toRun);
        }

        sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Finished looping command."));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            return Arrays.asList("1", "5", "10", "100");
        }
        // Let the server's own tab-complete handle command names/args beyond <amount>
        return Collections.emptyList();
    }
}