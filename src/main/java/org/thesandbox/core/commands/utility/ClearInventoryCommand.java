package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.ISubCommand;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class ClearInventoryCommand implements ISubCommand
{
    private final JavaPlugin plugin;
    private final DataSource dataSource;

    private static final long CONFIRM_WINDOW_MS = 15_000L;

    private final Map<UUID, Long> pendingConfirm = new HashMap<>();
    private final Set<UUID> confirmationDisabled = new HashSet<>();

    public ClearInventoryCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.dataSource = findDataSource(plugin);

        if (this.dataSource == null)
        {
            plugin.getLogger().warning("[ClearInventory] Could not find MySQL dataSource. Confirmation settings will not save.");
        }
        else
        {
            setupDatabaseAndLoad();
        }
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length == 0)
        {
            if (!(sender instanceof Player player))
            {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cConsole must specify a player or all. Usage: /" + label + " <player|all>"));
                return true;
            }

            if (isConfirmationDisabled(player))
            {
                clearInv(player);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7Your inventory has been cleared."));
                return true;
            }

            long now = System.currentTimeMillis();
            Long until = pendingConfirm.get(player.getUniqueId());

            if (until != null && until >= now)
            {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7Type /" + label + " confirm to clear your inventory."));
            }
            else
            {
                pendingConfirm.put(player.getUniqueId(), now + CONFIRM_WINDOW_MS);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7Are you sure? Type /" + label + " confirm within 15 seconds to clear your inventory."));
            }

            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&7&lCommand &8» &7You can disable this confirmation with /" + label + " confirm off&7."));
            return true;
        }

        if (args[0].equalsIgnoreCase("confirm"))
        {
            if (!(sender instanceof Player player))
            {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cConsole cannot confirm. Use /" + label + " <player|all>."));
                return true;
            }

            UUID uuid = player.getUniqueId();

            if (args.length >= 2)
            {
                if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("disable"))
                {
                    confirmationDisabled.add(uuid);
                    pendingConfirm.remove(uuid);
                    saveConfirmationDisabled(uuid, true);

                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7&lCommand &8» &7Inventory clear confirmation has been disabled."));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7&lCommand &8» &7Use /" + label + " confirm on to enable it again."));
                    return true;
                }

                if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("enable"))
                {
                    confirmationDisabled.remove(uuid);
                    pendingConfirm.remove(uuid);
                    saveConfirmationDisabled(uuid, false);

                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7&lCommand &8» &7Inventory clear confirmation has been enabled."));
                    return true;
                }

                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lUsage &8» &c/" + label + " confirm <on|off>"));
                return true;
            }

            if (isConfirmationDisabled(player))
            {
                clearInv(player);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7&lCommand &8» &7Your inventory has been cleared."));
                return true;
            }

            Long until = pendingConfirm.get(uuid);

            if (until == null || until < System.currentTimeMillis())
            {
                pendingConfirm.remove(uuid);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cNo pending confirmation. Use /" + label + " first."));
                return true;
            }

            pendingConfirm.remove(uuid);
            clearInv(player);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&7&lCommand &8» &7Your inventory has been cleared."));
            return true;
        }

        if (!sender.hasPermission("sandbox.staff"))
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lError &8» &cNo permission."));
            return true;
        }

        if (args[0].equalsIgnoreCase("all"))
        {
            for (Player target : Bukkit.getOnlinePlayers())
            {
                clearInv(target);
            }

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lServer &8» &c" + sender.getName() + " cleared all online players' inventories."));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);

        if (target == null)
        {
            String search = args[0].toLowerCase(Locale.ROOT);

            List<Player> matches = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(args[0]) || p.getName().toLowerCase(Locale.ROOT).startsWith(search))
                    .collect(Collectors.toList());

            if (matches.size() == 1)
            {
                target = matches.get(0);
            }
        }

        if (target == null)
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lError &8» &cPlayer not found: " + args[0]));
            return true;
        }

        clearInv(target);

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
        "&c&lServer &8» &c" + sender.getName() + " cleared " + target.getName() + "'s inventory."));
        return true;
    }

    private DataSource findDataSource(JavaPlugin plugin)
    {
        try
        {
            Field field = plugin.getClass().getDeclaredField("dataSource");
            field.setAccessible(true);

            Object value = field.get(plugin);

            if (value instanceof DataSource)
            {
                return (DataSource) value;
            }
        }
        catch (ReflectiveOperationException ex)
        {
            plugin.getLogger().warning("[ClearInventory] Failed to access dataSource field: " + ex.getMessage());
        }

        return null;
    }

    private void setupDatabaseAndLoad()
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            String sql = "CREATE TABLE IF NOT EXISTS clearinventory_confirmations (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY" +
                    ")";

            Set<UUID> loaded = new HashSet<>();

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement())
            {
                stmt.executeUpdate(sql);

                try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM clearinventory_confirmations");
                     ResultSet rs = ps.executeQuery())
                {
                    while (rs.next())
                    {
                        try
                        {
                            loaded.add(UUID.fromString(rs.getString("uuid")));
                        }
                        catch (IllegalArgumentException ignored)
                        {
                        }
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () ->
                {
                    confirmationDisabled.clear();
                    confirmationDisabled.addAll(loaded);
                });
            }
            catch (SQLException ex)
            {
                plugin.getLogger().severe("[ClearInventory] Failed to setup/load MySQL confirmation settings.");
                ex.printStackTrace();
            }
        });
    }

    private void saveConfirmationDisabled(UUID uuid, boolean disabled)
    {
        if (dataSource == null)
        {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
        {
            try (Connection conn = dataSource.getConnection())
            {
                if (disabled)
                {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT IGNORE INTO clearinventory_confirmations (uuid) VALUES (?)"))
                    {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                }
                else
                {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM clearinventory_confirmations WHERE uuid = ?"))
                    {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                }
            }
            catch (SQLException ex)
            {
                plugin.getLogger().severe("[ClearInventory] Failed to save confirmation setting for " + uuid + ".");
                ex.printStackTrace();
            }
        });
    }

    private boolean isConfirmationDisabled(Player player)
    {
        return confirmationDisabled.contains(player.getUniqueId());
    }

    private void clearInv(Player player)
    {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> out = new ArrayList<>();

        if (args.length == 1)
        {
            String pref = args[0].toLowerCase(Locale.ROOT);

            if ("confirm".startsWith(pref))
            {
                out.add("confirm");
            }

            if (sender.hasPermission("sandbox.staff"))
            {
                if ("all".startsWith(pref))
                {
                    out.add("all");
                }

                for (Player player : Bukkit.getOnlinePlayers())
                {
                    String name = player.getName();

                    if (name.toLowerCase(Locale.ROOT).startsWith(pref))
                    {
                        out.add(name);
                    }
                }
            }

            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("confirm"))
        {
            String pref = args[1].toLowerCase(Locale.ROOT);

            if ("on".startsWith(pref))
            {
                out.add("on");
            }

            if ("off".startsWith(pref))
            {
                out.add("off");
            }

            return out;
        }

        return Collections.emptyList();
    }
}