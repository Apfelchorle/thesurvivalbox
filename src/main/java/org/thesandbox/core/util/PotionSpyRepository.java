package org.thesandbox.core.util;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PotionSpyRepository
{
    private final DataSource dataSource;

    public PotionSpyRepository(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    public void ensureSchema() throws SQLException
    {
        final String sql = """
            CREATE TABLE IF NOT EXISTS potion_spy (
              uuid CHAR(36) NOT NULL PRIMARY KEY,
              enabled TINYINT(1) NOT NULL,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement())
        {
            s.execute(sql);
        }
    }

    public void upsertState(UUID uuid, boolean enabled) throws SQLException
    {
        final String sql = """
            INSERT INTO potion_spy (uuid, enabled, updated_at)
            VALUES (?, ?, NOW())
            ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), updated_at = NOW();
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql))
        {
            ps.setString(1, uuid.toString());
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        }
    }

    public Boolean getState(UUID uuid) throws SQLException
    {
        final String sql = "SELECT enabled FROM potion_spy WHERE uuid = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql))
        {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return rs.getBoolean(1);
                }
                return null;
            }
        }
    }

    public Set<UUID> getAllEnabled() throws SQLException
    {
        final String sql = "SELECT uuid FROM potion_spy WHERE enabled = 1";
        Set<UUID> out = new HashSet<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
    }
}