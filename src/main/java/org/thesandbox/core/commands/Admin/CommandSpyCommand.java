package org.thesandbox.core.commands.Admin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CommandSpyCommand implements ISubCommand
{
    private final TheSandboxCore plugin;

    public CommandSpyCommand(TheSandboxCore plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lError &8» &cOnly players can use this command."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle"))
        {
            boolean currentlyEnabled = !plugin.isCmdSpyDisabled(player.getUniqueId());
            boolean newState = !currentlyEnabled;

            plugin.setCmdSpy(player, newState);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&7&lCommand &8» &7CommandSpy has been "
                    + (newState ? "enabled" : "disabled")
                    + "&7."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub)
        {
            case "on":
            {
                plugin.setCmdSpy(player, true);

                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7CommandSpy has been enabled."));
                return true;
            }

            case "off":
            {
                plugin.setCmdSpy(player, false);

                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7CommandSpy has been &cdisabled."));
                return true;
            }

            case "status":
            {
                boolean enabled = !plugin.isCmdSpyDisabled(player.getUniqueId());

                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7CommandSpy is currently "
                        + (enabled ? "enabled" : "disabled")
                        + "&7."));
                return true;
            }

            default:
            {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lUsage &8» &c/" + label + " [on|off|toggle|status]"));
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            return Arrays.asList("on", "off", "toggle", "status");
        }

        return Collections.emptyList();
    }
}