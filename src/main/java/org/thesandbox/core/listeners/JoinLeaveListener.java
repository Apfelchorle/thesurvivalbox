package org.thesandbox.core.listeners;

import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;
import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.fun.LoginMessages;
import org.thesandbox.core.managers.RankScoreboardManager;
import org.thesandbox.core.services.ShushService;
import org.thesandbox.core.util.PlayerDataKeys;
import org.thesandbox.core.util.PlayerDataListener;

public class JoinLeaveListener implements Listener {

    private final TheSandboxCore plugin;
    private final PlayerDataListener dataListener;
    private final LoginMessages loginMessages;
    private final RankScoreboardManager rankScoreboardManager;
    private final ShushService shushService;

    public JoinLeaveListener(TheSandboxCore plugin,
                             PlayerDataListener dataListener,
                             LoginMessages loginMessages,
                             RankScoreboardManager rankScoreboardManager,
                             ShushService shushService) {
        this.plugin = plugin;
        this.dataListener = dataListener;
        this.loginMessages = loginMessages;
        this.rankScoreboardManager = rankScoreboardManager;
        this.shushService = shushService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rankScoreboardManager.applyRankScoreboardTeam(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> rankScoreboardManager.applyRankScoreboardTeam(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerJoinMessage(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        boolean vanished = dataListener.get(p.getUniqueId(), PlayerDataKeys.VANISHED, false);

        event.setJoinMessage(vanished ? null : buildJoinMessageFor(p));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        boolean vanished = dataListener.get(p.getUniqueId(), PlayerDataKeys.VANISHED, false);

        event.setQuitMessage(vanished ? null : buildLeaveMessageFor(p));
    }

    @EventHandler
    public void onVanishHide(PlayerHideEvent event) {
        final Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            dataListener.set(p.getUniqueId(), PlayerDataKeys.VANISHED, true);
            Bukkit.broadcastMessage(buildLeaveMessageFor(p));

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
        Bukkit.getScheduler().runTask(plugin, () -> {
            dataListener.set(p.getUniqueId(), PlayerDataKeys.VANISHED, false);
            Bukkit.broadcastMessage(buildJoinMessageFor(p));
            loginMessages.SendLoginMessage(p);

            String staffMsg = ChatColor.translateAlternateColorCodes(
                    '&', "&8[&b&lSTAFF&8] &c" + p.getName() + " unvanished.");
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.hasPermission("sandbox.staff") && (shushService == null || !shushService.isEnabled(viewer.getUniqueId()))) {
                    viewer.sendMessage(staffMsg);
                }
            }
        });
    }

    private String buildJoinMessageFor(Player p) {
        if (VanishAPI.isInvisible(p)) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', "&a&lJoin &8» &7" + p.getName());
    }

    private String buildLeaveMessageFor(Player p) {
        return ChatColor.translateAlternateColorCodes('&', "&c&lLeave &8» &7" + p.getName());
    }
}