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

public class NickRainbowCommand implements ISubCommand
{
    private final JavaPlugin plugin;
    private final Essentials essentials;

    // Legacy rainbow cycle (bright & clear order)
    private static final ChatColor[] RAINBOW = new ChatColor[]{
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE
    };

    private static final int MIN_VISIBLE = 3;
    private static final int MAX_VISIBLE = 30;

    public NickRainbowCommand(JavaPlugin plugin)
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
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "This command can only be used in-game."));
            return true;
        }
        if (essentials == null || !essentials.isEnabled())
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Essentials is not enabled on this server."));
            return true;
        }

        User user = essentials.getUser(player);
        if (user == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Could not access your Essentials data."));
            return true;
        }

        // Determine base text:
        // - If arg provided, use it.
        // - Else if player has a nickname, rainbow that nickname's VISIBLE text.
        // - Else use the player's username (and set one).
        final String baseInput;
        if (args.length >= 1)
        {
            baseInput = args[0].trim();
        }
        else
        {
            String currentNick = user.getNickname();
            if (currentNick != null && !ChatColor.stripColor(currentNick).isEmpty())
            {
                baseInput = ChatColor.stripColor(currentNick).trim();
            }
            else
            {
                baseInput = player.getName(); // no nick -> use username
            }
        }

        // Allow &-codes in input (only matters when user supplied an arg).
        String coloredAttempt = ChatColor.translateAlternateColorCodes('&', baseInput);
        String visible = ChatColor.stripColor(coloredAttempt);
        if (visible == null) visible = "";
        visible = visible.trim();

        // Validate allowed characters and visible length
        if (!visible.matches("^[a-zA-Z0-9_]+$"))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "That nickname contains invalid characters. Allowed: A-Z, a-z, 0-9, _"));
            return true;
        }
        int vlen = visible.length();
        if (vlen < MIN_VISIBLE || vlen > MAX_VISIBLE)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Your nickname must be between " + MIN_VISIBLE + " and " + MAX_VISIBLE + " visible characters."));
            return true;
        }

        // Uniqueness check against usernames, display names (stripped), and Essentials nicks
        for (Player p : Bukkit.getOnlinePlayers())
        {
            if (p == player) continue;
            String otherDisplay = ChatColor.stripColor(p.getDisplayName());
            String otherNick = null;
            try {
                User otherUser = essentials.getUser(p);
                if (otherUser != null) otherNick = otherUser.getNickname();
            } catch (Throwable ignored) {}
            if (p.getName().equalsIgnoreCase(visible)
                    || (otherDisplay != null && otherDisplay.trim().equalsIgnoreCase(visible))
                    || (otherNick != null && ChatColor.stripColor(otherNick).trim().equalsIgnoreCase(visible)))
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "That nickname is already in use."));
                return true;
            }
        }

        // Rainbowify each codepoint of the VISIBLE text
        String rainbowNick = rainbowifyVisible(visible);

        // Reset at end so further chat text isn't colored by accident
        String finalNick = rainbowNick + ChatColor.WHITE;

        user.setNickname(finalNick);
        sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Your nickname is now: " + finalNick));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // No static suggestions
        return Collections.emptyList();
    }

    // ===== Helpers =====

    /** Apply classic rainbow colors to each codepoint of the provided VISIBLE (no colors) text. */
    private static String rainbowifyVisible(String text)
    {
        StringBuilder out = new StringBuilder();
        int[] cps = text.codePoints().toArray();
        int colorIndex = 0;
        for (int cp : cps)
        {
            out.append(RAINBOW[colorIndex % RAINBOW.length])
               .append(new String(Character.toChars(cp)));
            colorIndex++;
        }
        return out.toString();
    }
}