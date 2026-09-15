package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.AutoClearService;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class AutoClearCommand implements ISubCommand
{
    private final AutoClearService service;

    public AutoClearCommand(AutoClearService service)
    {
        this.service = service;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length < 1)
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lUsage &8» &c/" + label + " <player>" ));
            return true;
        }

        // Works for offline players; name -> UUID (Bukkit cache)
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || target.getUniqueId() == null)
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cUnknown player: " + args[0]));
            return true;
        }

        // Toggle behavior: if already scheduled, disable; else enable
        if (service.contains(target.getUniqueId()))
        {
            service.remove(target.getUniqueId());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7AutoClear disabled for " + target.getName() + "."));
        }
        else
        {
            service.add(target.getUniqueId());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7AutoClear enabled for " + target.getName() + ". Their inventory will clear upon rejoining."));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }
        return Collections.emptyList();
    }
}