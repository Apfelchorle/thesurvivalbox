package org.thesandbox.core.commands.Fun;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class DeNickCommand implements ISubCommand
{
    private final JavaPlugin plugin;
    private final Essentials essentials;

    public DeNickCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;

        Essentials found = null;
        try {
            // Uses the Bukkit/JavaPlugin API instead of string lookups
            found = JavaPlugin.getPlugin(Essentials.class);
        } catch (Throwable ignored) {
        }
        this.essentials = found;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // Ensure Essentials is present & enabled
        if (essentials == null || !essentials.isEnabled()) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Essentials is not installed or not enabled. Cannot remove nicknames."));
            return true;
        }

        // Broadcast first (in red)
        final String who = (sender instanceof Player) ? sender.getName() : "CONSOLE";
        Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Removing all online nicknames"));

        // Clear nicknames for all online players
        for (Player target : Bukkit.getOnlinePlayers()) {
            try {
                User user = essentials.getUser(target);
                if (user != null) {
                    // Clear Essentials nickname
                    user.setNickname(null); // null = remove nick
                    // Make sure the live Player display resets as well
                    target.setDisplayName(target.getName());
                    try {
                        // Tab-list name; null or plain name depending on server version
                        target.setPlayerListName(target.getName());
                    } catch (Throwable ignored) {
                        // Older servers may not expose this setter or have restrictions
                        target.sendMessage(CommandMessages.command(ChatColor.RED + "Your nickname has been cleared by a staff member."));
                    }
                }
            } catch (Throwable t) {
                // Don’t fail the whole command if one player errors
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Failed to clear nickname for: " + target.getName()));
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}
