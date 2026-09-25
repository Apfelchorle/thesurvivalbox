package org.thesandbox.core.commands.misc;

import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.fun.Utils;

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

        String all = Registry.ATTRIBUTE.stream()
                .map(a -> a.getKey().getKey().toLowerCase(Locale.ROOT))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));

        String msg = "&7&lCommand &8» &7Possible attributes: " + "&a" + all;

        sender.sendMessage(Utils.AdventureAPI(msg));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // No args to complete
        return List.of();
    }
}