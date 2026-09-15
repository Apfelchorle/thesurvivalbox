package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.AutoTpService;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AutoTpCommand implements ISubCommand, TabCompleter {

    private final JavaPlugin plugin;
    private final AutoTpService service;

    public AutoTpCommand(JavaPlugin plugin, AutoTpService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lUsage &8» &c/" + label + " <player>" ));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cUnknown player: " + args[0]));
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean nowEnabled = service.toggle(uuid);

        if (nowEnabled) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7AutoTP enabled for " + target.getName() + "."));
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7AutoTP disabled for " + target.getName() + "."));
        }
        return true;
    }

    // ISubCommand tab completion (your interface)
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    // Bukkit TabCompleter method (needed to satisfy the interface)
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Delegate to the same logic
        List<String> result = tabComplete(sender, command, alias, args);
        return (result != null) ? result : List.of();
    }
}