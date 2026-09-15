package org.thesandbox.core.commands.Admin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;

public class WildcardCommand implements ISubCommand
{
    private final TheSandboxCore core;

    public WildcardCommand(TheSandboxCore core)
    {
        this.core = core;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // Block console usage like the original behavior
        if (!(sender instanceof Player))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "This command cannot be used from console."));
            return true;
        }

        if (args.length == 0)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <command...> [use '?' as optional placeholder]"));
            return true;
        }

        // Base command name (strip a leading slash for lookup)
        String baseName = args[0];
        if (baseName.startsWith("/")) baseName = baseName.substring(1);

        // Gather names + aliases to compare to blocklist
        Set<String> names = new HashSet<>();
        names.add(baseName.toLowerCase(Locale.ENGLISH));

        PluginCommand pc = core.getServer().getPluginCommand(baseName);
        if (pc != null)
        {
            for (String a : pc.getAliases())
            {
                if (a != null && !a.isEmpty())
                    names.add(a.toLowerCase(Locale.ENGLISH));
            }
        }

        // Blocked commands unless sandbox.superuser
        boolean isSuperuser = sender.hasPermission("sandbox.superuser");
        if (!isSuperuser)
        {
            List<String> blocked = core.getConfig().getStringList("wildcard.blocked-commands");
            if (blocked != null)
            {
                for (String b : blocked)
                {
                    if (b == null || b.isEmpty()) continue;
                    String bb = b.replaceFirst("^/", "").toLowerCase(Locale.ENGLISH);
                    if (names.contains(bb))
                    {
                        sender.sendMessage(CommandMessages.error(ChatColor.RED + "I applaud you for trying."));
                        return true;
                    }
                }
            }
        }

        // Build the template (don’t require '?')
        String template = String.join(" ", args);
        if (template.startsWith("/")) template = template.substring(1);

        boolean hasPlaceholder = template.indexOf('?') >= 0;

        for (Player target : core.getServer().getOnlinePlayers())
        {
            final String run;
            if (hasPlaceholder)
            {
                // Replace every '?' with the player's name
                run = template.replace("?", target.getName());
            }
            else
            {
                // No placeholder → append player name at end
                run = template + " " + target.getName();
            }

            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Running: /" + run));
            Bukkit.dispatchCommand(sender, run);
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            return Collections.singletonList("<command>");
        }
        return Collections.emptyList();
    }
}