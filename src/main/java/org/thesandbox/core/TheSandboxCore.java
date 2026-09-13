package org.thesandbox.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;
import de.myzelyam.api.vanish.VanishAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.thesandbox.core.commands.CommandManager;
import org.thesandbox.core.fun.LoginMessages;
import org.thesandbox.core.fun.items.itemUTILS.Item;
import org.thesandbox.core.fun.items.itemUTILS.ItemKeys;
import org.thesandbox.core.guilds.GuildManager;
import org.thesandbox.core.login.LoginService;
import org.thesandbox.core.tags.TagService;
import org.thesandbox.core.util.*;

import java.io.File;
import java.sql.*;
import java.util.*;

// POTIONSPY: service import


public class TheSandboxCore extends JavaPlugin implements Listener {

    public enum Source { MINECRAFT, DISCORD }

    private DataManager dataManager;
    private PlayerDataListener dataListener;

    private LoginMessages loginMessages;

    private HikariDataSource dataSource; // HIKARI O NAKAMA DESU - "Montagem Hikari"

    private final Set<UUID> cmdSpyDisabled   = new HashSet<>();
    private final Set<UUID> staffChatEnabled = new HashSet<>(); // speak mode
    private final Set<UUID> staffChatHidden  = new HashSet<>(); // hide receiving (and block speaking)

    private DiscordBridge discord;
    private CommandManager commandManager;
    private LoginService loginService;
    private TagService tagService; // field
    private GuildManager guildManager;
    private LiteBansWarningListener liteBansWarningListener;

    // Adventure / MiniMessage for staff chat
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.legacyAmpersand();
    private final LegacyComponentSerializer legacySection = LegacyComponentSerializer.legacySection();

    // Command blocking
    private final java.util.concurrent.ConcurrentHashMap<UUID, Long> cmdBlockedUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> cmdBlockedByName = new java.util.concurrent.ConcurrentHashMap<>(); // case-insensitive keying handled in code
    private volatile boolean cmdBlockAll = false;


    // POTIONSPY: service field
    private PotionSpyService potionSpyService;

    public PotionSpyService getPotionSpyService() { return potionSpyService; }

    // SHUSH: service field
    private ShushService shushService;

    // ---- CommandSpy tier mapping ----
    private enum CmdTier {
        DEFAULT(0), MOD(1), ADMIN(2), SRADMIN(3), SUPERUSER(4);
        final int v;
        CmdTier(int v) { this.v = v; }
    }

    private CmdTier getCmdTier(Player p) {
        if (p == null) return CmdTier.DEFAULT;
        if (p.hasPermission("sandbox.superuser")) return CmdTier.SUPERUSER;
        if (p.hasPermission("sandbox.admin"))   return CmdTier.SRADMIN;
        if (p.hasPermission("sandbox.staff"))     return CmdTier.ADMIN;
        if (p.hasPermission("sandbox.staff"))       return CmdTier.MOD;
        return CmdTier.DEFAULT;
    }

    // Getter so commands (e.g., ReportCommand) can access the Discord bridge
    public DiscordBridge getDiscord() { return this.discord; }

    private List<Item> registeredItems = new ArrayList<>();
    public boolean isDiscordChatBridgeEnabled() {
        return getConfig().getBoolean("discord.chat-bridge.enabled", true);
    }

    public boolean setDiscordChatBridgeEnabled(boolean enabled) {
        boolean current = isDiscordChatBridgeEnabled();
        if (current == enabled) return false;
        getConfig().set("discord.chat-bridge.enabled", enabled);
        saveConfig();
        if (discord != null) {
            discord.applyChatBridgePermissionState();
        }
        return true;
    }
    public List<Item> getRegisteredItems() {
        return this.registeredItems;
    }

    public File getPluginFile() {
        return getFile();
    }
    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();

        PluginConfigManager configManager = new PluginConfigManager(this);

        this.dataManager = new DataManager(this);
        this.dataListener = new PlayerDataListener(dataManager);

        getServer().getPluginManager().registerEvents(dataListener, this);

        this.loginMessages = new LoginMessages(dataListener, this);
        getServer().getPluginManager().registerEvents(this.loginMessages, this);

        ItemKeys itemKeys = new ItemKeys(this);
        this.registeredItems = ItemAutoRegistrar.registerAll(this, itemKeys, dataListener, loginMessages, configManager);

