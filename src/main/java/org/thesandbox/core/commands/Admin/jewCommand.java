package org.thesandbox.core.commands.Admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.List;

public class jewCommand implements ISubCommand {
    /// @param sender  player who sent command
    /// @param command command
    /// @param label   command label
    /// @param args    command arguments
    /// @return Boolean
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {

        boolean allowed = sender.hasPermission("sandbox.staff");
        if (!allowed) {
            sender.sendMessage(CommandMessages.error("Action Not Allowed"));
            return false;
        }

        Component msg = Component.text("WIPING ALL THE JEWS!", NamedTextColor.GOLD);
        Bukkit.broadcast(msg);

        // this is done because gay "" doesn't work with mixed stuff
        String cmdpart1 = "@e[type=item,nbt={Item:{id:";
        String cmdpart2 = "minecraft:gold_ingot";
        String cmdpart3 = "}}]";
        String fullcmd = cmdpart1 + "\"" + cmdpart2 + "\"" + cmdpart3;

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:kill " + fullcmd);

        return false;
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
