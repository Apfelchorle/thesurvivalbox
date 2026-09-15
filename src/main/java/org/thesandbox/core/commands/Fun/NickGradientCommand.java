package org.thesandbox.core.commands.Fun;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.md_5.bungee.api.ChatColor; // ChatColor.of(Color) -> §x... hex format
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NickGradientCommand implements ISubCommand
{
    private final JavaPlugin plugin;
    private final Essentials essentials;
    private final Random rng = new Random();

    public NickGradientCommand(JavaPlugin plugin)
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
            sender.sendMessage(CommandMessages.error(org.bukkit.ChatColor.RED + "This command can only be used in-game."));
            return true;
        }

        if (essentials == null || !essentials.isEnabled())
        {
            sender.sendMessage(CommandMessages.error(org.bukkit.ChatColor.RED + "Essentials is not enabled on this server."));
            return true;
        }

        if (args.length != 3)
        {
            sender.sendMessage(CommandMessages.usage(org.bukkit.ChatColor.RED + "Usage: /" + label + " <hex|random> <hex|random> <nick>"));
            return true;
        }

        final String nickRaw = args[2].trim();

        // Length check
        if (nickRaw.length() < 3 || nickRaw.length() > 30)
        {
            sender.sendMessage(CommandMessages.error(org.bukkit.ChatColor.RED + "Your nickname must be between 3 and 30 characters long."));
            return true;
        }

        // Uniqueness check (against usernames and current display names)
        for (Player p : Bukkit.getOnlinePlayers())
        {
            if (p == player) continue;
            String strippedDisplay = org.bukkit.ChatColor.stripColor(p.getDisplayName());
            if (p.getName().equalsIgnoreCase(nickRaw) || (strippedDisplay != null && strippedDisplay.trim().equalsIgnoreCase(nickRaw)))
            {
                sender.sendMessage(CommandMessages.error(org.bukkit.ChatColor.RED + "That nickname is already in use."));
                return true;
            }
        }

        // Parse colors (support #RRGGBB or "random"/"r")
        java.awt.Color awt1;
        java.awt.Color awt2;
        boolean showFrom = false, showTo = false;

        try {
            awt1 = parseOrRandom(args[0]);
            awt2 = parseOrRandom(args[1]);
            showFrom = isRandomToken(args[0]);
            showTo   = isRandomToken(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(CommandMessages.error(org.bukkit.ChatColor.RED + "Invalid hex values. Use #RRGGBB or 'random'."));
            return true;
        }

        // Build gradient across the nickname length
        Color c1 = fromAWT(awt1);
        Color c2 = fromAWT(awt2);
        List<Color> gradient = createColorGradient(c1, c2, nickRaw.length());

        String[] chars = nickRaw.split("");
        for (int i = 0; i < chars.length; i++)
        {
            java.awt.Color step = toAWT(gradient.get(i));
            chars[i] = ChatColor.of(step) + chars[i];
        }
        String outputNick = StringUtils.join(chars, "");

        // Save via Essentials
        User user = essentials.getUser(player);
        if (user == null)
        {
            sender.sendMessage(CommandMessages.error(org.bukkit.ChatColor.RED + "Could not access your Essentials data."));
            return true;
        }
        user.setNickname(outputNick);

        // Build feedback line
        StringBuilder suffix = new StringBuilder();
        if (showFrom) suffix.append(" (From: ").append(toHex(awt1)).append(")");
        if (showTo)   suffix.append(" (To: ").append(toHex(awt2)).append(")");

        sender.sendMessage(CommandMessages.command(org.bukkit.ChatColor.YELLOW + "Your nickname is now: '" + outputNick + org.bukkit.ChatColor.YELLOW + "'" + ChatColor.GRAY + suffix));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> out = new ArrayList<>();
        if (args.length == 1 || args.length == 2)
        {
            String pref = args[args.length - 1].toLowerCase();
            for (String s : new String[]{"random", "r", "#ff0000", "#00ff00", "#0000ff", "#ffffff", "#000000"})
            {
                if (s.startsWith(pref)) out.add(s);
            }
        }
        return out;
    }

    /* ---------------- helpers ---------------- */

    private boolean isRandomToken(String s)
    {
        return s.equalsIgnoreCase("random") || s.equalsIgnoreCase("r");
    }

    private java.awt.Color parseOrRandom(String token)
    {
        if (isRandomToken(token)) return new java.awt.Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
        // Allow #RRGGBB or RRGGBB
        String t = token.startsWith("#") ? token : "#" + token;
        return java.awt.Color.decode(t);
    }

    private static String toHex(java.awt.Color c)
    {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color fromAWT(java.awt.Color c)
    {
        return Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static java.awt.Color toAWT(Color c)
    {
        return new java.awt.Color(c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Create an inclusive gradient of 'steps' colors from c1 -> c2 (steps >= 1).
     */
    private static List<Color> createColorGradient(Color c1, Color c2, int steps)
    {
        List<Color> list = new ArrayList<>(steps);
        if (steps <= 1)
        {
            list.add(c1);
            return list;
        }

        double r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();
        double r2 = c2.getRed(), g2 = c2.getGreen(), b2 = c2.getBlue();

        for (int i = 0; i < steps; i++)
        {
            double t = (double) i / (double) (steps - 1);
            int r = (int) Math.round(r1 + (r2 - r1) * t);
            int g = (int) Math.round(g1 + (g2 - g1) * t);
            int b = (int) Math.round(b1 + (b2 - b1) * t);
            list.add(Color.fromRGB(clamp(r), clamp(g), clamp(b)));
        }
        return list;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}