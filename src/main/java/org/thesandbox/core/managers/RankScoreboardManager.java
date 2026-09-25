package org.thesandbox.core.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.thesandbox.core.login.LoginService;

public class RankScoreboardManager {

    private final LoginService loginService;

    public RankScoreboardManager(LoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * Creates the rank teams used for overhead player prefixes.
     */
    public void setupRankScoreboardTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        ensureRankTeam(board, "00_operator", "&4&lOP &8• ", ChatColor.DARK_RED);
        ensureRankTeam(board, "10_admin", "&c&lADMIN &8• ", ChatColor.RED);
        ensureRankTeam(board, "20_staff", "&6&lSTAFF &8• ", ChatColor.GOLD);
        ensureRankTeam(board, "30_mb", "&3&lMB &8• ", ChatColor.DARK_AQUA);
        ensureRankTeam(board, "40_vip", "&5&lVIP &8• ", ChatColor.DARK_PURPLE);
        ensureRankTeam(board, "99_default", "", ChatColor.GRAY);
    }

    private Team ensureRankTeam(Scoreboard board, String name, String prefix, ChatColor nameColor) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }

        team.setPrefix(ChatColor.translateAlternateColorCodes('&', prefix));

        try {
            team.setColor(nameColor);
        } catch (NoSuchMethodError ignored) {
            // Older APIs do not have Team#setColor; the prefix will still display.
        }

        return team;
    }

    /**
     * Re-applies rank scoreboard teams for every online player.
     */
    public void refreshAllRankScoreboardTeams() {
        setupRankScoreboardTeams();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRankScoreboardTeam(player);
        }
    }

    /**
     * Applies the overhead rank prefix team for one player based on their current permissions.
     */
    public void applyRankScoreboardTeam(Player player) {
        if (player == null) return;
        applyRankScoreboardTeam(player, loginService.getRank(player));
    }

    /**
     * Applies the overhead rank prefix team for one player using a known rank.
     */
    public void applyRankScoreboardTeam(Player player, LoginService.Rank rank) {
        if (player == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        setupRankScoreboardTeams();

        String entry = player.getName();
        String[] rankTeamNames = {
                "00_operator", "10_admin", "20_staff", "30_mb", "40_vip", "99_default"
        };
        for (String teamName : rankTeamNames) {
            Team team = board.getTeam(teamName);
            if (team != null && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }

        Team target = board.getTeam(rankScoreboardTeamName(rank));
        if (target != null) {
            target.addEntry(entry);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private String rankScoreboardTeamName(LoginService.Rank rank) {
        if (rank == null) return "99_default";
        return switch (rank) {
            case OPERATOR -> "00_operator";
            case ADMIN -> "10_admin";
            case STAFF -> "20_staff";
            case MB -> "30_mb";
            case VIP -> "40_vip";
            default -> "99_default";
        };
    }
}