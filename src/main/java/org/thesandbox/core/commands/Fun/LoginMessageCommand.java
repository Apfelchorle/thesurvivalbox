package org.thesandbox.core.commands.Fun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.fun.LoginMessages;
import org.thesandbox.core.util.PlayerDataListener;

import java.util.List;
import java.util.Locale;

public class LoginMessageCommand implements ISubCommand {

    private static final int MAX_LENGTH = 100;

    private final PlayerDataListener playerDataListener;
    private final LoginMessages loginMessages;

    public LoginMessageCommand(PlayerDataListener playerDataListener, LoginMessages loginMessages) {
        this.playerDataListener = playerDataListener;
        this.loginMessages = loginMessages;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can execute this command!", NamedTextColor.RED));
            return true;
        }

        String state = loginMessages.GetLoginMessagesState(player);
        if (state.equalsIgnoreCase("not_owned")) {
            player.sendMessage(Component.text("You don't own the Login Messages item! Buy it from /shop.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            String current = loginMessages.GetLoginMessage(player);
            if (current.isEmpty()) {
                player.sendMessage(Component.text("You haven't set a login message yet. Usage: /loginmessage <message>", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Your current login message: ", NamedTextColor.YELLOW));
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(current));
            }
            return true;
        }

        String raw = String.join(" ", args);

        if (raw.length() > MAX_LENGTH) {
            player.sendMessage(Component.text("Your message is too long! Max " + MAX_LENGTH + " characters.", NamedTextColor.RED));
            return true;
        }

        String sanitized = player.hasPermission("sandbox.staff") ? raw : raw.replaceAll("(?i)&k", "");

        loginMessages.SetLoginMessage(player, sanitized);

        player.sendMessage(Component.text("Login message set to: ", NamedTextColor.GREEN));
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(sanitized));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length == 0) {
            return List.of();
        }

        List<String> options = List.of("%rank%", "%name%");
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);

        return options.stream()
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(current))
                .toList();
    }
}