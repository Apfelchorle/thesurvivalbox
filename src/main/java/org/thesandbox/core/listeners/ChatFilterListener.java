package org.thesandbox.core.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.thesandbox.core.fun.Utils;

public class ChatFilterListener implements Listener {

    private final ChatFilterEngine engine;

    public ChatFilterListener(ChatFilterEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
//        if (sender.hasPermission(RankKeys.STAFF)) return;

        String plain = Utils.plainText(event.message());
        ChatFilterEngine.Result result = engine.scan(plain);

        if (result.triggered) {
            Component staffAlert = Component.text(
                    "[ChatFilter] " + sender.getName() + " triggered the chat filter. Original: " + plain,
                    NamedTextColor.DARK_GRAY);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("sandbox.staff")) {
                    staff.sendMessage(staffAlert);
                }
            }
        }
    }
}