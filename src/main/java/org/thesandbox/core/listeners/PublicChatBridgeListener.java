package org.thesandbox.core.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.thesandbox.core.DiscordBridge;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.fun.Utils;

public class PublicChatBridgeListener implements Listener
{
    private final TheSandboxCore plugin;
    private final DiscordBridge bridge;

    public PublicChatBridgeListener(TheSandboxCore plugin, DiscordBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    // IMPORTANT: ignoreCancelled = true so plugins like ChatReaction can cancel chat before it mirrors to Discord
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (bridge == null || !bridge.isReady()) return;

        Player p = event.getPlayer();
        String msg = Utils.AdventureAPI(event.message());

        // Mirror to Discord (does not modify your in-game chat)
        bridge.sendPublicMessageFromMinecraft(p, msg);
    }
}