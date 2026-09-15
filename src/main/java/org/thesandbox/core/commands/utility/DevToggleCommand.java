package org.thesandbox.core.commands.utility;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.commands.meta.CommandName;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@CommandName("devtoggle")
public class DevToggleCommand implements ISubCommand {

    private final TheSandboxCore plugin;

    public DevToggleCommand(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p && !p.hasPermission("sandbox.superuser")) {
            p.sendMessage(CommandMessages.error(ChatColor.RED + "You do not have permission to use this command."));
            return true;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("discord")) {
            sendUsage(sender, label);
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Discord chat bridge is currently " + statusText() + ChatColor.GRAY + "."));
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender, label);
            return true;
        }

        boolean desired = !plugin.isDiscordChatBridgeEnabled();
        plugin.setDiscordChatBridgeEnabled(desired);
        sender.sendMessage(CommandMessages.command((desired ? ChatColor.GREEN : ChatColor.RED) + "Minecraft/Discord chat bridge has been " + (desired ? "enabled" : "disabled") + "."));
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " discord [status]"));
        sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Currently: " + statusText()));
    }

    private String statusText() {
        return plugin.isDiscordChatBridgeEnabled()
                ? ChatColor.GREEN + "enabled"
                : ChatColor.RED + "disabled";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof Player p && !p.hasPermission("sandbox.superuser")) return out;

        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            if ("discord".startsWith(pref)) out.add("discord");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("discord")) {
            String pref = args[1].toLowerCase(Locale.ROOT);
            if ("status".startsWith(pref)) out.add("status");
        }
        return out;
    }
}
