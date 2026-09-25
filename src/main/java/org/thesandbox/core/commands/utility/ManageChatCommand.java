package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.services.ManageChatService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /managechat mute | clear
 * - mute: toggles global mute, broadcasts in red.
 * - clear: clear chat for everyone EXCEPT players with "sandbox.staff", then broadcast in red.
 */
public class ManageChatCommand implements ISubCommand, TabCompleter {

    private final JavaPlugin plugin;
    private final ManageChatService service;

    public ManageChatCommand(JavaPlugin plugin, ManageChatService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 1) {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <mute|clear>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mute": {
                if (!service.isMuted()) {
                    service.mute();
                    Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - Muting the chat"));
                } else {
                    service.unmute();
                    Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - Unmuting the chat"));
                }
                return true;
            }
            case "clear": {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("sandbox.staff")) continue; // don't clear for mods
                    for (int i = 0; i < 100; i++) p.sendMessage(" ");
                }
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - Cleared the chat"));
                return true;
            }
            case "forceclear": {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    for (int i = 0; i < 100; i++) p.sendMessage(" ");
                }
                Bukkit.broadcastMessage(CommandMessages.server("&c" + sender.getName() + " - Force Cleared the chat"));
                return true;
            }
            default: {
                sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <mute|clear|forceclear>"));
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            if ("mute".startsWith(pref)) out.add("mute");
            if ("clear".startsWith(pref)) out.add("clear");
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> r = tabComplete(sender, command, alias, args);
        return (r != null) ? r : java.util.Collections.emptyList();
    }
}
