package org.thesandbox.core.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.thesandbox.core.DiscordBridge;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.services.ShushService;
import org.thesandbox.core.util.HexColorUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class StaffChatManager {

    private final TheSandboxCore plugin;
    private final Supplier<javax.sql.DataSource> dataSourceSupplier;
    private final Supplier<ShushService> shushServiceSupplier;
    private final Supplier<DiscordBridge> discordSupplier;
    private final Set<UUID> staffChatEnabled = new HashSet<>();
    private final Set<UUID> staffChatHidden = new HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySection = LegacyComponentSerializer.legacySection();

    public StaffChatManager(TheSandboxCore plugin,
                            Supplier<javax.sql.DataSource> dataSourceSupplier,
                            Supplier<ShushService> shushServiceSupplier,
                            Supplier<DiscordBridge> discordSupplier) {
        this.plugin = plugin;
        this.dataSourceSupplier = dataSourceSupplier;
        this.shushServiceSupplier = shushServiceSupplier;
        this.discordSupplier = discordSupplier;
    }

    public boolean isStaffChatEnabled(UUID id) {
        return staffChatEnabled.contains(id);
    }

    public void setStaffChat(Player player, boolean enabled) {
        if (enabled) staffChatEnabled.add(player.getUniqueId());
        else staffChatEnabled.remove(player.getUniqueId());
        saveStaffChatState(player.getUniqueId(), enabled, staffChatHidden.contains(player.getUniqueId()));
    }

    public void setStaffChat(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        if (enabled) staffChatEnabled.add(uuid);
        else staffChatEnabled.remove(uuid);
        saveStaffChatState(uuid, enabled, staffChatHidden.contains(uuid));
    }

    public boolean isStaffChatHidden(UUID id) {
        return staffChatHidden.contains(id);
    }

    public void setStaffChatHidden(Player player, boolean hidden) {
        if (hidden) staffChatHidden.add(player.getUniqueId());
        else staffChatHidden.remove(player.getUniqueId());
        saveStaffChatState(player.getUniqueId(), staffChatEnabled.contains(player.getUniqueId()), hidden);
    }

    /**
     * Called from the join-data-load flow to populate in-memory state from DB rows.
     */
    public void applyLoadedState(UUID uuid, boolean enabled, boolean hidden) {
        if (enabled) staffChatEnabled.add(uuid);
        else staffChatEnabled.remove(uuid);
        if (hidden) staffChatHidden.add(uuid);
        else staffChatHidden.remove(uuid);
    }

    /**
     * Unified staff-chat formatter/dispatcher with MiniMessage support.
     */
    public void broadcastStaffChat(String name, String message, String roleTag, String roleColor, Source src) {
        String key = (src == Source.DISCORD) ? "staffchat-from-discord-format" : "staffchat-format";
        String fmt = plugin.getConfig().getString(
                key,
                src == Source.DISCORD
                        ? "&8[&9Discord&8] | [&dStaff&8]&r &8[%role%&8] %rolecolor%%name% &8» &f%message%"
                        : "&8[&dStaff&8]&r &8[%role%&8] %rolecolor%%name% &8» &f%message%"
        );

        final String MESSAGE_TOKEN = "%MESSAGE_TOKEN%";

        String filled = fmt
                .replace("%role%", roleTag == null ? "" : roleTag)
                .replace("%rolecolor%", roleColor == null ? "" : roleColor)
                .replace("%name%", name == null ? "" : name)
                .replace("%message%", MESSAGE_TOKEN);

        int idx = filled.indexOf(MESSAGE_TOKEN);
        String beforeStr;
        String afterStr;
        if (idx >= 0) {
            beforeStr = filled.substring(0, idx);
            afterStr = filled.substring(idx + MESSAGE_TOKEN.length());
        } else {
            beforeStr = filled;
            afterStr = "";
        }

        Component prefixComponent = legacySection.deserialize(HexColorUtil.translate(beforeStr));
        Component suffixComponent = legacySection.deserialize(HexColorUtil.translate(afterStr));

        boolean useMini = (src == Source.MINECRAFT) && looksLikeMiniMessage(message);

        String consoleLegacy = HexColorUtil.translate(filled.replace(MESSAGE_TOKEN, message == null ? "" : message));

        ShushService shushService = shushServiceSupplier.get();

        for (Player t : Bukkit.getOnlinePlayers()) {
            if (!t.hasPermission("sandbox.staff")) continue;
            if (staffChatHidden.contains(t.getUniqueId())) continue;
            if (shushService != null && shushService.isEnabled(t.getUniqueId())) continue;

            Component body;
            if (message == null || message.isEmpty()) {
                body = Component.empty();
            } else if (useMini) {
                body = miniMessage.deserialize(message);
            } else {
                body = legacySection.deserialize(HexColorUtil.translate(message));
            }

            Component full = prefixComponent.append(body).append(suffixComponent);
            t.sendMessage(full);
        }

        Bukkit.getConsoleSender().sendMessage(consoleLegacy);
        plugin.getLogger().info(ChatColor.stripColor(consoleLegacy));

        DiscordBridge discord = discordSupplier.get();
        if (src == Source.MINECRAFT && discord != null) {
            discord.sendStaffMessageFromMinecraft(roleTag, name, message);
        }
    }

    private void saveStaffChatState(UUID uuid, boolean speakEnabled, boolean hidden) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            javax.sql.DataSource dataSource = dataSourceSupplier.get();
            if (dataSource == null) return;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO staffchat (uuid, enabled, hidden) VALUES (?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled), hidden=VALUES(hidden)")) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, speakEnabled);
                ps.setBoolean(3, hidden);
                ps.executeUpdate();
            } catch (SQLException e) {
                try (Connection conn2 = dataSource.getConnection();
                     PreparedStatement ps2 = conn2.prepareStatement(
                             "REPLACE INTO staffchat (uuid, enabled) VALUES (?, ?)")) {
                    ps2.setString(1, uuid.toString());
                    ps2.setBoolean(2, speakEnabled);
                    ps2.executeUpdate();
                } catch (SQLException ignore) {
                }
            }
        });
    }

    private boolean looksLikeMiniMessage(String s) {
        if (s == null) return false;
        return s.contains("<") && s.contains(">");
    }

    public enum Source {MINECRAFT, DISCORD}
}