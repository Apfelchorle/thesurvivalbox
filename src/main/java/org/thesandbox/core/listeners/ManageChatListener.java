package org.thesandbox.core.listeners;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.thesandbox.core.services.ManageChatService;

/**
 * Cancels chat while muted and informs the speaker in red.
 * Uses legacy AsyncPlayerChatEvent for widest compat (Purpur/Paper still fires it).
 */
public class ManageChatListener implements Listener {
    private final ManageChatService service;

    public ManageChatListener(ManageChatService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent e) {
        if (!service.isMuted()) return;

        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.RED + "A staff member has muted the chat. You cannot speak at this time.");
    }
}