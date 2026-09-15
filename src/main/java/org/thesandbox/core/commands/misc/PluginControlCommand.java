package org.thesandbox.core.commands.misc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;
import java.util.stream.Collectors;

public class PluginControlCommand implements ISubCommand
{
    private final JavaPlugin plugin;
    private final Set<String> untouchable;

    public PluginControlCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
        // Protect your core plugin from disable/reload
        this.untouchable = new HashSet<>(Collections.singleton(plugin.getName()));
        // If you want to protect more, add them here:
        // this.untouchable.addAll(Arrays.asList("LuckPerms", "Vault", "Essentials"));
    }

    private boolean canUse(CommandSender sender)
    {
        // Adjust the permission to match your setup
        return sender.hasPermission("sandbox.staff") || sender.isOp();
    }

    private void msg(CommandSender sender, String message)
    {
        sender.sendMessage(message);
    }

    private Plugin findPluginCaseInsensitive(PluginManager pm, String name)
    {
        Plugin exact = pm.getPlugin(name);
        if (exact != null) return exact;

        String target = name.toLowerCase(Locale.ROOT);
        for (Plugin p : pm.getPlugins())
        {
            if (p.getName().equalsIgnoreCase(target))
            {
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!canUse(sender))
        {
            msg(sender, CommandMessages.error(ChatColor.RED + "You don't have permission to use this."));
            return true;
        }

        final PluginManager pm = Bukkit.getPluginManager();

        if (args.length == 1 && args[0].equalsIgnoreCase("list"))
        {
            for (Plugin pl : pm.getPlugins())
            {
                PluginDescriptionFile desc = pl.getDescription();
                String version = desc.getVersion() == null ? "" : desc.getVersion().trim();
                List<String> authors = desc.getAuthors() == null ? Collections.emptyList() : desc.getAuthors();

                ChatColor stateColor = pl.isEnabled() ? ChatColor.GREEN : ChatColor.RED;
                String authorsJoined = authors.isEmpty() ? "Unknown" : String.join(", ", authors);

                msg(sender,
                        CommandMessages.command(ChatColor.GRAY + "- " + stateColor + pl.getName()
                                + ChatColor.GOLD + (version.isEmpty() ? "" : " v" + version)
                                + ChatColor.GRAY + " by " + authorsJoined));
            }
            return true;
        }

        if (args.length == 2)
        {
            String action = args[0].toLowerCase(Locale.ROOT);
            Plugin target = findPluginCaseInsensitive(pm, args[1]);

            if (target == null)
            {
                msg(sender, CommandMessages.error(ChatColor.RED + "Plugin not found: " + args[1]));
                return true;
            }

            switch (action)
            {
                case "enable" -> {
                    if (target.isEnabled())
                    {
                        msg(sender, CommandMessages.error(ChatColor.GRAY + target.getName() + " is already enabled."));
                        return true;
                    }
                    pm.enablePlugin(target);
                    if (target.isEnabled())
                    {
                        msg(sender, CommandMessages.command(ChatColor.GREEN + target.getName() + " is now enabled!"));
                    }
                    else
                    {
                        msg(sender, CommandMessages.error(ChatColor.RED + "Failed to enable " + target.getName() + "."));
                    }
                    return true;
                }

                case "disable" -> {
                    if (!target.isEnabled())
                    {
                        msg(sender, CommandMessages.error(target.getName() + " is already disabled!"));
                        return true;
                    }
                    if (untouchable.contains(target.getName()))
                    {
                        msg(sender, CommandMessages.error(ChatColor.RED + target.getName() + " cannot be disabled!"));
                        return true;
                    }
                    pm.disablePlugin(target);
                    msg(sender, CommandMessages.command(ChatColor.YELLOW + target.getName() + " is now disabled!"));
                    return true;
                }

                case "reload" -> {
                    if (untouchable.contains(target.getName()))
                    {
                        msg(sender, CommandMessages.error(ChatColor.RED + target.getName() + " cannot be reloaded!"));
                        return true;
                    }
                    // Note: reloading arbitrary plugins can be risky.
                    pm.disablePlugin(target);
                    pm.enablePlugin(target);
                    msg(sender, CommandMessages.command(ChatColor.GREEN + target.getName() + " has been reloaded!"));
                    return true;
                }
            }
        }

        // Usage message
        msg(sender, CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <enable|disable|reload> <plugin> | list"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (!canUse(sender))
        {
            return Collections.emptyList();
        }

        if (args.length == 1)
        {
            return Arrays.asList("enable", "disable", "reload", "list")
                    .stream()
                    .filter(opt -> opt.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("list"))
        {
            final String partial = args[1].toLowerCase(Locale.ROOT);
            return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                    .map(Plugin::getName)
                    .filter(name -> !untouchable.contains(name))
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}