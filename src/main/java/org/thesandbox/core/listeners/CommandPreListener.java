package org.thesandbox.core.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.thesandbox.core.DiscordBridge;
import org.thesandbox.core.managers.CommandBlockManager;
import org.thesandbox.core.managers.CommandSpyManager;
import org.thesandbox.core.services.ShushService;

import java.util.function.Supplier;

public class CommandPreListener implements Listener {

    private final CommandBlockManager commandBlockManager;
    private final CommandSpyManager commandSpyManager;
    private final Supplier<ShushService> shushServiceSupplier;
    private final Supplier<DiscordBridge> discordSupplier;
    private final org.thesandbox.core.TheSandboxCore plugin;

    public CommandPreListener(org.thesandbox.core.TheSandboxCore plugin,
                              CommandBlockManager commandBlockManager,
                              CommandSpyManager commandSpyManager,
                              Supplier<ShushService> shushServiceSupplier,
                              Supplier<DiscordBridge> discordSupplier) {
        this.plugin = plugin;
        this.commandBlockManager = commandBlockManager;
        this.commandSpyManager = commandSpyManager;
        this.shushServiceSupplier = shushServiceSupplier;
        this.discordSupplier = discordSupplier;
    }

    private CmdTier getCmdTier(Player p) {
        if (p == null) return CmdTier.DEFAULT;
        if (p.hasPermission("sandbox.superuser")) return CmdTier.SUPERUSER;
        if (p.hasPermission("sandbox.admin")) return CmdTier.SRADMIN;
        if (p.hasPermission("sandbox.staff")) return CmdTier.ADMIN;
        if (p.hasPermission("sandbox.mod")) return CmdTier.MOD; // fixed: was checking sandbox.staff twice
        return CmdTier.DEFAULT;
    }

    @EventHandler
    public void onCommandPre(PlayerCommandPreprocessEvent event) {
        final Player sender = event.getPlayer();
        final ShushService shushService = shushServiceSupplier.get();

        if (commandBlockManager.isCommandsBlocked(sender)) {
            sender.sendMessage(ChatColor.RED + "Your commands are currently blocked.");
            event.setCancelled(true);
            return;
        }

        final String rawMessage = event.getMessage();

        final String spyCommand = commandSpyManager.spyDisplayCommand(rawMessage);
        String fmt = plugin.getConfig()
                .getString("commandspy.format",
                        sender.getServer().getPluginManager().getPlugin("TheSandboxCore").getConfig()
                                .getString("commandspy-format", "&7%player%: %command%"));
        String formatted = ChatColor.translateAlternateColorCodes('&', fmt)
                .replace("%player%", sender.getName())
                .replace("%command%", spyCommand);

        CmdTier senderTier = getCmdTier(sender);
        for (Player viewer : sender.getServer().getOnlinePlayers()) {
            if (!viewer.hasPermission("sandbox.staff")) continue;
            if (shushService != null && shushService.isEnabled(viewer.getUniqueId())) continue;
            if (commandSpyManager.isCmdSpyDisabled(viewer.getUniqueId())) continue;
            if (viewer == sender) continue;
            CmdTier viewerTier = getCmdTier(viewer);
            if (senderTier.v <= viewerTier.v) {
                viewer.sendMessage(formatted);
            }
        }

        try {
            String raw = rawMessage.trim();
            if (raw.startsWith("/")) raw = raw.substring(1);
            String[] parts = raw.split("\\s+");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase("tpo")) {
                String clickedName = parts[1];
                DiscordBridge bridge = discordSupplier.get();
                if (bridge != null && bridge.isReady()) {
                    bridge.tryArchiveRecentForPlayer(clickedName, sender.getName());
                }
            }
        } catch (Exception ignore) {
        }
    }

    public enum CmdTier {
        DEFAULT(0), MOD(1), ADMIN(2), SRADMIN(3), SUPERUSER(4);
        final int v;

        CmdTier(int v) {
            this.v = v;
        }
    }
}