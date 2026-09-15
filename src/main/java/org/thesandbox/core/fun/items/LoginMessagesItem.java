package org.thesandbox.core.fun.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.fun.LoginMessages;
import org.thesandbox.core.fun.items.itemUTILS.Item;
import org.thesandbox.core.fun.items.itemUTILS.ItemKeys;
import org.thesandbox.core.util.PlayerDataKeys;
import org.thesandbox.core.util.PlayerDataListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginMessagesItem implements Item {


    private final TheSandboxCore plugin;

    private final ItemKeys keys;

    private final long cooldownMs;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final String NAME = PlayerDataKeys.LOGIN_MESSAGES_STATE;
    private final PlayerDataListener playerDataListener;
    private final LoginMessages loginMessages;

    public LoginMessagesItem(TheSandboxCore plugin, ItemKeys keys, PlayerDataListener playerDataListener, LoginMessages loginMessages) {
        this.plugin = plugin;
        this.keys = keys;
        this.cooldownMs = plugin.getConfig().getLong("items.loginMessages.cooldown", 1500);
        this.playerDataListener = playerDataListener;
        this.loginMessages = loginMessages;
    }


    @Override
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(NAME, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(Component.text("Get your very own personalized login message!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)));
        meta.getPersistentDataContainer().set(keys.loginMessages, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean matches(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.loginMessages, PersistentDataType.BYTE);
    }

    @Override
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        cooldowns.put(p.getUniqueId(), now);

        String current = loginMessages.GetLoginMessagesState(p);

        if (current.equalsIgnoreCase("not_owned")) { return; }

        String next;
        if (current.equalsIgnoreCase("enabled")) {
            next = "disabled";
        } else {
            next = "enabled";
        }

        loginMessages.SetLoginMessagesState(p, next);


        if (next.equals("enabled")) {
            p.sendMessage(Component.text("Login message enabled!", NamedTextColor.GREEN));
        } else {
            p.sendMessage(Component.text("Login message disabled.", NamedTextColor.RED));
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPrice() {
        return plugin.getConfig().getInt("items.loginMessages.price", 250);
    }
}
