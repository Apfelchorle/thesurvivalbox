package org.thesandbox.core.commands.utility;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ShushCommand implements ISubCommand {

    private final TheSandboxCore plugin;

    public ShushCommand(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can use /" + label + "."));
            return true;
        }

        boolean toggle;
        if (args.length == 0) {
            toggle = !plugin.getShushService().isEnabled(p.getUniqueId());
        } else if (args.length == 1) {
            String a = args[0].toLowerCase(Locale.ENGLISH);
            if ("on".equals(a)) toggle = true;
            else if ("off".equals(a)) toggle = false;
            else {
                p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " [on|off]"));
                return true;
            }
        } else {
            p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " [on|off]"));
            return true;
        }

        if (toggle) {
            if (plugin.getShushService().isEnabled(p.getUniqueId())) {
                p.sendMessage(CommandMessages.error(ChatColor.RED + "Shush mode is already enabled."));
                return true;
            }
            plugin.getShushService().enable(p);
            p.sendMessage(CommandMessages.command(ChatColor.RED + "Shush mode is enabled, you will not receive staff notifications."));
        } else {
            if (!plugin.getShushService().isEnabled(p.getUniqueId())) {
                p.sendMessage(CommandMessages.error(ChatColor.RED + "Shush mode is already disabled."));
                return true;
            }
            plugin.getShushService().disable(p);
            p.sendMessage(CommandMessages.command(ChatColor.GREEN + "Shush mode disabled. Your previous spy/chat settings have been restored."));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("on", "off");
        }
        return java.util.Collections.emptyList();
    }
}