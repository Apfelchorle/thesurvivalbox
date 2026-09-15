package org.thesandbox.core.commands.Fun;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;

public class ColorMeCommand implements ISubCommand, TabCompleter {

    private final JavaPlugin plugin;
    private final Map<String, ChatColor> colors = new LinkedHashMap<>();

    public ColorMeCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        // Allow only plain colors (no magic/underline/italic/strike/reset)
        for (ChatColor c : ChatColor.values()) {
            if (c.isFormat()) continue;        // bold/italic/underline/strikethrough/magic
            if (c == ChatColor.RESET) continue;
            colors.put(c.name().toLowerCase(Locale.ROOT), c);
        }
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lError &8» " + "Only players can use this command."));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lUsage &8» " + "/" + label + " <color|list>"));
            return true;
        }

        final Plugin p = Bukkit.getPluginManager().getPlugin("Essentials");
        if (!(p instanceof Essentials ess)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lError &8» " + "Essentials is required. Contact a server administrator."));
            return true;
        }

        String arg = args[0].toLowerCase(Locale.ROOT);
        if (arg.equals("list")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&7&lCommand &8» " + "&7Available colors: &e" + String.join(", ", colors.keySet())));
            return true;
        }

        ChatColor chosen = resolveColor(arg);
        if (chosen == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lError &8» " + "&cInvalid color: " + arg + ". Use /" + label + " list"));
            return true;
        }

        // Work with Essentials nickname ONLY (no rank/prefix from chat format)
        User user = ess.getUser(player);
        if (user == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lError &8» " + "Could not access Essentials playerdata. Contact a server administrator."));
            return true;
        }

        String baseNick = user.getNickname();              // Essentials nick (may be null)
        if (baseNick == null || baseNick.isBlank()) {
            baseNick = player.getName();                   // fallback to username
        }

        // Strip any colors from the nick itself, don’t touch rank/prefix (since we’re not using displayName)
        baseNick = ChatColor.stripColor(baseNick).trim();
        if (baseNick.isBlank()) baseNick = player.getName();

        String newNick = chosen + baseNick + ChatColor.WHITE; // apply color only

        user.setNickname(newNick); // Essentials API
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        "&7&lCommand &8» &7Your nickname is now: " + newNick));
        return true;
    }

    private ChatColor resolveColor(String input) {
        for (String k : colors.keySet()) if (k.startsWith(input)) return colors.get(k);
        for (String k : colors.keySet()) if (k.contains(input))  return colors.get(k);
        return null;
    }

    // ISubCommand tab completion
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("list".startsWith(pref)) out.add("list");
            for (String key : colors.keySet()) if (key.startsWith(pref)) out.add(key);
            return out;
        }
        return Collections.emptyList();
    }

    // Bukkit TabCompleter bridge
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> r = tabComplete(sender, command, alias, args);
        return (r != null) ? r : Collections.emptyList();
    }
}