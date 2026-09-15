package org.thesandbox.core.commands.utility;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.events.DisguiseEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class DisguiseToggleCommand implements ISubCommand, Listener
{
    // Global toggle: disguises enabled by default
    private static boolean disguisesEnabled = true;

    private final JavaPlugin plugin;

    public DisguiseToggleCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {

        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "LibsDisguises is not installed or not enabled."));
            return true;
        }

        disguisesEnabled = !disguisesEnabled;

        String who = (sender instanceof Player) ? sender.getName() : "CONSOLE";
        String verb = disguisesEnabled ? "Enabling" : "Disabling";
        Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - " + verb + " disguises"));

        // If we just disabled disguises, clear them immediately
        if (!disguisesEnabled) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                try {
                    if (DisguiseAPI.isDisguised(online)) {
                        DisguiseAPI.undisguiseToAll(online);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisguise(DisguiseEvent event)
    {
        if (!disguisesEnabled) {
            event.setCancelled(true);

            if (event.getEntity() instanceof Player) {
                event.getEntity().sendMessage(CommandMessages.error(ChatColor.RED + "Disguises are currently disabled."));
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}