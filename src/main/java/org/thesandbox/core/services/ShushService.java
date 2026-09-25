package org.thesandbox.core.services;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Persists per-player "Shush mode" and remembers original spy/chat states
 * so they can be restored when Shush is turned off.
 */
public class ShushService {

    private final TheSandboxCore plugin;
    private final DataSource dataSource;
    private final Logger log;

    // Cache of who is currently shushed
    private final Set<UUID> enabled = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Enable shush: capture originals, disable notifications, persist.
     */
    public void enable(Player p) {
        final UUID id = p.getUniqueId();

        // Capture originals
        boolean cmdSpyEnabled = !plugin.getCommandSpyManager().isCmdSpyDisabled(id);
        boolean __potionTmp = false;
        try {
            __potionTmp = (plugin.getPotionSpyService() != null) && plugin.getPotionSpyService().isEnabled(id);
        } catch (Throwable t) {
            // ignore
        }
        final boolean potionSpyEnabled = __potionTmp;
        boolean staffChatHidden = plugin.getStaffChatManager().isStaffChatHidden(id);

        originals.put(id, new Originals(cmdSpyEnabled, potionSpyEnabled, staffChatHidden));

        // Disable all notification channels
        plugin.getCommandSpyManager().setCmdSpy(p, false); // disable CommandSpy
        if (plugin.getPotionSpyService() != null) {
            plugin.getPotionSpyService().setEnabled(id, false);
        }
        plugin.getStaffChatManager().setStaffChatHidden(p, true); // hide staff chat receive (also blocks speaking per plugin logic)

        enabled.add(id);
        enabledMisses.remove(id);

        // Persist
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO shush (uuid, enabled, orig_cmdspy, orig_potionspy, orig_staffchat_hidden) " +
                                 "VALUES (?, TRUE, ?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled), orig_cmdspy=VALUES(orig_cmdspy), orig_potionspy=VALUES(orig_potionspy), orig_staffchat_hidden=VALUES(orig_staffchat_hidden)")) {
                ps.setString(1, id.toString());
                ps.setBoolean(2, cmdSpyEnabled);
                ps.setBoolean(3, potionSpyEnabled);
                ps.setBoolean(4, staffChatHidden);
                ps.executeUpdate();
            } catch (SQLException e) {
                log.warning("[Shush] persist enable failed for " + id + ": " + e.getMessage());
            }
        });
    }
    private final Map<UUID, Originals> originals = new ConcurrentHashMap<>();

    public ShushService(TheSandboxCore plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.log = plugin.getLogger();
        ensureSchema();
        // Warm cache for already-online players (plugin reloads etc.)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (loadEnabled(p.getUniqueId())) enabled.add(p.getUniqueId());
        }
    }

    private void ensureSchema() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS shush (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "enabled BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "orig_cmdspy BOOLEAN, " +
                    "orig_potionspy BOOLEAN, " +
                    "orig_staffchat_hidden BOOLEAN)");
        } catch (SQLException e) {
            log.severe("[Shush] Failed to ensure schema: " + e.getMessage());
        }
    }

    /** Returns true if shush is enabled for this player (cached, loads on miss). */
    public boolean isEnabled(UUID id) {
        if (enabled.contains(id)) return true;
        if (enabledMisses.contains(id)) return false;
        boolean db = loadEnabled(id);
        if (!db) enabledMisses.add(id);
        else enabled.add(id);
        return db;
    }

    // small negative cache to avoid DB spam
    private final Set<UUID> enabledMisses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Loads enabled flag and originals from DB into cache. */
    private boolean loadEnabled(UUID id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT enabled, orig_cmdspy, orig_potionspy, orig_staffchat_hidden FROM shush WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean en = rs.getBoolean(1);
                    Boolean oc = (Boolean)rs.getObject(2);
                    Boolean op = (Boolean)rs.getObject(3);
                    Boolean oh = (Boolean)rs.getObject(4);
                    if (oc != null && op != null && oh != null) {
                        originals.put(id, new Originals(oc, op, oh));
                    }
                    return en;
                }
            }
        } catch (SQLException e) {
            log.warning("[Shush] loadEnabled failed for " + id + ": " + e.getMessage());
        }
        return false;
    }

    /** Disable shush: restore originals (if known), persist. */
    public void disable(Player p) {
        final UUID id = p.getUniqueId();
        Originals o = originals.get(id);
        if (o == null) {
            // Try DB
            loadEnabled(id);
            o = originals.get(id);
        }
        if (o != null) {
            // Restore states
            plugin.getCommandSpyManager().setCmdSpy(p, o.cmdSpyEnabled);
            if (plugin.getPotionSpyService() != null) {
                plugin.getPotionSpyService().setEnabled(id, o.potionSpyEnabled);
            }
            plugin.getStaffChatManager().setStaffChatHidden(p, o.staffChatHidden);
        }

        enabled.remove(id);
        enabledMisses.add(id);

        // Persist
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "UPDATE shush SET enabled=FALSE WHERE uuid=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.warning("[Shush] persist disable failed for " + id + ": " + e.getMessage());
            }
        });
    }

    // In-memory originals for quick restores (also persisted)
    private record Originals(boolean cmdSpyEnabled, boolean potionSpyEnabled, boolean staffChatHidden) {
    }
}