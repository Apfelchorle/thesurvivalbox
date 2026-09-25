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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class NickCleanCommand implements ISubCommand
{
    private final JavaPlugin plugin;
    private final Essentials essentials;

    // Blocked chat codes: &0 (black), &k (obfuscated), &m (strikethrough), &n (underline)
    private static final Pattern BLOCKED = Pattern.compile("(?i)[§&][0kmn]");

    public NickCleanCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
        Essentials ess = null;
        try {
            ess = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
        } catch (Throwable ignored) {}
        this.essentials = ess;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (essentials == null || !essentials.isEnabled())
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Essentials is not available; cannot clean nicknames."));
            return true;
        }

        final String who = (sender instanceof Player) ? sender.getName() : "CONSOLE";

        if (args.length >= 1)
        {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null)
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player not found: " + args[0]));
                return true;
            }

            Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Cleaning " + target.getName() + "'s nickname"));
            cleanOne(sender, target);
            return true;
        }

        // No args: clean everyone
        Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Cleaning all nicknames"));
        int changed = 0;
        for (Player p : Bukkit.getOnlinePlayers())
        {
            if (cleanOne(sender, p)) changed++;
        }
        sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Finished cleaning nicknames. " + changed + " change" + (changed == 1 ? "" : "s") + " applied."));
        return true;
    }

    private boolean cleanOne(CommandSender sender, Player player)
    {
        User user = essentials.getUser(player);
        if (user == null) return false;

        String orig = user.getNickname(); // may be null
        if (orig == null || orig.isEmpty() || orig.equalsIgnoreCase(player.getName()))
        {
            return false; // nothing to clean
        }

        String cleaned = BLOCKED.matcher(orig).replaceAll("");

        if (!cleaned.equals(orig))
        {
            if (cleaned.trim().isEmpty())
            {
                user.setNickname(null);
            }
            else
            {
                user.setNickname(cleaned);
            }

            sender.sendMessage(CommandMessages.command(ChatColor.RESET + player.getName() + ": \"" + orig + ChatColor.RESET
                    + "\" -> \"" + cleaned + ChatColor.RESET + "\"."));
            return true;
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
            {
                if (p.getName().toLowerCase().startsWith(prefix))
                {
                    out.add(p.getName());
                }
            }
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }
}