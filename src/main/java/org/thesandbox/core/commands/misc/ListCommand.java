package org.thesandbox.core.commands.misc;

import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.login.LoginService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ListCommand implements ISubCommand
{
    private final TheSandboxCore core;
    private final LoginService login;

    // Staff vs Player buckets as defined in your LoginService.Rank
    private static final EnumSet<LoginService.Rank> STAFF_RANKS = EnumSet.of(
            LoginService.Rank.OPERATOR,
            LoginService.Rank.ADMIN,
            LoginService.Rank.STAFF
    );
    private static final EnumSet<LoginService.Rank> PLAYER_RANKS = EnumSet.of(
            LoginService.Rank.MB,
            LoginService.Rank.VIP,
            LoginService.Rank.DEFAULT
    );

    public ListCommand(TheSandboxCore core)
    {
        this.core = core;
        this.login = core.getLoginService();
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        final boolean viewerIsStaff = sender.hasPermission("sandbox.staff");

        List<String> staffLines  = new ArrayList<>();
        List<String> playerLines = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers())
        {
            boolean vanished = isVanished(p);

            // Hide vanished players from non-staff viewers
            if (vanished && !viewerIsStaff) {
                continue;
            }

            LoginService.Rank rank = login.getRank(p);
            String entry = formatEntry(p, rank, vanished && viewerIsStaff);

            if (STAFF_RANKS.contains(rank)) {
                staffLines.add(entry);
            } else { // MB / DEFAULT (and any future non-staff rank)
                playerLines.add(entry);
            }
        }

        int totalShown = staffLines.size() + playerLines.size();

        // Header
        send(sender, "&7&m--------------------&r &dOnline (" + totalShown + ") &7&m--------------------");

        // Staff section (Operator, Admin, Staff)
        send(sender, ("&dStaff&r &7- &d" + staffLines.size()));
        if (staffLines.isEmpty()) {
            send(sender, ("&7- &7No staff online."));
        } else {
            send(sender, ("&8- " + String.join(color("&7, "), staffLines)));
        }

        // Players section (Master Builder, VIP, Player)
        send(sender, ("&dPlayers &8- &d" + playerLines.size()));
        if (playerLines.isEmpty()) {
            send(sender, ("&7- &7No players online."));
        } else {
            send(sender, ("&7- " + String.join(color("&7, "), playerLines)));
        }

        // Footer
        send(sender, ("&7&m-----------------------------------------------------"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return java.util.Collections.emptyList(); // /list has no args
    }

    /* ================= helpers ================= */

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static void send(CommandSender s, String msg) {
        s.sendMessage(color(msg));
    }

    private boolean isVanished(Player p)
    {
        try {
            return VanishAPI.isInvisible(p);
        } catch (Throwable t) {
            // PremiumVanish not present or API not available -> treat as not vanished
            return false;
        }
    }

    private String formatEntry(Player p, LoginService.Rank rank, boolean showVanishedTag)
    {
        String vanished = showVanishedTag ? color(" &e(VANISHED)") : "";
        String name = p.getName();

        // Match the same rank prefix style used for LuckPerms/chat and scoreboard teams.
        // Operator is intentionally displayed as OP here.
        return switch (rank == null ? LoginService.Rank.DEFAULT : rank) {
            case OPERATOR -> color("&4&lOP &8• &4" + name) + ChatColor.RESET + vanished;
            case ADMIN    -> color("&c&lADMIN &8• &c" + name) + ChatColor.RESET + vanished;
            case STAFF    -> color("&6&lSTAFF &8• &6" + name) + ChatColor.RESET + vanished;
            case MB       -> color("&3&lMB &8• &3" + name) + ChatColor.RESET + vanished;
            case VIP      -> color("&5&lVIP &8• &5" + name) + ChatColor.RESET + vanished;
            case DEFAULT  -> color("&7" + name) + ChatColor.RESET + vanished;
        };
    }
}