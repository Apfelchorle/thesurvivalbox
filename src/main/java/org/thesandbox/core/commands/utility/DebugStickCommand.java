package org.thesandbox.core.commands.utility;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DebugStickCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public DebugStickCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "This command can only be used in-game."));
            return true;
        }

        // Build the item (DEBUG_STICK if present, else STICK)
        Material mat = getDebugStickMaterial();
        ItemStack item = new ItemStack(mat, 1);

        // Always apply meta, regardless of which material we used
        ItemMeta meta = item.getItemMeta();
        if (meta != null)
        {
            if (hasAdventureMeta(meta)) {
                // Paper / Adventure path
                meta.displayName(Component.text("Debug Stick", NamedTextColor.LIGHT_PURPLE));
                meta.lore(Arrays.asList(
                    Component.text("Behold: The DEBUG STICK!", NamedTextColor.GOLD),
                    Component.text("Left click to select the block to change.", NamedTextColor.YELLOW),
                    Component.text("Right click to change its properties!", NamedTextColor.RED)
                ));
            } else {
                // Spigot / legacy path
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Debug Stick");
                meta.setLore(Arrays.asList(
                    ChatColor.GOLD + "Behold, the DEBUG STICK!",
                    ChatColor.YELLOW + "Left click to select the block to change.",
                    ChatColor.RED + "Right click to change its properties!"
                ));
            }
            item.setItemMeta(meta);
        }

        // Give to player (drop if full)
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty())
        {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(CommandMessages.command(ChatColor.RED + "Your inventory is full; dropped the item at your feet."));
        }
        else
        {
            player.sendMessage(CommandMessages.command(ChatColor.GREEN + "You received a Debug Stick."));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }

    private Material getDebugStickMaterial()
    {
        try {
            return Material.valueOf("DEBUG_STICK");
        } catch (IllegalArgumentException ex) {
            return Material.STICK; // fallback for older versions
        }
    }

    /**
     * Detect whether Paper/Adventure item meta methods are available at runtime.
     * We check for the presence of the 'displayName(Component)' method.
     */
    private boolean hasAdventureMeta(ItemMeta meta)
    {
        try {
            // If this method exists, we're on Paper (or an API that exposes it)
            meta.getClass().getMethod("displayName", Component.class);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}