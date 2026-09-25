package org.thesandbox.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public interface ISubCommand {
    boolean execute(CommandSender sender, Command command, String label, String[] args);
    List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args);

    default boolean allowed() {
        return true;
    }
}