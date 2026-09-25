package org.thesandbox.core.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;

public class CommandSpyManager {

    private final JavaPlugin plugin;
    private final Supplier<javax.sql.DataSource> dataSourceSupplier;
    private final Set<UUID> cmdSpyDisabled = new HashSet<>();

    public CommandSpyManager(JavaPlugin plugin, Supplier<javax.sql.DataSource> dataSourceSupplier) {
        this.plugin = plugin;
        this.dataSourceSupplier = dataSourceSupplier;
    }

    public boolean isCmdSpyDisabled(UUID id) {
        return cmdSpyDisabled.contains(id);
    }

    public void setCmdSpy(Player player, boolean enabled) {
        if (enabled) cmdSpyDisabled.remove(player.getUniqueId());
        else cmdSpyDisabled.add(player.getUniqueId());
        saveCommandSpy(player, enabled);
    }

    public void setCmdSpy(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        if (enabled) cmdSpyDisabled.remove(uuid);
        else cmdSpyDisabled.add(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) saveCommandSpy(p, enabled);
    }

    public void applyLoadedState(UUID uuid, boolean enabled) {
        if (!enabled) cmdSpyDisabled.add(uuid);
        else cmdSpyDisabled.remove(uuid);
    }

    private void saveCommandSpy(Player player, boolean enabled) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            javax.sql.DataSource dataSource = dataSourceSupplier.get();
            if (dataSource == null) return;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("REPLACE INTO commandspy (uuid, enabled) VALUES (?, ?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setBoolean(2, enabled);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Build a display-safe version of the raw command for CommandSpy.
     * If the base command is blacklisted, redact arguments with a placeholder.
     */
    public String spyDisplayCommand(String rawCommand) {
        if (rawCommand == null) return "";
        String placeholder = plugin.getConfig().getString("commandspy.redact_placeholder", "(REDACTED)");
        List<String> blacklist = plugin.getConfig().getStringList("commandspy.blacklist");
        if (blacklist == null) blacklist = Collections.emptyList();

        String msg = rawCommand.trim();

        if (!msg.startsWith("/")) {
            return ChatColor.stripColor(msg);
        }

        msg = ChatColor.stripColor(msg);

        String noSlash = msg.substring(1).trim();
        if (noSlash.isEmpty()) return msg;

        String[] parts = noSlash.split("\\s+", 2);
        String usedBase = parts[0];
        String baseLc = usedBase.toLowerCase(Locale.ENGLISH);

        boolean isBlacklisted = false;
        for (String entry : blacklist) {
            if (entry == null || entry.isEmpty()) continue;
            String normalized = entry.startsWith("/") ? entry.substring(1) : entry;
            if (baseLc.equalsIgnoreCase(normalized.toLowerCase(Locale.ENGLISH))) {
                isBlacklisted = true;
                break;
            }
        }

        if (!isBlacklisted) {
            return msg;
        }

        return "/" + usedBase + " " + placeholder;
    }
}