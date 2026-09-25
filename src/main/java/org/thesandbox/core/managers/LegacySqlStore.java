package org.thesandbox.core.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the legacy MySQL connection pool used for commandspy/staffchat persistence.
 */
public class LegacySqlStore {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public LegacySqlStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        close();

        String host = plugin.getConfig().getString("mysql.host");
        int port = plugin.getConfig().getInt("mysql.port");
        String database = plugin.getConfig().getString("mysql.database");
        String user = plugin.getConfig().getString("mysql.user");
        String password = plugin.getConfig().getString("mysql.password");

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

            try {
                stmt.executeUpdate("ALTER TABLE staffchat ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE");
            } catch (SQLException e) {
                try {
                    stmt.executeUpdate("ALTER TABLE staffchat ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE");
                } catch (SQLException ignore) {
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not initialize database!");
            e.printStackTrace();
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }
}