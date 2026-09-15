package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GamemodeCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public GamemodeCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length < 1)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <gamemode> [player]"));
            return true;
        }

        GameMode mode = parseGamemode(args[0]);
        if (mode == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED +
                    "Unknown gamemode. Use: survival/s, creative/c, adventure/a, spectator/sp"));
            return true;
        }

        // Self-change: /gamemode <mode>
        if (args.length == 1)
        {
            if (!(sender instanceof Player player))
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "CONSOLE must specify a target player."));
                return true;
            }

            player.setGameMode(mode);
            player.sendMessage(CommandMessages.command(ChatColor.GRAY + "Your gamemode has been set to " + mode.name() + "."));
            return true;
        }

        // Targeted change: /gamemode <mode> <player>
        if (!sender.hasPermission("sandbox.staff"))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED +
                    "You do not have permission to change other players' gamemodes."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player not found."));
            return true;
        }

        target.setGameMode(mode);

        String modeName = mode.name();
        String senderName = sender.getName();
        String targetName = target.getName();

        // Notify target
        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId()))
        {
            // This case shouldn't normally happen here (self-change uses args.length == 1),
            // but handle gracefully anyway.
            target.sendMessage(CommandMessages.command(ChatColor.GRAY + "Your gamemode has been set to " + modeName + "."));
        }
        else
        {
            target.sendMessage(CommandMessages.command(ChatColor.GRAY + "Your gamemode has been set to " +
                    modeName + " by " + senderName + "."));
        }

        // Sender feedback
        if (sender instanceof Player && !((Player) sender).getUniqueId().equals(target.getUniqueId()))
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Set " + targetName + " to " + modeName + " mode."));

            // Broadcast only when a *player* changes another user's gamemode
            Bukkit.broadcastMessage(CommandMessages.server(ChatColor.AQUA + senderName +
                    " -  Changing " + targetName + "'s gamemode to " + modeName));
        }
        else
        {
            // Console changing player: no broadcast per your requirement wording
            // but still a message back to console.
            if (!(sender instanceof Player))
            {
                sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Set " + targetName + " to " + modeName + " mode."));
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> modes = new ArrayList<>();
            modes.add("survival");
            modes.add("creative");
            modes.add("adventure");
            modes.add("spectator");
            modes.add("s");
            modes.add("c");
            modes.add("a");
            modes.add("sp");

            modes.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return modes;
        }
        else if (args.length == 2)
        {
            String prefix = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
            {
                String name = p.getName();
                if (name.toLowerCase().startsWith(prefix))
                {
                    names.add(name);
                }
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            return names;
        }

        return Collections.emptyList();
    }

    /* ===================== helpers ===================== */

    private GameMode parseGamemode(String input)
    {
        String in = input.toLowerCase();

        switch (in)
        {
            case "0":
            case "s":
            case "survival":
                return GameMode.SURVIVAL;

            case "1":
            case "c":
            case "creative":
                return GameMode.CREATIVE;

            case "2":
            case "a":
            case "adventure":
                return GameMode.ADVENTURE;

            case "3":
            case "sp":
            case "spectator":
                return GameMode.SPECTATOR;

            default:
                return null;
        }
    }
}
