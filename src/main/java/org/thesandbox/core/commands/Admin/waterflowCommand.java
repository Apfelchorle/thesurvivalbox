package org.thesandbox.core.commands.Admin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.util.GenericDataKeys;
import org.thesandbox.core.util.PluginConfigManager;

import java.util.List;


public class waterflowCommand implements ISubCommand {

    private final PluginConfigManager configManager;

    public waterflowCommand(PluginConfigManager configManager) {
        this.configManager = configManager;
    }

    /// @param sender  player who sent command
    /// @param command command
    /// @param label   command label
    /// @param args    command arguments
    /// @return Boolean
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        boolean current = configManager.getOrCreate(GenericDataKeys.WATER_FLOW, GenericDataKeys.WATER_FLOW_DEFAULT);
        boolean newValue = !current;
        configManager.forceset(GenericDataKeys.WATER_FLOW, newValue);

        announce(sender, newValue);
        return true;
    }

    private void announce(CommandSender sender, boolean current) {
        Component message = Component.text("waterFlow is now: " + current);
        Bukkit.broadcast(message);
    }

    /// @param sender  player who sent command
    /// @param command command
    /// @param alias   command aliases
    /// @param args    arguments
    /// @return List for autocomplete
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
