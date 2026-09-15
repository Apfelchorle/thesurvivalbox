package org.thesandbox.core.commands.misc;

import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AttributeListCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public AttributeListCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // List all attribute names (lowercase, sorted for readability)
        String all = Arrays.stream(Attribute.values())
                .map(a -> a.name().toLowerCase(Locale.ROOT))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7Possible attributes: " + ChatColor.GREEN + all));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // No args to complete
        return List.of();
    }
}