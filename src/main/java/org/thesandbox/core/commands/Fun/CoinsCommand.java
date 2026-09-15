package org.thesandbox.core.commands.Fun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.util.PlayerDataListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class CoinsCommand implements ISubCommand {

    private final PlayerDataListener playerDataListener;

    public CoinsCommand(PlayerDataListener playerDataListener) {
        this.playerDataListener = playerDataListener;
    }

    private Player resolveTarget(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("@s")) {
            return (sender instanceof Player) ? (Player) sender : null;
        }

        if (input.equalsIgnoreCase("@p")) {
            if (!(sender instanceof Player senderPlayer)) return null; // console has no location to measure "nearest" from
            return senderPlayer.getWorld().getPlayers().stream()
                    .filter(p -> !p.equals(senderPlayer))
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(senderPlayer.getLocation())))
                    .orElse(null);
        }
        return Bukkit.getPlayer(input);
    }


    // working on this doesnt work yet
    private boolean CheckPlayer(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("sandbox.staff"))) {
            sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
            return true;
        }

        String target = args[1];
        Player targetPlayer = resolveTarget(sender, target);
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("[Error]: " + args[2] + " is not a number.", NamedTextColor.RED));
            return true;
        }

        if (targetPlayer == null) {
            sender.sendMessage(Component.text(target + " is offline!", NamedTextColor.DARK_GRAY));
            return true;
        }
        return true;
    }


    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {


        // CONSOLE CAN RUN THESE

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {

            if (!(sender.hasPermission("sandbox.staff"))) {
                sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
                return true;
            }

            String target = args[1];
            Player targetPlayer = resolveTarget(sender, target);
            int amount;

            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("[Error]: " + args[2] + " is not a number.", NamedTextColor.RED));
                return true;
            }

            if (targetPlayer == null) {
                sender.sendMessage(Component.text(target + " is offline!", NamedTextColor.DARK_GRAY));
                return true;
            }

            playerDataListener.setCoins(targetPlayer.getUniqueId(), amount);
            sender.sendMessage(Component.text( sender.getName() + " Set Coins To " + amount + " For " + targetPlayer.getName(), NamedTextColor.DARK_GREEN));

            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("take")) {
            if (!(sender.hasPermission("sandbox.staff"))) {
                sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
                return true;
            }
            String target = args[1];
            Player targetPlayer = resolveTarget(sender, target);
            Player player = Bukkit.getPlayer(target);

            UUID targetUUID = targetPlayer.getUniqueId();
            UUID playerUUID = player.getUniqueId();

            int targetBalance = playerDataListener.getCoins(targetUUID);
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("[Error]: " + args[2] + " is not a number.", NamedTextColor.RED));
                return true;
            }
            if (!targetPlayer.isOnline()) {
                sender.sendMessage(Component.text(target + " is offline!", NamedTextColor.DARK_GRAY));
                return true;
            }

            Bukkit.broadcast(Component.text(sender.getName() + " Has Taken " + amount + " Coins From " + targetPlayer.getName(), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("You've Taken " + amount + " Coins From " + targetPlayer.getName(), NamedTextColor.DARK_GREEN));
            targetPlayer.sendMessage(Component.text("You've Been Beanzz'd by hehe", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD, TextDecoration.ITALIC));
            playerDataListener.addCoins(playerUUID, amount);
            playerDataListener.removeCoins(targetUUID, amount);


        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            // permission check
            if (!(sender.hasPermission("sandbox.staff"))) {
                sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
                return true;
            }
            String target = args[1];
            Player targetPlayer = resolveTarget(sender, target);
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("[Error]: " + args[2] + " is not a number.", NamedTextColor.RED));
                return true;
            }

            if (targetPlayer == null) {
                sender.sendMessage(Component.text(target + " is offline!", NamedTextColor.DARK_GRAY));
                return true;
            }

            playerDataListener.addCoins(targetPlayer.getUniqueId(), amount);

            if (sender instanceof Player) {
                Bukkit.broadcast(Component.text(sender.getName() + " has given " + targetPlayer.getName() + " " + amount + " coins!", NamedTextColor.GOLD));
            }

            sender.sendMessage(Component.text(targetPlayer.getName() + " has " + playerDataListener.getCoins(targetPlayer.getUniqueId()) + " Coins!", NamedTextColor.GREEN));
            targetPlayer.sendMessage(Component.text("You've Recieved " + amount + " Coins From " + sender.getName(), NamedTextColor.GREEN));

            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {

            String target = args[1];
            Player targetPlayer = resolveTarget(sender, target);

            if (targetPlayer == null || (!(sender instanceof Player))) {
                sender.sendMessage(Component.text(target + " is offline!", NamedTextColor.DARK_GRAY));
                return true;
            }

            int pcoins =  playerDataListener.getCoins(targetPlayer.getUniqueId());

            sender.sendMessage(Component.text( targetPlayer.getName() + " has " + pcoins + " Coins!", NamedTextColor.YELLOW));
            return true;
        }


        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can execute this command!", NamedTextColor.RED));
            return true;
        }

        // CONSOLE CANT RUN THESE

        int coins = playerDataListener.getCoins(((Player) sender).getUniqueId());

        if (args.length < 2) {
            // Shows Current Coins
            sender.sendMessage(Component.text("You Currently Have: " + coins + " Coins!", NamedTextColor.YELLOW));
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String target = args[1];
            Player targetPlayer = resolveTarget(sender, target);
            int givenCoins;

            try {
                givenCoins = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("[Error]: " + args[2] + " is not a number.", NamedTextColor.RED));
                return true;
            }

            if (targetPlayer == null) {
                sender.sendMessage(Component.text(target + " is offline!", NamedTextColor.DARK_GRAY));
                return true;
            }

            int senderBalance = playerDataListener.getCoins(((Player) sender).getUniqueId());
            if (senderBalance < givenCoins) {
                sender.sendMessage(Component.text("You don't have enough coins!", NamedTextColor.RED));
                return true;
            } else if (givenCoins <= 0) {
            sender.sendMessage(Component.text("Dude.", NamedTextColor.DARK_RED, TextDecoration.ITALIC, TextDecoration.BOLD));
            return true;
        }

            // data
            playerDataListener.removeCoins(((Player) sender).getUniqueId(), givenCoins);
            playerDataListener.addCoins(targetPlayer.getUniqueId(), givenCoins);


            // notifs
            Bukkit.broadcast(Component.text(sender.getName() + " has given " + targetPlayer.getName() + " " + givenCoins + " coins!", NamedTextColor.GOLD));
            sender.sendMessage(Component.text(targetPlayer.getName() + " has " + playerDataListener.getCoins(targetPlayer.getUniqueId()) + " Coins!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Current Balance: " + playerDataListener.getCoins(((Player) sender).getUniqueId()) + " Coins!", NamedTextColor.YELLOW));
            targetPlayer.sendMessage(Component.text("You've Recieved " + givenCoins + " Coins From " + sender.getName(), NamedTextColor.GREEN));

            return true;
        }
        // beanzz hehe was hehe
        // -usfl

        // 0 = give
        // 1 = player target
        // 2 = given coins
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> list = new ArrayList<>();
            list.add("get");
            list.add("set");
            list.add("give");
            list.add("add");
            list.add("take");
            list.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return list;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("get")|| args[0].equalsIgnoreCase("take"))) {
            String prefix = args[1].toLowerCase();
            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            suggestions.add("@p");
            suggestions.add("@s");
            suggestions.removeIf(name -> !name.toLowerCase().startsWith(prefix));
            return suggestions;
        }

        return List.of();
    }
}