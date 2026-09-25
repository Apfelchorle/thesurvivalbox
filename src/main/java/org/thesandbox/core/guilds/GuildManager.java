package org.thesandbox.core.guilds;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import org.thesandbox.core.util.HexColorUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.*;
import java.util.*;

public final class GuildManager {
    public static final List<String> RANK_PERMISSIONS = List.of(
            "invite", "tag", "kick", "delwarp", "sethome",
            "rank.set", "rank.delete", "rank.create", "rank.allow", "rank.deny", "toggle"
    );

    public static final List<String> TOGGLES = List.of("open", "public");

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yml;
    private HikariDataSource guildDataSource;
    private boolean mysqlGuildsAvailable = false;

    private final Map<String, Guild> guildsByName = new HashMap<>();
    private final Map<UUID, String> guildOfPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> guildChatToggled = new HashSet<>();
    private final Set<UUID> guildChatSpies = new HashSet<>();

    private static final long INVITE_EXPIRE_MS = 5L * 60L * 1000L;
    private static final int TAG_PREFIX_PRIORITY = 200;

    public GuildManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
        setupMysqlGuilds();
        reload();
    }

    public void shutdown() {
        try {
            if (guildDataSource != null) guildDataSource.close();
        } catch (Throwable t) {
            plugin.getLogger().warning("[Guilds] MySQL shutdown error: " + t.getMessage());
        } finally {
            guildDataSource = null;
            mysqlGuildsAvailable = false;
        }
    }

    public void reload() {
        this.yml = YamlConfiguration.loadConfiguration(file);
        guildsByName.clear();
        guildOfPlayer.clear();

        if (mysqlGuildsAvailable) {
            if (hasYamlGuilds()) {
                loadFromYaml();
                loadExistingMysqlLocationsIntoMemory();
                if (saveAllGuildsToMysql()) {
                    deleteMigratedYamlFile();
                    plugin.getLogger().info("[Guilds] Migrated guilds.yml into MySQL successfully.");
                } else {
                    plugin.getLogger().severe("[Guilds] MySQL migration failed. Keeping guilds.yml and using the in-memory YAML data for this boot.");
                }
            } else {
                loadFromMysql();
            }
            return;
        }

        loadFromYaml();
    }

    private boolean hasYamlGuilds() {
        ConfigurationSection root = yml.getConfigurationSection("guilds");
        return file.exists() && root != null && !root.getKeys(false).isEmpty();
    }

    private void rebuildGuildIndex() {
        guildOfPlayer.clear();
        for (Guild g : guildsByName.values()) {
            for (UUID u : g.members().keySet()) guildOfPlayer.put(u, g.name());
        }
    }

    private void deleteMigratedYamlFile() {
        if (!file.exists()) return;
        if (!file.delete()) {
            plugin.getLogger().warning("[Guilds] Migrated guilds.yml into MySQL, but could not delete the old guilds.yml file. You can delete it manually after confirming the MySQL data loaded correctly.");
        }
    }

    private void loadFromYaml() {
        ConfigurationSection root = yml.getConfigurationSection("guilds");
        if (root == null) return;

        for (String guildName : root.getKeys(false)) {
            String base = "guilds." + guildName + ".";
            String tag = yml.getString(base + "tag", guildName);
            String ownerStr = yml.getString(base + "owner", null);
            if (ownerStr == null) continue;

            UUID owner;
            try { owner = UUID.fromString(ownerStr); }
            catch (IllegalArgumentException e) { continue; }

            Guild g = new Guild(guildName, tag, owner);
            g.setOpen(yml.getBoolean(base + "toggles.open", false));
            g.setPublicHome(yml.getBoolean(base + "toggles.public", false));
            Location loadedHome = readLocation(base + "home");
            if (loadedHome != null) g.setHome(loadedHome);

            ConfigurationSection ranksSec = yml.getConfigurationSection(base + "ranks");
            if (ranksSec != null) {
                g.ranks().clear();
                for (String r : ranksSec.getKeys(false)) {
                    g.ranks().put(r.toUpperCase(Locale.ROOT), ranksSec.getString(r, ""));
                }
                g.ranks().putIfAbsent("OWNER", "");
                g.ranks().putIfAbsent("MEMBER", "");
            }

            g.rankPermissions().clear();
            for (String rank : g.ranks().keySet()) {
                g.rankPermissions().put(rank.toUpperCase(Locale.ROOT), new HashSet<>());
            }
            ConfigurationSection permSec = yml.getConfigurationSection(base + "rank-permissions");
            if (permSec != null) {
                for (String r : permSec.getKeys(false)) {
                    String key = r.toUpperCase(Locale.ROOT);
                    g.rankPermissions().putIfAbsent(key, new HashSet<>());
                    for (String perm : permSec.getStringList(r)) {
                        String normalized = normalizePermission(perm);
                        if (RANK_PERMISSIONS.contains(normalized)) g.rankPermissions().get(key).add(normalized);
                    }
                }
            }

            ConfigurationSection memSec = yml.getConfigurationSection(base + "members");
            g.members().clear();
            if (memSec != null) {
                for (String uuidStr : memSec.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(uuidStr);
                        String rank = memSec.getString(uuidStr, "MEMBER");
                        g.members().put(u, rank.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            g.members().putIfAbsent(owner, "OWNER");

            ConfigurationSection warpSec = yml.getConfigurationSection(base + "warps");
            if (warpSec != null) {
                for (String warpName : warpSec.getKeys(false)) {
                    Location location = readLocation(base + "warps." + warpName);
                    if (location != null) g.setWarp(warpName, location);
                }
            }

            guildsByName.put(guildName.toLowerCase(Locale.ROOT), g);
            for (UUID u : g.members().keySet()) guildOfPlayer.put(u, g.name());
        }
    }

    public void save() {
        if (mysqlGuildsAvailable) {
            if (!saveAllGuildsToMysql()) {
                plugin.getLogger().severe("[Guilds] Failed to save guild data to MySQL. Current changes remain in memory until the next successful save.");
            }
            return;
        }

        yml.set("guilds", null);

        for (Guild g : new HashSet<>(guildsByName.values())) {
            String base = "guilds." + g.name() + ".";
            yml.set(base + "tag", g.tag());
            yml.set(base + "owner", g.owner().toString());
            yml.set(base + "toggles.open", g.isOpen());
            yml.set(base + "toggles.public", g.isPublicHome());
            writeLocation(base + "home", g.home());

            for (var e : g.ranks().entrySet()) yml.set(base + "ranks." + e.getKey(), e.getValue());
            for (var e : g.rankPermissions().entrySet()) yml.set(base + "rank-permissions." + e.getKey(), new ArrayList<>(e.getValue()));
            for (var e : g.members().entrySet()) yml.set(base + "members." + e.getKey(), e.getValue());
            for (var e : g.warps().entrySet()) writeLocation(base + "warps." + e.getKey(), e.getValue());
        }

        try { yml.save(file); }
        catch (Exception e) { plugin.getLogger().severe("Failed to save guilds.yml: " + e.getMessage()); }
    }


    private void setupMysqlGuilds() {
        try {
            String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("mysql.port", 3306);
            String db = plugin.getConfig().getString("mysql.database", "sandbox");
            String user = plugin.getConfig().getString("mysql.user", "sandbox");
            String pass = plugin.getConfig().getString("mysql.password", "");

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true");
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setPoolName("SandboxGuildsPool");
            cfg.setMaximumPoolSize(3);
            cfg.setMinimumIdle(1);
            cfg.setConnectionTimeout(10000);
            cfg.setIdleTimeout(600000);
            cfg.setMaxLifetime(1800000);

            this.guildDataSource = new HikariDataSource(cfg);
            ensureMysqlGuildTables();
            this.mysqlGuildsAvailable = true;
            plugin.getLogger().info("[Guilds] Guild data is using MySQL.");
        } catch (Throwable t) {
            this.mysqlGuildsAvailable = false;
            plugin.getLogger().warning("[Guilds] MySQL guild storage unavailable; falling back to guilds.yml: " + t.getMessage());
            try { if (guildDataSource != null) guildDataSource.close(); } catch (Throwable ignored) {}
            guildDataSource = null;
        }
    }

    private void ensureMysqlGuildTables() throws SQLException {
        try (Connection c = guildDataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS sandbox_guilds (" +
                    "guild_name VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "tag VARCHAR(64) NOT NULL," +
                    "owner_uuid CHAR(36) NOT NULL," +
                    "open_join BOOLEAN NOT NULL DEFAULT FALSE," +
                    "public_home BOOLEAN NOT NULL DEFAULT FALSE," +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS sandbox_guild_ranks (" +
                    "guild_name VARCHAR(64) NOT NULL," +
                    "rank_name VARCHAR(64) NOT NULL," +
                    "prefix TEXT NOT NULL," +
                    "PRIMARY KEY (guild_name, rank_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS sandbox_guild_rank_permissions (" +
                    "guild_name VARCHAR(64) NOT NULL," +
                    "rank_name VARCHAR(64) NOT NULL," +
                    "permission VARCHAR(64) NOT NULL," +
                    "PRIMARY KEY (guild_name, rank_name, permission)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS sandbox_guild_members (" +
                    "guild_name VARCHAR(64) NOT NULL," +
                    "player_uuid CHAR(36) NOT NULL PRIMARY KEY," +
                    "rank_name VARCHAR(64) NOT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS sandbox_guild_homes (" +
                    "guild_name VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "world VARCHAR(128) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL," +
                    "pitch FLOAT NOT NULL," +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS sandbox_guild_warps (" +
                    "guild_name VARCHAR(64) NOT NULL," +
                    "warp_name VARCHAR(64) NOT NULL," +
                    "world VARCHAR(128) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw FLOAT NOT NULL," +
                    "pitch FLOAT NOT NULL," +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (guild_name, warp_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            ensureMysqlGuildColumns(c);
        }
    }

    private void ensureMysqlGuildColumns(Connection c) throws SQLException {
        ensureColumn(c, "sandbox_guilds", "open_join", "TINYINT(1) NOT NULL DEFAULT 0");
        ensureColumn(c, "sandbox_guilds", "public_home", "TINYINT(1) NOT NULL DEFAULT 0");
        // Compatibility with the existing s2_sandbox schema.
        ensureColumn(c, "sandbox_guilds", "is_open", "TINYINT(1) NOT NULL DEFAULT 0");
        ensureColumn(c, "sandbox_guilds", "is_public_home", "TINYINT(1) NOT NULL DEFAULT 0");
        ensureColumn(c, "sandbox_guilds", "public_guild", "TINYINT(1) NOT NULL DEFAULT 0");
    }

    private void ensureColumn(Connection c, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), null, table, column)) {
            if (rs.next()) return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }


    private void loadExistingMysqlLocationsIntoMemory() {
        if (!mysqlGuildsAvailable || guildsByName.isEmpty()) return;

        try (Connection c = guildDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT guild_name, world, x, y, z, yaw, pitch FROM sandbox_guild_homes");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Guild g = getGuildByName(rs.getString("guild_name"));
                if (g == null || g.home() != null) continue;
                Location loc = locationFromResult(rs);
                if (loc != null) g.setHome(loc);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Guilds] Could not preserve existing MySQL homes during guilds.yml migration: " + e.getMessage());
        }

        try (Connection c = guildDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT guild_name, warp_name, world, x, y, z, yaw, pitch FROM sandbox_guild_warps");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Guild g = getGuildByName(rs.getString("guild_name"));
                if (g == null || g.warp(rs.getString("warp_name")) != null) continue;
                Location loc = locationFromResult(rs);
                if (loc != null) g.setWarp(rs.getString("warp_name"), loc);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[Guilds] Could not preserve existing MySQL warps during guilds.yml migration: " + e.getMessage());
        }
    }

    private boolean saveAllGuildsToMysql() {
        if (!mysqlGuildsAvailable) return false;

        try (Connection c = guildDataSource.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM sandbox_guild_rank_permissions");
                    st.executeUpdate("DELETE FROM sandbox_guild_ranks");
                    st.executeUpdate("DELETE FROM sandbox_guild_members");
                    // Do not delete sandbox_guilds here. Existing installs may have foreign keys
                    // from homes/warps to sandbox_guilds with ON DELETE CASCADE. Deleting guilds
                    // during a normal save can silently wipe every guild home and warp.
                    // Guild rows are upserted below instead.
                }

                try (PreparedStatement guildPs = c.prepareStatement("INSERT INTO sandbox_guilds (guild_name, tag, owner_uuid, open_join, public_home, public_guild, is_open, is_public_home) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE tag = VALUES(tag), owner_uuid = VALUES(owner_uuid), open_join = VALUES(open_join), public_home = VALUES(public_home), public_guild = VALUES(public_guild), is_open = VALUES(is_open), is_public_home = VALUES(is_public_home)");
                     PreparedStatement rankPs = c.prepareStatement("INSERT INTO sandbox_guild_ranks (guild_name, rank_name, prefix) VALUES (?, ?, ?)");
                     PreparedStatement permPs = c.prepareStatement("INSERT INTO sandbox_guild_rank_permissions (guild_name, rank_name, permission) VALUES (?, ?, ?)");
                     PreparedStatement memberPs = c.prepareStatement("INSERT INTO sandbox_guild_members (guild_name, player_uuid, rank_name) VALUES (?, ?, ?)");
                     PreparedStatement homePs = c.prepareStatement("INSERT INTO sandbox_guild_homes (guild_name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)");
                     PreparedStatement warpPs = c.prepareStatement("INSERT INTO sandbox_guild_warps (guild_name, warp_name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)")) {

                    for (Guild g : new HashSet<>(guildsByName.values())) {
                        guildPs.setString(1, g.name());
                        guildPs.setString(2, g.tag() == null ? g.name() : g.tag());
                        guildPs.setString(3, g.owner().toString());
                        guildPs.setBoolean(4, g.isOpen());
                        guildPs.setBoolean(5, g.isPublicHome());
                        guildPs.setBoolean(6, g.isPublicHome());
                        guildPs.setBoolean(7, g.isOpen());
                        guildPs.setBoolean(8, g.isPublicHome());
                        guildPs.addBatch();

                        for (var e : g.ranks().entrySet()) {
                            rankPs.setString(1, g.name());
                            rankPs.setString(2, e.getKey().toUpperCase(Locale.ROOT));
                            rankPs.setString(3, e.getValue() == null ? "" : e.getValue());
                            rankPs.addBatch();
                        }

                        for (var e : g.rankPermissions().entrySet()) {
                            String rankName = e.getKey().toUpperCase(Locale.ROOT);
                            for (String permission : e.getValue()) {
                                String normalized = normalizePermission(permission);
                                if (!RANK_PERMISSIONS.contains(normalized)) continue;
                                permPs.setString(1, g.name());
                                permPs.setString(2, rankName);
                                permPs.setString(3, normalized);
                                permPs.addBatch();
                            }
                        }

                        for (var e : g.members().entrySet()) {
                            memberPs.setString(1, g.name());
                            memberPs.setString(2, e.getKey().toString());
                            memberPs.setString(3, e.getValue() == null ? "MEMBER" : e.getValue().toUpperCase(Locale.ROOT));
                            memberPs.addBatch();
                        }

                        if (g.home() != null && g.home().getWorld() != null) {
                            bindLocation(homePs, g.name(), g.home(), 1);
                            homePs.addBatch();
                        }

                        for (var e : g.warps().entrySet()) {
                            Location loc = e.getValue();
                            if (loc == null || loc.getWorld() == null) continue;
                            warpPs.setString(1, g.name());
                            warpPs.setString(2, e.getKey().toLowerCase(Locale.ROOT));
                            warpPs.setString(3, loc.getWorld().getName());
                            warpPs.setDouble(4, loc.getX());
                            warpPs.setDouble(5, loc.getY());
                            warpPs.setDouble(6, loc.getZ());
                            warpPs.setFloat(7, loc.getYaw());
                            warpPs.setFloat(8, loc.getPitch());
                            warpPs.addBatch();
                        }
                    }

                    guildPs.executeBatch();
                    rankPs.executeBatch();
                    permPs.executeBatch();
                    memberPs.executeBatch();
                    homePs.executeBatch();
                    warpPs.executeBatch();
                }

                c.commit();
                c.setAutoCommit(oldAutoCommit);
                return true;
            } catch (SQLException e) {
                c.rollback();
                c.setAutoCommit(oldAutoCommit);
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] MySQL save failed: " + e.getMessage());
            return false;
        }
    }

    private void loadFromMysql() {
        guildsByName.clear();
        guildOfPlayer.clear();

        try (Connection c = guildDataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT guild_name, tag, owner_uuid, COALESCE(open_join, is_open, 0) AS open_join, COALESCE(public_home, public_guild, is_public_home, 0) AS public_home FROM sandbox_guilds");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID owner;
                    try { owner = UUID.fromString(rs.getString("owner_uuid")); }
                    catch (IllegalArgumentException e) { continue; }

                    Guild g = new Guild(rs.getString("guild_name"), rs.getString("tag"), owner);
                    g.setOpen(rs.getBoolean("open_join"));
                    g.setPublicHome(rs.getBoolean("public_home"));
                    g.ranks().clear();
                    g.rankPermissions().clear();
                    g.members().clear();
                    guildsByName.put(g.name().toLowerCase(Locale.ROOT), g);
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT guild_name, rank_name, prefix FROM sandbox_guild_ranks");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Guild g = getGuildByName(rs.getString("guild_name"));
                    if (g == null) continue;
                    g.ranks().put(rs.getString("rank_name").toUpperCase(Locale.ROOT), rs.getString("prefix"));
                }
            }

            for (Guild g : guildsByName.values()) {
                g.ranks().putIfAbsent("OWNER", "");
                g.ranks().putIfAbsent("MEMBER", "");
                for (String rank : g.ranks().keySet()) g.rankPermissions().putIfAbsent(rank.toUpperCase(Locale.ROOT), new HashSet<>());
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT guild_name, rank_name, permission FROM sandbox_guild_rank_permissions");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Guild g = getGuildByName(rs.getString("guild_name"));
                    if (g == null) continue;
                    String rank = rs.getString("rank_name").toUpperCase(Locale.ROOT);
                    String permission = normalizePermission(rs.getString("permission"));
                    if (!RANK_PERMISSIONS.contains(permission)) continue;
                    g.rankPermissions().computeIfAbsent(rank, ignored -> new HashSet<>()).add(permission);
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT guild_name, player_uuid, rank_name FROM sandbox_guild_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Guild g = getGuildByName(rs.getString("guild_name"));
                    if (g == null) continue;
                    try {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        g.members().put(uuid, rs.getString("rank_name").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            for (Guild g : guildsByName.values()) {
                g.members().putIfAbsent(g.owner(), "OWNER");
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT guild_name, world, x, y, z, yaw, pitch FROM sandbox_guild_homes");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Guild g = getGuildByName(rs.getString("guild_name"));
                    if (g == null) continue;
                    Location loc = locationFromResult(rs);
                    if (loc != null) g.setHome(loc);
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT guild_name, warp_name, world, x, y, z, yaw, pitch FROM sandbox_guild_warps");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Guild g = getGuildByName(rs.getString("guild_name"));
                    if (g == null) continue;
                    Location loc = locationFromResult(rs);
                    if (loc != null) g.setWarp(rs.getString("warp_name"), loc);
                }
            }

            rebuildGuildIndex();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to load guild data from MySQL: " + e.getMessage());
        }
    }

    private Location locationFromResult(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world");
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[Guilds] Could not load MySQL location because world '" + worldName + "' is not loaded.");
            return null;
        }
        return new Location(
                world,
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch")
        );
    }

    private Location loadHomeMysql(String guildName) {
        if (!mysqlGuildsAvailable || guildName == null) return null;
        try (Connection c = guildDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM sandbox_guild_homes WHERE guild_name = ?")) {
            ps.setString(1, guildName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return locationFromResult(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to load guild home from MySQL for " + guildName + ": " + e.getMessage());
            return null;
        }
    }

    private Location loadWarpMysql(String guildName, String warpName) {
        if (!mysqlGuildsAvailable || guildName == null || warpName == null) return null;
        try (Connection c = guildDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM sandbox_guild_warps WHERE guild_name = ? AND warp_name = ?")) {
            ps.setString(1, guildName);
            ps.setString(2, warpName.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return locationFromResult(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to load guild warp '" + warpName + "' from MySQL for " + guildName + ": " + e.getMessage());
            return null;
        }
    }

    private Set<String> loadWarpNamesMysql(String guildName) {
        if (!mysqlGuildsAvailable || guildName == null) return Set.of();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (Connection c = guildDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT warp_name FROM sandbox_guild_warps WHERE guild_name = ? ORDER BY warp_name")) {
            ps.setString(1, guildName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("warp_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to load guild warp names from MySQL for " + guildName + ": " + e.getMessage());
        }
        return names;
    }

    private boolean upsertHomeMysql(String guildName, Location loc) {
        if (!mysqlGuildsAvailable || guildName == null || loc == null || loc.getWorld() == null) return false;
        String sql = "INSERT INTO sandbox_guild_homes (guild_name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)";
        try (Connection c = guildDataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindLocation(ps, guildName, loc, 1);
            ps.executeUpdate();
            plugin.getLogger().info("[Guilds] Saved guild home to MySQL for " + guildName + ".");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to save guild home to MySQL for " + guildName + ": " + e.getMessage());
            return false;
        }
    }

    private boolean upsertWarpMysql(String guildName, String warpName, Location loc) {
        if (!mysqlGuildsAvailable || guildName == null || warpName == null || loc == null || loc.getWorld() == null) return false;
        String sql = "INSERT INTO sandbox_guild_warps (guild_name, warp_name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)";
        try (Connection c = guildDataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildName);
            ps.setString(2, warpName.toLowerCase(Locale.ROOT));
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.executeUpdate();
            plugin.getLogger().info("[Guilds] Saved guild warp '" + warpName + "' to MySQL for " + guildName + ".");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to save guild warp '" + warpName + "' to MySQL for " + guildName + ": " + e.getMessage());
            return false;
        }
    }

    private int deleteWarpMysql(String guildName, String warpName) {
        if (!mysqlGuildsAvailable || guildName == null || warpName == null) return -1;
        try (Connection c = guildDataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM sandbox_guild_warps WHERE guild_name = ? AND warp_name = ?")) {
            ps.setString(1, guildName);
            ps.setString(2, warpName.toLowerCase(Locale.ROOT));
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to delete guild warp '" + warpName + "' from MySQL for " + guildName + ": " + e.getMessage());
            return -1;
        }
    }

    private void deleteGuildFromMysql(String guildName) {
        if (!mysqlGuildsAvailable || guildName == null) return;
        try (Connection c = guildDataSource.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                deleteGuildRows(c, "sandbox_guild_rank_permissions", guildName);
                deleteGuildRows(c, "sandbox_guild_ranks", guildName);
                deleteGuildRows(c, "sandbox_guild_members", guildName);
                deleteGuildRows(c, "sandbox_guild_homes", guildName);
                deleteGuildRows(c, "sandbox_guild_warps", guildName);
                deleteGuildRows(c, "sandbox_guilds", guildName);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to delete MySQL guild " + guildName + ": " + e.getMessage());
        }
    }

    private void deleteGuildRows(Connection c, String table, String guildName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE guild_name = ?")) {
            ps.setString(1, guildName);
            ps.executeUpdate();
        }
    }

    private void bindLocation(PreparedStatement ps, String guildName, Location loc, int start) throws SQLException {
        ps.setString(start, guildName);
        ps.setString(start + 1, loc.getWorld().getName());
        ps.setDouble(start + 2, loc.getX());
        ps.setDouble(start + 3, loc.getY());
        ps.setDouble(start + 4, loc.getZ());
        ps.setFloat(start + 5, loc.getYaw());
        ps.setFloat(start + 6, loc.getPitch());
    }


    private void writeLocation(String path, Location loc) {
        yml.set(path, null);
        if (loc == null || loc.getWorld() == null) return;
        yml.set(path + ".world", loc.getWorld().getName());
        yml.set(path + ".x", loc.getX());
        yml.set(path + ".y", loc.getY());
        yml.set(path + ".z", loc.getZ());
        yml.set(path + ".yaw", loc.getYaw());
        yml.set(path + ".pitch", loc.getPitch());
    }

    private Location readLocation(String path) {
        Location direct = yml.getLocation(path);
        if (direct != null) return direct;

        String worldName = yml.getString(path + ".world");
        if (worldName == null || worldName.isBlank()) return null;
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Could not load guild location at " + path + " because world '" + worldName + "' is not loaded.");
            return null;
        }

        return new Location(
                world,
                yml.getDouble(path + ".x"),
                yml.getDouble(path + ".y"),
                yml.getDouble(path + ".z"),
                (float) yml.getDouble(path + ".yaw"),
                (float) yml.getDouble(path + ".pitch")
        );
    }

    public boolean isInGuild(UUID uuid) { return guildOfPlayer.containsKey(uuid); }
    public String guildNameOf(UUID uuid) { return guildOfPlayer.get(uuid); }
    public Guild guildOf(UUID uuid) {
        String name = guildOfPlayer.get(uuid);
        return name == null ? null : guildsByName.get(name.toLowerCase(Locale.ROOT));
    }
    public Guild getGuildByName(String guildName) { return guildName == null ? null : guildsByName.get(guildName.toLowerCase(Locale.ROOT)); }
    public Collection<Guild> allGuilds() { return new HashSet<>(guildsByName.values()); }

    public boolean isGuildChatToggled(UUID uuid) { return guildChatToggled.contains(uuid); }
    public void toggleGuildChat(UUID uuid) { if (!guildChatToggled.add(uuid)) guildChatToggled.remove(uuid); }

    public boolean isGuildChatSpy(UUID uuid) { return guildChatSpies.contains(uuid); }
    public void toggleGuildChatSpy(UUID uuid) { if (!guildChatSpies.add(uuid)) guildChatSpies.remove(uuid); }

    public boolean sendGuildChat(Player sender, String rawMessage) {
        if (sender == null || rawMessage == null) return false;
        Guild g = guildOf(sender.getUniqueId());
        if (g == null) return false;

        Component line = Text.c("&6&l[GUILD]&r ");
        String rankTag = g.visiblePrefixFor(sender.getUniqueId());
        if (rankTag != null && !rankTag.isBlank()) {
            line = line.append(rich(rankTag)).append(Component.space()).append(Text.c("&r"));
        }
        line = line
                .append(guildChatDisplayName(sender))
                .append(Component.space())
                .append(Text.c("&8»&r"))
                .append(Component.space())
                .append(rich(rawMessage));

        Set<UUID> delivered = new HashSet<>();
        for (UUID member : g.members().keySet()) {
            Player target = Bukkit.getPlayer(member);
            if (target != null) {
                target.sendMessage(line);
                delivered.add(target.getUniqueId());
            }
        }
        for (UUID spyId : guildChatSpies) {
            Player spy = Bukkit.getPlayer(spyId);
            if (spy != null && !delivered.contains(spy.getUniqueId()) && spy.hasPermission("sandbox.staff")) {
                spy.sendMessage(line);
            }
        }

        plugin.getLogger().info("[GuildChat:" + g.name() + "] " + PlainTextComponentSerializer.plainText().serialize(line));
        return true;
    }

    private Component guildChatDisplayName(Player player) {
        String nickname = essentialsNickname(player);
        if (nickname != null && !nickname.isBlank()) {
            return rich(nickname);
        }
        return Text.c("&a" + player.getName());
    }

    private String essentialsNickname(Player player) {
        try {
            Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
            if (essentials == null) return null;

            Method getUser = essentials.getClass().getMethod("getUser", Player.class);
            Object user = getUser.invoke(essentials, player);
            if (user == null) return null;

            Method getNickname = user.getClass().getMethod("getNickname");
            Object nick = getNickname.invoke(user);
            if (nick instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Throwable ignored) {
            // Essentials is optional; fall back to the player's username.
        }
        return null;
    }

    private Component rich(String input) {
        if (input == null || input.isEmpty()) return Component.empty();

        // Supports mixed guild-chat formatting in one message:
        //   legacy colors: &a, &l, &r, etc.
        //   hex colors: &#ff00ff, #ff00ff, &x&f&f&0&0&f&f
        //   MiniMessage: <red>, <bold>, <gradient:#ff0:#f0f>, etc.
        if (looksLikeMiniMessage(input)) {
            try {
                return MiniMessage.miniMessage().deserialize(legacyToMiniMessage(input));
            } catch (Exception ignored) {
                return Text.c(input);
            }
        }
        return Text.c(input);
    }

    private boolean looksLikeMiniMessage(String input) {
        return input.contains("<") && input.contains(">");
    }

    private String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return "";

        String out = input.replace('§', '&');

        // Convert legacy repeated hex: &x&f&f&0&0&f&f -> <#ff00ff>
        java.util.regex.Matcher repeatedHex = java.util.regex.Pattern
                .compile("(?i)&x&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])")
                .matcher(out);
        StringBuffer hexBuffer = new StringBuffer();
        while (repeatedHex.find()) {
            String hex = "#" + repeatedHex.group(1) + repeatedHex.group(2) + repeatedHex.group(3)
                    + repeatedHex.group(4) + repeatedHex.group(5) + repeatedHex.group(6);
            repeatedHex.appendReplacement(hexBuffer, java.util.regex.Matcher.quoteReplacement("<" + hex + ">"));
        }
        repeatedHex.appendTail(hexBuffer);
        out = hexBuffer.toString();

        // Convert &#ff00ff and plain #ff00ff into MiniMessage hex colors.
        java.util.regex.Matcher hex = java.util.regex.Pattern
                .compile("(?i)&?#([0-9a-f]{6})")
                .matcher(out);
        StringBuffer directHexBuffer = new StringBuffer();
        while (hex.find()) {
            hex.appendReplacement(directHexBuffer, java.util.regex.Matcher.quoteReplacement("<#" + hex.group(1) + ">"));
        }
        hex.appendTail(directHexBuffer);
        out = directHexBuffer.toString();

        out = out.replaceAll("(?i)&0", "<black>");
        out = out.replaceAll("(?i)&1", "<dark_blue>");
        out = out.replaceAll("(?i)&2", "<dark_green>");
        out = out.replaceAll("(?i)&3", "<dark_aqua>");
        out = out.replaceAll("(?i)&4", "<dark_red>");
        out = out.replaceAll("(?i)&5", "<dark_purple>");
        out = out.replaceAll("(?i)&6", "<gold>");
        out = out.replaceAll("(?i)&7", "<gray>");
        out = out.replaceAll("(?i)&8", "<dark_gray>");
        out = out.replaceAll("(?i)&9", "<blue>");
        out = out.replaceAll("(?i)&a", "<green>");
        out = out.replaceAll("(?i)&b", "<aqua>");
        out = out.replaceAll("(?i)&c", "<red>");
        out = out.replaceAll("(?i)&d", "<light_purple>");
        out = out.replaceAll("(?i)&e", "<yellow>");
        out = out.replaceAll("(?i)&f", "<white>");
        out = out.replaceAll("(?i)&l", "<bold>");
        out = out.replaceAll("(?i)&m", "<strikethrough>");
        out = out.replaceAll("(?i)&n", "<underlined>");
        out = out.replaceAll("(?i)&o", "<italic>");
        out = out.replaceAll("(?i)&r", "<reset>");
        // There is no safe direct obfuscated conversion needed for chat content here.
        out = out.replaceAll("(?i)&k", "");

        return out;
    }

    public enum CreateResult { OK, ALREADY_IN_GUILD, NAME_TAKEN }
    public CreateResult createGuild(String name, String tag, UUID owner) {
        if (isInGuild(owner)) return CreateResult.ALREADY_IN_GUILD;
        if (getGuildByName(name) != null) return CreateResult.NAME_TAKEN;
        Guild g = new Guild(name, tag, owner);
        guildsByName.put(name.toLowerCase(Locale.ROOT), g);
        guildOfPlayer.put(owner, name);
        save();
        return CreateResult.OK;
    }

    public boolean disbandGuild(Guild g) {
        if (g == null) return false;
        for (UUID u : new ArrayList<>(g.members().keySet())) {
            guildOfPlayer.remove(u);
            guildChatToggled.remove(u);
        }
        guildsByName.remove(g.name().toLowerCase(Locale.ROOT));
        deleteGuildFromMysql(g.name());
        save();
        return true;
    }

    public boolean leaveGuild(UUID player) {
        Guild g = guildOf(player);
        if (g == null || g.isOwner(player)) return false;
        g.removeMember(player);
        guildOfPlayer.remove(player);
        guildChatToggled.remove(player);
        save();
        return true;
    }

    public enum InviteResult { OK, NOT_IN_GUILD, NO_PERMISSION, TARGET_IN_GUILD }
    public InviteResult invite(UUID inviter, UUID target) {
        Guild g = guildOf(inviter);
        if (g == null) return InviteResult.NOT_IN_GUILD;
        if (!hasGuildPermission(inviter, "invite")) return InviteResult.NO_PERMISSION;
        if (isInGuild(target)) return InviteResult.TARGET_IN_GUILD;
        invites.put(target, new Invite(g.name(), inviter, System.currentTimeMillis()));
        return InviteResult.OK;
    }

    public enum RevokeInviteResult { OK, NOT_IN_GUILD, NO_PERMISSION, NO_INVITE, WRONG_GUILD }
    public RevokeInviteResult revokeInvite(UUID actor, UUID target) {
        Guild g = guildOf(actor);
        if (g == null) return RevokeInviteResult.NOT_IN_GUILD;
        if (!g.isOwner(actor) && !hasGuildPermission(actor, "invite")) return RevokeInviteResult.NO_PERMISSION;
        Invite inv = invites.get(target);
        if (inv == null) return RevokeInviteResult.NO_INVITE;
        if (!g.name().equalsIgnoreCase(inv.guildName())) return RevokeInviteResult.WRONG_GUILD;
        invites.remove(target);
        return RevokeInviteResult.OK;
    }

    public boolean hasPendingInvite(UUID target) {
        Invite inv = invites.get(target);
        if (inv == null) return false;
        if (System.currentTimeMillis() - inv.createdAtMillis() > INVITE_EXPIRE_MS) {
            invites.remove(target);
            return false;
        }
        return getGuildByName(inv.guildName()) != null;
    }

    public boolean denyInvite(UUID target) { return invites.remove(target) != null; }

    public enum JoinResult { OK, NO_INVITE, INVITE_EXPIRED, ALREADY_IN_GUILD, GUILD_MISSING, NOT_OPEN }
    public JoinResult join(UUID player) { return join(player, null); }
    public JoinResult join(UUID player, String guildName) {
        if (isInGuild(player)) return JoinResult.ALREADY_IN_GUILD;
        if (guildName != null && !guildName.isBlank()) {
            Guild g = getGuildByName(guildName);
            if (g == null) return JoinResult.GUILD_MISSING;
            if (!g.isOpen()) return JoinResult.NOT_OPEN;
            g.addMember(player);
            guildOfPlayer.put(player, g.name());
            save();
            return JoinResult.OK;
        }

        Invite inv = invites.get(player);
        if (inv == null) return JoinResult.NO_INVITE;
        if (System.currentTimeMillis() - inv.createdAtMillis() > INVITE_EXPIRE_MS) {
            invites.remove(player);
            return JoinResult.INVITE_EXPIRED;
        }
        Guild g = getGuildByName(inv.guildName());
        if (g == null) {
            invites.remove(player);
            return JoinResult.GUILD_MISSING;
        }
        g.addMember(player);
        guildOfPlayer.put(player, g.name());
        invites.remove(player);
        save();
        return JoinResult.OK;
    }

    public enum KickResult { OK, NO_GUILD, NO_PERMISSION, TARGET_NOT_IN_GUILD, CANNOT_KICK_OWNER }
    public KickResult kickMember(UUID actor, String targetName) {
        Guild g = guildOf(actor);
        if (g == null) return KickResult.NO_GUILD;
        if (!hasGuildPermission(actor, "kick")) return KickResult.NO_PERMISSION;
        return kickMemberFromGuild(g, targetName);
    }

    public KickResult kickMemberFromGuild(Guild g, String targetName) {
        if (g == null) return KickResult.NO_GUILD;
        UUID target = findMemberByName(g, targetName);
        if (target == null) return KickResult.TARGET_NOT_IN_GUILD;
        if (g.isOwner(target)) return KickResult.CANNOT_KICK_OWNER;
        g.removeMember(target);
        guildOfPlayer.remove(target);
        guildChatToggled.remove(target);
        save();
        return KickResult.OK;
    }

    public UUID memberByName(Guild g, String name) {
        return findMemberByName(g, name);
    }

    private UUID findMemberByName(Guild g, String name) {
        if (g == null || name == null) return null;
        for (UUID member : g.members().keySet()) {
            String memberName = nameOf(member);
            if (memberName != null && memberName.equalsIgnoreCase(name)) return member;
        }
        return null;
    }

    public enum TransferResult { OK, NO_GUILD, NOT_OWNER, TARGET_NOT_IN_GUILD, ALREADY_OWNER, STORAGE_ERROR }

    public TransferResult transferOwnership(UUID actor, String targetName) {
        Guild g = guildOf(actor);
        if (g == null) return TransferResult.NO_GUILD;
        if (!g.isOwner(actor)) return TransferResult.NOT_OWNER;

        UUID target = findMemberByName(g, targetName);
        if (target == null) return TransferResult.TARGET_NOT_IN_GUILD;
        if (target.equals(actor)) return TransferResult.ALREADY_OWNER;

        UUID oldOwner = g.owner();
        String oldOwnerPrefix = g.visiblePrefixFor(oldOwner);
        String newOwnerPrefix = g.visiblePrefixFor(target);

        if (mysqlGuildsAvailable && !transferOwnershipMysql(g.name(), oldOwner, target)) {
            return TransferResult.STORAGE_ERROR;
        }

        if (!g.transferOwnership(target)) return TransferResult.TARGET_NOT_IN_GUILD;

        if (!mysqlGuildsAvailable) save();

        refreshGuildRankPrefix(oldOwner, oldOwnerPrefix);
        refreshGuildRankPrefix(target, newOwnerPrefix);
        return TransferResult.OK;
    }

    private boolean transferOwnershipMysql(String guildName, UUID oldOwner, UUID newOwner) {
        if (!mysqlGuildsAvailable || guildName == null || oldOwner == null || newOwner == null) return false;

        try (Connection c = guildDataSource.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement guildPs = c.prepareStatement("UPDATE sandbox_guilds SET owner_uuid = ? WHERE guild_name = ?");
                 PreparedStatement memberPs = c.prepareStatement("INSERT INTO sandbox_guild_members (guild_name, player_uuid, rank_name) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE guild_name = VALUES(guild_name), rank_name = VALUES(rank_name)")) {

                guildPs.setString(1, newOwner.toString());
                guildPs.setString(2, guildName);
                if (guildPs.executeUpdate() == 0) {
                    c.rollback();
                    c.setAutoCommit(oldAutoCommit);
                    return false;
                }

                memberPs.setString(1, guildName);
                memberPs.setString(2, oldOwner.toString());
                memberPs.setString(3, "MEMBER");
                memberPs.addBatch();

                memberPs.setString(1, guildName);
                memberPs.setString(2, newOwner.toString());
                memberPs.setString(3, "OWNER");
                memberPs.addBatch();
                memberPs.executeBatch();

                c.commit();
                c.setAutoCommit(oldAutoCommit);
                return true;
            } catch (SQLException e) {
                c.rollback();
                c.setAutoCommit(oldAutoCommit);
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Guilds] Failed to transfer ownership of " + guildName + " in MySQL: " + e.getMessage());
            return false;
        }
    }

    public boolean createRank(UUID actor, String rankName, String prefix) {
        Guild g = guildOf(actor);
        if (g == null || !hasGuildPermission(actor, "rank.create")) return false;
        String key = rankName.toUpperCase(Locale.ROOT);
        if (key.equals("OWNER") || key.equals("MEMBER")) return false;
        g.createRank(key, prefix);
        save();
        return true;
    }

    public boolean deleteRank(UUID actor, String rankName) {
        Guild g = guildOf(actor);
        if (g == null || !hasGuildPermission(actor, "rank.delete")) return false;
        String key = rankName.toUpperCase(Locale.ROOT);
        if (key.equals("OWNER") || key.equals("MEMBER") || !g.rankExists(key)) return false;

        Map<UUID, String> oldPrefixes = oldPrefixesForRank(g, key);
        g.deleteRank(key);
        save();
        refreshGuildRankPrefixes(oldPrefixes);
        return true;
    }

    public enum SetRankResult { OK, NO_GUILD, NO_PERMISSION, TARGET_NOT_IN_GUILD, RANK_NOT_FOUND }
    public SetRankResult setRank(UUID actor, UUID target, String rankName) {
        Guild g = guildOf(actor);
        if (g == null) return SetRankResult.NO_GUILD;
        if (!hasGuildPermission(actor, "rank.set")) return SetRankResult.NO_PERMISSION;
        if (!g.isMember(target)) return SetRankResult.TARGET_NOT_IN_GUILD;
        String key = rankName.toUpperCase(Locale.ROOT);
        if (!g.rankExists(key)) return SetRankResult.RANK_NOT_FOUND;
        String oldPrefix = g.visiblePrefixFor(target);
        g.setMemberRank(target, key);
        save();
        refreshGuildRankPrefix(target, oldPrefix);
        return SetRankResult.OK;
    }

    public enum RankPermissionResult { OK, NO_GUILD, NO_PERMISSION, RANK_NOT_FOUND, INVALID_PERMISSION }
    public RankPermissionResult setRankPermission(UUID actor, String rankName, String permission, boolean allow) {
        Guild g = guildOf(actor);
        if (g == null) return RankPermissionResult.NO_GUILD;
        String needed = allow ? "rank.allow" : "rank.deny";
        if (!hasGuildPermission(actor, needed)) return RankPermissionResult.NO_PERMISSION;
        String normalized = normalizePermission(permission);
        if (!RANK_PERMISSIONS.contains(normalized)) return RankPermissionResult.INVALID_PERMISSION;
        if (!g.rankExists(rankName)) return RankPermissionResult.RANK_NOT_FOUND;
        if (allow) g.allowPermission(rankName, normalized);
        else g.denyPermission(rankName, normalized);
        save();
        return RankPermissionResult.OK;
    }

    public boolean setGuildTag(UUID actor, String newTag) {
        Guild g = guildOf(actor);
        if (g == null || !hasGuildPermission(actor, "tag")) return false;
        g.setTag(newTag);
        save();
        return true;
    }

    public boolean hasGuildPermission(UUID actor, String permission) {
        Guild g = guildOf(actor);
        return g != null && g.hasPermission(actor, normalizePermission(permission));
    }

    public Set<String> permissionsFor(UUID actor) {
        Guild g = guildOf(actor);
        return g == null ? Set.of() : g.permissionsFor(actor);
    }

    public boolean setHome(UUID actor, Location location) {
        Guild g = guildOf(actor);
        if (g == null || !hasGuildPermission(actor, "sethome") || location == null || location.getWorld() == null) return false;
        if (mysqlGuildsAvailable && !upsertHomeMysql(g.name(), location)) return false;
        g.setHome(location.clone());
        if (!mysqlGuildsAvailable) save();
        return true;
    }

    public Location homeFor(UUID actor) {
        Guild g = guildOf(actor);
        if (g == null) return null;
        if (g.home() != null) return g.home();
        Location loaded = loadHomeMysql(g.name());
        if (loaded != null) g.setHome(loaded);
        return loaded;
    }

    public Location publicHome(String guildName) {
        Guild g = getGuildByName(guildName);
        if (g == null || !g.isPublicHome()) return null;
        if (g.home() != null) return g.home();
        Location loaded = loadHomeMysql(g.name());
        if (loaded != null) g.setHome(loaded);
        return loaded;
    }

    public boolean setWarp(UUID actor, String warpName, Location location) {
        Guild g = guildOf(actor);
        if (g == null || warpName == null || warpName.isBlank() || location == null || location.getWorld() == null) return false;
        if (mysqlGuildsAvailable && !upsertWarpMysql(g.name(), warpName, location)) return false;
        g.setWarp(warpName, location.clone());
        if (!mysqlGuildsAvailable) save();
        return true;
    }

    public boolean deleteWarp(UUID actor, String warpName) {
        Guild g = guildOf(actor);
        if (g == null || !hasGuildPermission(actor, "delwarp") || warpName == null) return false;
        if (mysqlGuildsAvailable) {
            int mysqlDeleted = deleteWarpMysql(g.name(), warpName);
            if (mysqlDeleted < 0) return false;
            boolean memoryDeleted = g.deleteWarp(warpName);
            return memoryDeleted || mysqlDeleted > 0;
        }
        boolean memoryDeleted = g.deleteWarp(warpName);
        if (memoryDeleted) save();
        return memoryDeleted;
    }

    public Location warp(UUID actor, String warpName) {
        Guild g = guildOf(actor);
        if (g == null || warpName == null) return null;
        Location loc = g.warp(warpName);
        if (loc != null) return loc;
        Location loaded = loadWarpMysql(g.name(), warpName);
        if (loaded != null) g.setWarp(warpName, loaded);
        return loaded;
    }

    public Set<String> warpNames(UUID actor) {
        Guild g = guildOf(actor);
        if (g == null) return Set.of();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(g.warps().keySet());
        names.addAll(loadWarpNamesMysql(g.name()));
        return names;
    }


    public boolean adminSetHome(Guild g, Location location) {
        if (g == null || location == null || location.getWorld() == null) return false;
        if (mysqlGuildsAvailable && !upsertHomeMysql(g.name(), location)) return false;
        g.setHome(location.clone());
        if (!mysqlGuildsAvailable) save();
        return true;
    }

    public boolean adminDeleteWarp(Guild g, String warpName) {
        if (g == null || warpName == null) return false;
        if (mysqlGuildsAvailable) {
            int mysqlDeleted = deleteWarpMysql(g.name(), warpName);
            if (mysqlDeleted < 0) return false;
            boolean memoryDeleted = g.deleteWarp(warpName);
            return memoryDeleted || mysqlDeleted > 0;
        }
        boolean memoryDeleted = g.deleteWarp(warpName);
        if (memoryDeleted) save();
        return memoryDeleted;
    }

    public enum ToggleResult { OK, NO_GUILD, NO_PERMISSION, INVALID_TOGGLE }
    public ToggleResult toggle(UUID actor, String toggleName) {
        Guild g = guildOf(actor);
        if (g == null) return ToggleResult.NO_GUILD;
        if (!hasGuildPermission(actor, "toggle")) return ToggleResult.NO_PERMISSION;
        String key = toggleName.toLowerCase(Locale.ROOT);
        if (key.equals("open")) g.setOpen(!g.isOpen());
        else if (key.equals("public")) g.setPublicHome(!g.isPublicHome());
        else return ToggleResult.INVALID_TOGGLE;
        save();
        return ToggleResult.OK;
    }

    public Map<UUID, String> oldPrefixesForRank(Guild g, String rankName) {
        Map<UUID, String> oldPrefixes = new HashMap<>();
        if (g == null || rankName == null) return oldPrefixes;
        String key = rankName.toUpperCase(Locale.ROOT);
        for (var entry : g.members().entrySet()) {
            if (key.equalsIgnoreCase(entry.getValue())) {
                oldPrefixes.put(entry.getKey(), g.visiblePrefixFor(entry.getKey()));
            }
        }
        return oldPrefixes;
    }

    public void refreshGuildRankPrefixes(Map<UUID, String> oldPrefixes) {
        if (oldPrefixes == null || oldPrefixes.isEmpty()) return;
        for (var entry : oldPrefixes.entrySet()) {
            refreshGuildRankPrefix(entry.getKey(), entry.getValue());
        }
    }

    public void refreshGuildRankPrefix(UUID uuid, String oldPrefix) {
        if (uuid == null) return;
        Guild g = guildOf(uuid);
        String newPrefix = g == null ? null : g.visiblePrefixFor(uuid);
        String oldNormalized = normalizeStoredPrefix(oldPrefix);
        String newNormalized = normalizeStoredPrefix(newPrefix);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UserManager um = LuckPermsProvider.get().getUserManager();
                User user = um.loadUser(uuid).get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (user == null) return;

                String current = currentPrefixAtPriority(user);
                boolean currentIsGuildPrefix = current == null || current.isBlank()
                        || (oldNormalized != null && oldNormalized.equals(current));

                if (!currentIsGuildPrefix) {
                    return;
                }

                removePrefixNodesAtPriority(user.data());
                removePrefixNodesAtPriority(user.transientData());

                if (newNormalized != null && !newNormalized.isBlank()) {
                    user.data().add(PrefixNode.builder(newNormalized, TAG_PREFIX_PRIORITY).build());
                }

                user.getCachedData().invalidate();
                um.saveUser(user).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to refresh guild rank prefix for " + uuid + ": " + e.getMessage());
            }
        });
    }

    private String currentPrefixAtPriority(User user) {
        for (Node n : user.data().toCollection()) {
            if (n instanceof PrefixNode pn && pn.getPriority() == TAG_PREFIX_PRIORITY) return pn.getMetaValue();
        }
        for (Node n : user.transientData().toCollection()) {
            if (n instanceof PrefixNode pn && pn.getPriority() == TAG_PREFIX_PRIORITY) return pn.getMetaValue();
        }
        return null;
    }

    private int removePrefixNodesAtPriority(NodeMap map) {
        List<Node> toRemove = new ArrayList<>();
        for (Node n : map.toCollection()) {
            if (n instanceof PrefixNode pn && pn.getPriority() == TAG_PREFIX_PRIORITY) toRemove.add(n);
        }
        int removed = 0;
        for (Node n : toRemove) {
            DataMutateResult res = map.remove(n);
            if (res != null && res.wasSuccessful()) removed++;
        }
        return removed;
    }

    private String normalizeStoredPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return null;
        String out = HexColorUtil.translate(prefix);
        out = out.replaceAll("\\s+$", "");
        out = out.replaceAll("(?i)(?:&[0-9A-FK-OR]|§[0-9A-FK-OR])+$", "");
        out = out.replaceAll("(?i)(?:§x(§[0-9A-F]){6})+$", "");
        out = out.replaceAll("(?i)(?:&#[0-9A-F]{6})+$", "");
        return out + ChatColor.RESET;
    }

    public static String normalizePermission(String permission) {
        if (permission == null) return "";
        return permission.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
    }

    public String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }
}