        // Initialize login service (rank lookup)
        loginService = new LoginService(this);
        loginService.init();

        ManageChatService manageChatService = new ManageChatService(this);

        // Guilds are stored in plugins/TheSandboxCore/guilds.yml and their tags are prepended
        // by the same chat display flow used by /tag.
        this.guildManager = new GuildManager(this);

        getServer().getPluginManager().registerEvents(new ChatMentionFormatListener(this), this);
        getServer().getPluginManager().registerEvents(new ManageChatListener(manageChatService), this);

        AutoTpService autoTpService = new AutoTpService(this);
        getServer().getPluginManager().registerEvents(new AutoTpListener(this, autoTpService), this);

        // TagService
        this.tagService = new TagService(this);
        this.tagService.init();
        new org.thesandbox.core.tags.TagCleanupListener(this, tagService, guildManager).register();

        // Events & Discord
        Bukkit.getPluginManager().registerEvents(this, this);
        discord = new DiscordBridge(this, this.dataListener);
        discord.start();

        AutoClearService autoClearService = new AutoClearService();
        getServer().getPluginManager().registerEvents(new AutoClearListener(autoClearService), this);

        // POTIONSPY: init & register listener (uses existing Hikari DataSource)
        this.potionSpyService = new PotionSpyService(this, this.dataSource);
        getServer().getPluginManager().registerEvents(this.potionSpyService, this);

        // SHUSH: init & listener
        this.shushService = new ShushService(this, this.dataSource);
        getServer().getPluginManager().registerEvents(new ShushListener(this), this);

        // LiteBans warning actions: survival, optional inventory clear, lightning, and kill.
        this.liteBansWarningListener = new LiteBansWarningListener(this);
        this.liteBansWarningListener.register();

        // registered items


        // Services List
        List<Object> commandServices = new ArrayList<>(List.of(
                tagService,
                loginService,
                autoClearService,
                autoTpService,
                manageChatService,
                guildManager,
                potionSpyService,
                this.shushService,
                this.discord,
                dataListener,
                itemKeys,
                loginMessages,
                configManager
        ));

        // Command Auto Registrar + ItemAutoRegistrar
        commandServices.addAll(this.registeredItems);
        CommandAutoRegistrar.registerAll(this, commandServices.toArray());

        setupRankScoreboardTeams();
        refreshAllRankScoreboardTeams();

