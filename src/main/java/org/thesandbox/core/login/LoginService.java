package org.thesandbox.core.login;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public class LoginService {

    // ===== Ranks =====
    // One enum constant per LuckPerms group: owner, admin, moderator, helper, developer, default
    public enum Rank {
        OWNER("Owner", "&4", "sandbox.owner"),
        ADMIN("Admin", "&c", "sandbox.admin"),
        MODERATOR("Moderator", "&6", "sandbox.moderator"),
        HELPER("Helper", "&b", "sandbox.helper"),
        DEVELOPER("Developer", "&5", "sandbox.developer"),
        DEFAULT("Default", "&7", "sandbox.default");

        /** Pretty display name (e.g., "Moderator"). */
        public final String display;      // kept for backward compat
        public final String displayName;  // primary field used elsewhere
        /** Legacy color using '&' codes (e.g., "&c"). */
        public final String color;
        /** LuckPerms auto-granted group permission, e.g. "group.moderator". */
        public final String permission;

        Rank(String display, String color, String permission) {
            this.display = display;
            this.displayName = display;
            this.color = color;
            this.permission = permission;
        }
    }

    /** Highest → lowest, used for rank resolution priority. */
    private static final Rank[] PRIORITY_ORDER = {
            Rank.OWNER, Rank.ADMIN, Rank.MODERATOR, Rank.HELPER, Rank.DEVELOPER, Rank.DEFAULT
    };

    private final JavaPlugin plugin;

    public LoginService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Kept for the existing plugin lifecycle. No database setup is needed anymore. */
    public void init() {
        // Custom login messages were removed. Rank lookup is permission-based only.
    }

    /** Kept for the existing plugin lifecycle. */
    public void shutdown() {
        // No resources to close.
    }

    // ===== Rank resolution & convenience =====

    /** Highest → lowest permissions check, based on LuckPerms group.<name> permissions. */
    public Rank getRank(Player p) {
        if (p == null) return Rank.DEFAULT;
        for (Rank rank : PRIORITY_ORDER) {
            if (rank == Rank.DEFAULT) continue; // fallback, not a permission check
            if (p.hasPermission(rank.permission)) return rank;
        }
        return Rank.DEFAULT;
    }

    public Rank getRank(OfflinePlayer p) {
        if (p instanceof Player) return getRank((Player) p);
        return Rank.DEFAULT;
    }

    public Rank getRank(UUID uuid) {
        if (uuid == null) return Rank.DEFAULT;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
        return getRank(op);
    }

    public String getDisplayNameFor(Player p) { return getRank(p).displayName; }
    public String getColorFor(Player p)       { return getRank(p).color; }

    /** Restored helpers used elsewhere in your codebase. */
    public boolean isDefault(Player p)          { return getRank(p) == Rank.DEFAULT; }
    public boolean isDefault(OfflinePlayer p)   { return getRank(p) == Rank.DEFAULT; }

    /** Already stored pretty-case; return as-is for display. */
    public static String displayRankTitleCase(Rank rank) {
        return (rank == null ? Rank.DEFAULT : rank).displayName;
    }

    /** Very simple heuristic for chat usage. */
    public static String articleFor(String nextWord) {
        if (nextWord == null || nextWord.isEmpty()) return "a";
        char c = Character.toLowerCase(nextWord.trim().charAt(0));
        return (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') ? "an" : "a";
    }

    /** Title-case + underscores→spaces; kept for legacy callers. */
    public static String toTitleCaseWords(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] parts = s.replace('_', ' ').toLowerCase(Locale.ENGLISH).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** Colorize legacy '&' codes to section sign. */
    public static String colorize(String s) {
        return s == null ? null : ChatColor.translateAlternateColorCodes('&', s);
    }
}