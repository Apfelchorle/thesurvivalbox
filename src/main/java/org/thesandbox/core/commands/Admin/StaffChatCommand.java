package org.thesandbox.core.commands.Admin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.login.LoginService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StaffChatCommand implements ISubCommand {

    private final TheSandboxCore plugin;

    public StaffChatCommand(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        // Console: broadcast whatever is typed to staff chat
        if (sender instanceof ConsoleCommandSender) {
            if (args.length == 0) {
                sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <message>"));
                return true;
            }
            String msg = String.join(" ", args);
            plugin.broadcastStaffChat(
                    "Console",
                    msg,
                    "Console",
                    "&8",
                    TheSandboxCore.Source.MINECRAFT
            );
            return true;
        }

        // Player branch
        if (!(sender instanceof Player p)) return true;

        if (!p.hasPermission("sandbox.staff")) {
            p.sendMessage(CommandMessages.error(ChatColor.RED + "You don't have permission to use staff chat."));
            return true;
        }

        // /sc hide on|off
        if (args.length >= 1 && "hide".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " hide <on|off>"));
                return true;
            }
            String v = args[1].toLowerCase(Locale.ENGLISH);
            if (!v.equals("on") && !v.equals("off")) {
                p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " hide <on|off>"));
                return true;
            }
            boolean hidden = v.equals("on");
            plugin.setStaffChatHidden(p, hidden);
            if (hidden) {
                p.sendMessage(CommandMessages.command(ChatColor.GRAY + "You will no longer receive staff chat (and cannot speak in it). Use /" + label + " hide off to re-enable."));
            } else {
                p.sendMessage(CommandMessages.command(ChatColor.GREEN + "You will now receive staff chat."));
            }
            return true;
        }

        // Block sending if hidden
        if (plugin.isStaffChatHidden(p.getUniqueId())) {
            p.sendMessage(CommandMessages.error(ChatColor.RED + "You have staff chat hidden. Use /" + label + " hide off to talk again."));
            return true;
        }

        if (args.length == 0) {
            p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <message>"));
            return true;
        }

        // Build message text
        String msg = String.join(" ", args);

        // %name% should be the raw Minecraft username
        String name = p.getName();

        // Pull pretty role name + color directly from LoginService
        String roleTag = null;  // becomes %role%
        String roleColor = "";  // becomes %rolecolor%

        LoginService.Rank rank = plugin.getLoginService().getRank(p);
        if (rank != null) {
            // These fields should exist per your LoginService.java
            // e.g., rank.displayName = "Senior Admin", rank.color = "&c"
            roleTag = rank.displayName;
            if (rank.color != null) roleColor = rank.color;
        }

        // Broadcast to staff chat
        plugin.broadcastStaffChat(
                name,
                msg,
                roleTag,
                roleColor,
                TheSandboxCore.Source.MINECRAFT
        );
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof ConsoleCommandSender) return out;

        if (args.length == 1) {
            String a = args[0].toLowerCase(Locale.ENGLISH);
            if ("hide".startsWith(a)) out.add("hide");
        } else if (args.length == 2 && "hide".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ENGLISH);
            if ("on".startsWith(p)) out.add("on");
            if ("off".startsWith(p)) out.add("off");
        }
        return out;
    }
}