        getLogger().info("TheSandboxCore enabled!");
    }

    @Override
    public void onDisable() {
        // SAVE DATA
        if (dataListener != null) dataListener.saveAll();


        if (liteBansWarningListener != null) liteBansWarningListener.unregister();
        if (discord != null) discord.stop();
        if (guildManager != null) {
            guildManager.save();
            guildManager.shutdown();
        }
        if (loginService != null) loginService.shutdown();
        closeDataSource();
        getLogger().info("TheSandboxCore disabled!");
    }

    /* =================== Helper API ==================== */

    public ShushService getShushService() { return shushService; }

    public GuildManager getGuildManager() { return guildManager; }

    public boolean isCmdSpyDisabled(UUID id) { return cmdSpyDisabled.contains(id); }

    public void setCmdSpy(Player player, boolean enabled) {
        if (enabled) cmdSpyDisabled.remove(player.getUniqueId());
        else cmdSpyDisabled.add(player.getUniqueId());
        saveCommandSpy(player, enabled);
    }

    // ✅ Overload to support UUID callers (fixes your ShushService compile error)
    public void setCmdSpy(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        if (enabled) cmdSpyDisabled.remove(uuid);
        else cmdSpyDisabled.add(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) saveCommandSpy(p, enabled);
    }

    // Speak mode (player’s messages go to staff chat automatically)
    public boolean isStaffChatEnabled(UUID id) { return staffChatEnabled.contains(id); }

    public void setStaffChat(Player player, boolean enabled) {
        if (enabled) staffChatEnabled.add(player.getUniqueId());
        else staffChatEnabled.remove(player.getUniqueId());
        saveStaffChatState(player.getUniqueId(), enabled, staffChatHidden.contains(player.getUniqueId()));
    }

    // ✅ Overload to support UUID callers (fixes your StaffChatCommand compile error)
    public void setStaffChat(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        if (enabled) staffChatEnabled.add(uuid);
        else staffChatEnabled.remove(uuid);
        saveStaffChatState(uuid, enabled, staffChatHidden.contains(uuid));
    }

    // Hidden mode (player does NOT receive staff chat AND cannot speak)
    public boolean isStaffChatHidden(UUID id) { return staffChatHidden.contains(id); }

    public void setStaffChatHidden(Player player, boolean hidden) {
        if (hidden) staffChatHidden.add(player.getUniqueId());
        else staffChatHidden.remove(player.getUniqueId());
        saveStaffChatState(player.getUniqueId(), staffChatEnabled.contains(player.getUniqueId()), hidden);
    }

    /**
     * Unified staff-chat formatter/dispatcher with MiniMessage support.
     * - If src == DISCORD → uses "staffchat-from-discord-format" (&-colors, no MiniMessage).
     * - If src == MINECRAFT → uses "staffchat-format" and message may be MiniMessage.
     * Skips recipients who have hidden enabled. Logs to console (colored + plain).
     * Mirrors to Discord only for MC-origin to avoid loops.
     */
    public void broadcastStaffChat(String name, String message, String roleTag, String roleColor, Source src) {
        String key = (src == Source.DISCORD) ? "staffchat-from-discord-format" : "staffchat-format";
        String fmt = getConfig().getString(
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

        // Split around the message token so we can render prefix/suffix as legacy and body as MiniMessage/legacy.
        int idx = filled.indexOf(MESSAGE_TOKEN);
        String beforeStr;
        String afterStr;
        if (idx >= 0) {
            beforeStr = filled.substring(0, idx);
            afterStr  = filled.substring(idx + MESSAGE_TOKEN.length());
        } else {
            // Fallback if token missing: everything is "before", no "after"
            beforeStr = filled;
            afterStr  = "";
        }

        // Deserialize prefix/suffix using legacy colors, including hex codes like &#7200ff.
        Component prefixComponent = legacySection.deserialize(HexColorUtil.translate(beforeStr));
        Component suffixComponent = legacySection.deserialize(HexColorUtil.translate(afterStr));

        boolean useMini = (src == Source.MINECRAFT) && looksLikeMiniMessage(message);

        // Build a plain legacy string for console / logging
        String consoleLegacy = HexColorUtil.translate(filled.replace(MESSAGE_TOKEN, message == null ? "" : message));

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

        // Console + log (legacy)
        Bukkit.getConsoleSender().sendMessage(consoleLegacy);
        getLogger().info(ChatColor.stripColor(consoleLegacy));

        if (src == Source.MINECRAFT && discord != null) {
            // pass the group tag that came from LoginService-driven logic; DiscordBridge strips MiniMessage tags
            discord.sendStaffMessageFromMinecraft(roleTag, name, message);
        }
    }

    public void reloadAndReconnect() {
        reloadConfig();
        setupDatabase();
        if (discord != null) { discord.stop(); discord.start(); }    }

    /* ===== Command-Block helpers ===== */

    public boolean isCmdBlockAll() { return cmdBlockAll; }
    public void enableCmdBlockAll() { cmdBlockAll = true; }
    public void disableCmdBlockAll() { cmdBlockAll = false; }

    public void purgeAllCmdBlocks() {
        disableCmdBlockAll();
        cmdBlockedUntil.clear();
        cmdBlockedByName.clear();
    }

    public void blockPlayerCommands(UUID uuid, long untilMs) {
        if (uuid != null) {
            cmdBlockedUntil.put(uuid, untilMs);
        }
    }

    public void blockPlayerCommandsByName(String name, long untilMs) {
        if (name != null) {
            cmdBlockedByName.put(name.toLowerCase(java.util.Locale.ENGLISH), untilMs);
        }
    }

    public void unblockPlayerCommands(UUID uuid) {
        if (uuid != null) {
            cmdBlockedUntil.remove(uuid);
        }
    }

    public void unblockPlayerCommandsByName(String name) {
        if (name != null) {
            cmdBlockedByName.remove(name.toLowerCase(java.util.Locale.ENGLISH));
        }
    }

    public boolean isPlayerCmdBlockedByName(String name) {
        if (name == null) return false;
        Long until = cmdBlockedByName.get(name.toLowerCase(java.util.Locale.ENGLISH));
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            cmdBlockedByName.remove(name.toLowerCase(java.util.Locale.ENGLISH));
            return false;
        }
        return true;
    }

    /** True if this player's commands should be blocked right now. */
    public boolean isCommandsBlocked(Player p) {
        // Moderators+ are NEVER blocked (per requirement)
        if (p.hasPermission("sandbox.staff")) return false;

        long now = System.currentTimeMillis();

        // Per-player UUID-based block
        Long until = cmdBlockedUntil.get(p.getUniqueId());
        if (until != null) {
            if (now > until) cmdBlockedUntil.remove(p.getUniqueId());
            else return true;
        }

        // Name-based block (set when they were offline)
        Long byName = cmdBlockedByName.get(p.getName().toLowerCase(java.util.Locale.ENGLISH));
        if (byName != null) {
            if (now > byName) cmdBlockedByName.remove(p.getName().toLowerCase(java.util.Locale.ENGLISH));
            else return true;
        }

        // Global block for everyone except moderator+
        return cmdBlockAll;
    }

    /* =================== DB ==================== */

    private void setupDatabase() {
        closeDataSource();

        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String user = getConfig().getString("mysql.user");
        String password = getConfig().getString("mysql.password");

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(10000);

        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS commandspy (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "enabled BOOLEAN NOT NULL)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS staffchat (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "enabled BOOLEAN NOT NULL)");

            // Ensure hidden column exists
            try {
                stmt.executeUpdate("ALTER TABLE staffchat ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE");
            } catch (SQLException e) {
                try { stmt.executeUpdate("ALTER TABLE staffchat ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE"); }
                catch (SQLException ignore) {}
            }
            // POTIONSPY: table created by repository on first use (ensureSchema), nothing to do here
        } catch (SQLException e) {
            getLogger().severe("Could not initialize database!");
            e.printStackTrace();
        }
    }

    private void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    /* =================== Load/Save ==================== */

    private void loadPlayer(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                // CommandSpy
                try (PreparedStatement ps = conn.prepareStatement("SELECT enabled FROM commandspy WHERE uuid=?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        boolean enabled = rs.getBoolean("enabled");
                        if (!enabled) cmdSpyDisabled.add(player.getUniqueId());
                    } else {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO commandspy (uuid, enabled) VALUES (?, ?)")) {
                            ins.setString(1, player.getUniqueId().toString());
                            ins.setBoolean(2, true);
                            ins.executeUpdate();
                        }
                    }
                }

                // StaffChat (speak + hidden)
                try (PreparedStatement ps = conn.prepareStatement("SELECT enabled, hidden FROM staffchat WHERE uuid=?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        if (rs.getBoolean("enabled")) staffChatEnabled.add(player.getUniqueId());
                        boolean hidden = false;
                        try { hidden = rs.getBoolean("hidden"); } catch (SQLException ignore) {}
                        if (hidden) staffChatHidden.add(player.getUniqueId());
                    } else {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO staffchat (uuid, enabled, hidden) VALUES (?, ?, ?)")) {
                            ins.setString(1, player.getUniqueId().toString());
                            ins.setBoolean(2, false);
                            ins.setBoolean(3, false);
                            ins.executeUpdate();
                        } catch (SQLException fallback) {
                            try (PreparedStatement ins2 = conn.prepareStatement(
                                    "INSERT INTO staffchat (uuid, enabled) VALUES (?, ?)")) {
                                ins2.setString(1, player.getUniqueId().toString());
                                ins2.setBoolean(2, false);
                                ins2.executeUpdate();
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void saveCommandSpy(Player player, boolean enabled) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("REPLACE INTO commandspy (uuid, enabled) VALUES (?, ?)")) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setBoolean(2, enabled);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    // Save speak+hidden in one upsert
    private void saveStaffChatState(UUID uuid, boolean speakEnabled, boolean hidden) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
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
                } catch (SQLException ignore) { }
            }
        });
    }

    // Login Service Helper
    public org.thesandbox.core.login.LoginService getLoginService() { return loginService; }

    /* =================== CommandSpy Redaction Helper ==================== */

    /**
     * Build a display-safe version of the raw command for CommandSpy.
     * If the base command is blacklisted, redact arguments with a placeholder.
     * Shows ampersand color codes literally (e.g., "&c") while stripping any real (§) codes.
     *
     * Config keys:
     *   commandspy.blacklist: [ "login", "register" ]
     *   commandspy.redact_placeholder: "(REDACTED)"
     *   commandspy.format: "&7%player%: %command%"
     *
     * Legacy fallback for format: "commandspy-format"
     */
    private String spyDisplayCommand(String rawCommand) {
        if (rawCommand == null) return "";
        String placeholder = getConfig().getString("commandspy.redact_placeholder", "(REDACTED)");
        List<String> blacklist = getConfig().getStringList("commandspy.blacklist");
        if (blacklist == null) blacklist = java.util.Collections.emptyList();

        String msg = rawCommand.trim();

        // If it's not a slash command, still neutralize real (§) colors but keep & codes visible
        if (!msg.startsWith("/")) {
            // Strip actual section-sign formatting; DO NOT translate '&' codes
            msg = ChatColor.stripColor(msg);
            return msg;
        }

        // For commands: first neutralize any real (§) formatting
        msg = ChatColor.stripColor(msg);

        String noSlash = msg.substring(1).trim();
        if (noSlash.isEmpty()) return msg;

        String[] parts = noSlash.split("\\s+", 2);
        String usedBase = parts[0];
        String baseLc   = usedBase.toLowerCase(java.util.Locale.ENGLISH);

        boolean isBlacklisted = false;
        for (String entry : blacklist) {
            if (entry == null || entry.isEmpty()) continue;
            String normalized = entry.startsWith("/") ? entry.substring(1) : entry;
            if (baseLc.equalsIgnoreCase(normalized.toLowerCase(java.util.Locale.ENGLISH))) {
                isBlacklisted = true;
                break;
            }
        }

        if (!isBlacklisted) {
            // Show full command, with '&' codes visible and harmless
            return msg;
        }

        // Redact arguments (always show placeholder, conservative default)
        return "/" + usedBase + " " + placeholder;
    }

    /* =================== Events ==================== */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
        applyRankScoreboardTeam(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> applyRankScoreboardTeam(event.getPlayer()), 20L);
    }

    // Login message logic (uses shared helper)
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Boolean vanish_status = dataListener.get(p.getUniqueId(), PlayerDataKeys.VANISHED, false);

        if (vanish_status) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Boolean vanish_status = dataListener.get(p.getUniqueId(), PlayerDataKeys.VANISHED, false);

        if (vanish_status) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onVanishHide(PlayerHideEvent event) {
        final Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            // SAVE FOR PLAYER
            dataListener.set(p.getUniqueId(), PlayerDataKeys.VANISHED, true);
            // Staff-only notice
            String staffMsg = ChatColor.translateAlternateColorCodes(
                    '&', "&8[&b&lSTAFF&8] &c" + p.getName() + " vanished.");
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.hasPermission("sandbox.staff") && (shushService == null || !shushService.isEnabled(viewer.getUniqueId()))) {
                    viewer.sendMessage(staffMsg);
                }
            }
        });
    }

    @EventHandler
    public void onVanishShow(PlayerShowEvent event) {
        final Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            // SAVE FOR PLAYER
            dataListener.set(p.getUniqueId(), PlayerDataKeys.VANISHED, false);

            // Staff-only notice

            String staffMsg = ChatColor.translateAlternateColorCodes(
                    '&', "&8[&b&lSTAFF&8] &c" + p.getName() + " unvanished.");
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.hasPermission("sandbox.staff") && (shushService == null || !shushService.isEnabled(viewer.getUniqueId()))) {
                    viewer.sendMessage(staffMsg);
                }
            }
        });
    }

    private void LoginMessagesFakeLogin(Player player) {
        loginMessages.SendLoginMessage(player);
    }

    @EventHandler
    public void onCommandPre(PlayerCommandPreprocessEvent event) {
        final Player sender = event.getPlayer();

        // === Blocked? Cancel immediately ===
        if (isCommandsBlocked(sender)) {
            sender.sendMessage(ChatColor.RED + "Your commands are currently blocked.");
            event.setCancelled(true);
            return;
        }

        final String rawMessage = event.getMessage();

        // === CommandSpy redaction & display ===
        final String spyCommand = spyDisplayCommand(rawMessage); // color-neutralized for '§', '&' shown literally
        String fmt = getConfig().getString("commandspy.format",
                getConfig().getString("commandspy-format", "&7%player%: %command%"));
        String formatted = ChatColor.translateAlternateColorCodes('&', fmt)
                .replace("%player%", sender.getName())
                .replace("%command%", spyCommand);

        // Tier-based filtering (viewers see same-or-lower tiers only)
        CmdTier senderTier = getCmdTier(sender);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // Only staff receive CommandSpy at all
            if (!viewer.hasPermission("sandbox.staff")) continue;
            if (shushService != null && shushService.isEnabled(viewer.getUniqueId())) continue;
            if (isCmdSpyDisabled(viewer.getUniqueId())) continue;
            if (viewer == sender) continue;
            CmdTier viewerTier = getCmdTier(viewer);
            if (senderTier.v <= viewerTier.v) {
                viewer.sendMessage(formatted);
            }
        }

        // === Auto-archive the latest report when staff use /tpo (e.g., from "Click to Teleport") ===
        try {
            String raw = rawMessage.trim();
            if (raw.startsWith("/")) raw = raw.substring(1); // strip leading slash
            String[] parts = raw.split("\\s+");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase("tpo")) {
                String clickedName = parts[1]; // the player staff clicked on
                DiscordBridge bridge = this.discord;
                if (bridge != null && bridge.isReady()) {
                    bridge.tryArchiveRecentForPlayer(clickedName, sender.getName());
                }
            }
        } catch (Exception ignore) { }
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (staffChatEnabled.contains(p.getUniqueId())) {
            event.setCancelled(true);

            // Block speaking if they have staff chat hidden
            if (isStaffChatHidden(p.getUniqueId())) {
                p.sendMessage(ChatColor.RED + "You cannot talk in staff chat while it is hidden. Use /staffchat hide off.");
                return;
            }

            String name = p.getName(); // per your preference, raw username
            String msg = event.getMessage();

            Bukkit.getScheduler().runTask(this, () ->
                    broadcastStaffChat(name, msg, "", "", Source.MINECRAFT));
        }
    }

    /* =================== Helpers ==================== */

    /** Creates the rank teams used for overhead player prefixes. */
    private void setupRankScoreboardTeams() {
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

        // Prefix controls the rank text before the player's name.
        team.setPrefix(ChatColor.translateAlternateColorCodes('&', prefix));

        // Team color controls the actual player name color in nametags/overheads.
        // Without this, Minecraft may render the player's name as white even if the prefix is colored.
        try {
            team.setColor(nameColor);
        } catch (NoSuchMethodError ignored) {
            // Older APIs do not have Team#setColor; the prefix will still display.
        }

        return team;
    }

    /** Re-applies rank scoreboard teams for every online player. */
    public void refreshAllRankScoreboardTeams() {
        setupRankScoreboardTeams();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRankScoreboardTeam(player);
        }
    }

    /** Applies the overhead rank prefix team for one player based on their current permissions. */
    public void applyRankScoreboardTeam(Player player) {
        if (player == null) return;
        applyRankScoreboardTeam(player, loginService.getRank(player));
    }

    /** Applies the overhead rank prefix team for one player using a known rank. */
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
        switch (rank) {
            case OPERATOR: return "00_operator";
            case ADMIN:    return "10_admin";
            case STAFF:    return "20_staff";
            case MB:       return "30_mb";
            case VIP:      return "40_vip";
            case DEFAULT:
            default:       return "99_default";
        }
    }

    /** Build the exact join message used for normal joins and PremiumVanish unvanish. */
    private String buildJoinMessageFor(Player p) {
        if (VanishAPI.isInvisible(p)) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', "&a&lJoin &8» &7" + p.getName());
    }

    /** Build the exact leave message used for normal quits and PremiumVanish vanish. */
    private String buildLeaveMessageFor(Player p) {
        return ChatColor.translateAlternateColorCodes('&', "&c&lLeave &8» &7" + p.getName());
    }

    // Very simple heuristic: if the message contains both '<' and '>', assume MiniMessage-style
    private boolean looksLikeMiniMessage(String s) {
        if (s == null) return false;
        return s.contains("<") && s.contains(">");
    }
}
