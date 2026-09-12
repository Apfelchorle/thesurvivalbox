package org.thesandbox.core.login;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public class LoginService {

    // ===== Ranks =====
    public enum Rank {
        DEFAULT("Default", "&7"),
        VIP("helper", "&5"),
        MB("helper", "&3"),

        DEVELOPER("developer", "&7"),
        STAFF("admin", "&6"),
        ADMIN("admin", "&c"),
        OPERATOR("owner", "&4");

        /** Pretty display name (e.g., "Master Builder"). */
        public final String display;      // kept for backward compat
        public final String displayName;  // primary field used elsewhere
        /** Legacy color using '&' codes (e.g., "&c"). */
        public final String color;

        Rank(String display, String color) {
            this.display = display;
            this.displayName = display;
            this.color = color;
        }
    }

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

    /** Highest → lowest permissions check. */
    public Rank getRank(Player p) {
        if (p == null) return Rank.DEFAULT;
        if (p.hasPermission("sandbox.operator")) return Rank.OPERATOR;
        if (p.hasPermission("sandbox.admin"))    return Rank.ADMIN;
        if (p.hasPermission("sandbox.staff"))    return Rank.STAFF;
        if (p.hasPermission("sandbox.mb"))       return Rank.MB;
        if (p.hasPermission("sandbox.vip"))      return Rank.VIP;
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
