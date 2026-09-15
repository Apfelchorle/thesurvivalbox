package org.thesandbox.core.commands.Fun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.fun.items.*;
import org.thesandbox.core.fun.items.itemUTILS.Item;
import org.thesandbox.core.util.PlayerDataListener;

import java.util.*;

public class ShopCommand implements Listener, ISubCommand {

    private final LightningRodItem lightningRodItem;
    private final PlayerDataListener playerDataListener;
    private final LoginMessagesItem loginMessagesItem;
    private final ClownFishItem clownFishItem;
    private final Rideable_Ender_Pearl_Item rideableEnderPearlItem;
    private final GrapplingHookItem grapplingHookItem;
    private final StackingPotatoItem stackingPotatoItem;
    private final FloatBoatItem floatBoatItem;
    private final WindRodItem windRodItem;

    private record ShopEntry(int slot, Item item) {}
    private List<ShopEntry> shopLayout() {
        return List.of(
                new ShopEntry(3, stackingPotatoItem),
                new ShopEntry(4, grapplingHookItem),
                new ShopEntry(5, windRodItem),
                new ShopEntry(11, lightningRodItem),
                new ShopEntry(12, floatBoatItem),
                new ShopEntry(13, loginMessagesItem),
                new ShopEntry(14, clownFishItem),
                new ShopEntry(15, rideableEnderPearlItem)
        );
    }

    private static class ShopHolder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

    }
    private final Component shoptitle = Component.text("The Shop", NamedTextColor.DARK_GREEN);
    private final Map<Integer, Item> shopSlots = new HashMap<>();


    public ShopCommand(TheSandboxCore plugin, PlayerDataListener playerDataListener, LightningRodItem lightningRodItem, LoginMessagesItem loginMessagesItem, ClownFishItem clownFishItem, Rideable_Ender_Pearl_Item rideableEnderPearlItem, GrapplingHookItem grapplingHookItem, StackingPotatoItem stackingPotatoItem, FloatBoatItem floatBoatItem, WindRodItem windRodItem) {
        this.playerDataListener = playerDataListener;
        this.lightningRodItem = lightningRodItem;
        this.loginMessagesItem = loginMessagesItem;
        this.clownFishItem = clownFishItem;
        this.rideableEnderPearlItem = rideableEnderPearlItem;
        this.grapplingHookItem = grapplingHookItem;
        this.stackingPotatoItem = stackingPotatoItem;
        this.floatBoatItem = floatBoatItem;
        this.windRodItem = windRodItem;

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @SuppressWarnings("unchecked")
    private <T extends Item> T findItem(TheSandboxCore plugin, Class<T> clazz) {
        for (Item item : plugin.getRegisteredItems()) {
            if (clazz.isInstance(item)) {
                return (T) item;
            }
        }
        throw new IllegalStateException("[TheSandboxCore] Required shop item " + clazz.getSimpleName() + " was not auto-registered!");
    }


    // Buy Logic [Called By Click Logic]
    private void buy(Player player, Item item, String itemName) {
        List<String> owned_statuses = Arrays.asList("owned", "bought", "enabled", "disabled");
        int price = item.getPrice();
        UUID puuid = player.getUniqueId();
        int balance = playerDataListener.getCoins(puuid);
        String item_status = playerDataListener.get(puuid,itemName, "not_owned");

        if (owned_statuses.contains(item_status.toLowerCase())) {
            var leftover = player.getInventory().addItem(item.create());

            if (!leftover.isEmpty()) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(Component.text("Your Inventory is full!", NamedTextColor.RED));
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1f);
            player.sendMessage(Component.text("You received a " + itemName + "!", NamedTextColor.YELLOW));
            return;
        }

        if (balance < price) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(Component.text("You Do Not Have Enough Coins.", NamedTextColor.DARK_RED));
            player.sendMessage(Component.text("You Need " + (price - balance) + " More Coins!", NamedTextColor.DARK_RED));
            return;
        }

        var leftover = player.getInventory().addItem(item.create());

        if (!leftover.isEmpty()) {
            player.sendMessage(Component.text("Your Inventory is full!", NamedTextColor.RED));
            return;
        }

        playerDataListener.set(player.getUniqueId(),itemName, "owned");
        playerDataListener.setCoins(player.getUniqueId(), playerDataListener.getCoins(player.getUniqueId()) - item.getPrice());

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        player.sendMessage(Component.text("You received a " + itemName + "!", NamedTextColor.YELLOW));

        }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) {
            return;
        }

        if (event.getClickedInventory() == null || (!(event.getClickedInventory().getHolder() instanceof ShopHolder))) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();



        // Click Logic
        Item item = shopSlots.get(slot);
        if (item != null) {
            buy(player, item, item.getName());
        }


        event.setCancelled(true);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players may execute this command.", NamedTextColor.RED));
            return true;
        }

        ShopHolder holder = new ShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, 9 * 3, shoptitle);
        holder.setInventory(inventory);

        // as shown above the Inventory Menu is 9 * 3 which is 27 slots

        for (ShopEntry entry : shopLayout()) {
            shopSlots.put(entry.slot(), entry.item());
            inventory.setItem(entry.slot(), shopIcon(player, entry.item()));
        }


        player.openInventory(inventory);

        return true;
    }

    private ItemStack shopIcon(Player player, Item item) {
        ItemStack stack = item.create();
        String status = playerDataListener.get(player.getUniqueId(), item.getName(), "not_owned");
        List<String> ownedStatuses = Arrays.asList("owned", "bought", "enabled", "disabled");
        if (ownedStatuses.contains(status.toLowerCase())) {
            return stack;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7Price: &e" + item.getPrice()));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack getItem(ItemStack item, String name, String ... lore) {
        ItemMeta meta = item.getItemMeta();

        // meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));

        List<Component> loreList = new ArrayList<>();

        for (String s : lore) {
            // loreList.add(ChatColor.translateAlternateColorCodes('&', s));
            loreList.add(LegacyComponentSerializer.legacyAmpersand().deserialize(s));
        }
        meta.lore(loreList);

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
