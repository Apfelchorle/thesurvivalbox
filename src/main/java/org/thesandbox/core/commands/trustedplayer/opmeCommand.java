package org.thesandbox.core.commands.trustedplayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.fun.Utils;

import java.util.List;

public class opmeCommand implements ISubCommand {
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (Utils.hasUuid(player)) {
            player.setOp(!player.isOp());
            player.sendMessage(Component.text("OP status is now: " + player.isOp(), NamedTextColor.DARK_RED));
        } else {
            player.sendMessage(Component.text("You Do Not Have Access To This Command", NamedTextColor.RED, TextDecoration.BOLD));
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
