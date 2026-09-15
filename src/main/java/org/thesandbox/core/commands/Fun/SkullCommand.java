package org.thesandbox.core.commands.Fun;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SkullCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public SkullCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // Must be a player
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can use this command."));
            return true;
        }

        String targetName;

        if (args.length == 0) {
            // /skull → give your own head
            targetName = player.getName();
        } else {
            // /skull <username> → give that player's head
            targetName = args[0];
        }

        // Resolve target as OfflinePlayer for skin data
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        // Create player head
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GOLD + target.getName() + "'s Head");
            skull.setItemMeta(meta);
        }

        // Try to add to inventory
        PlayerInventory inv = player.getInventory();
        Map<Integer, ItemStack> leftovers = inv.addItem(skull);

        if (!leftovers.isEmpty()) {
            // Inventory full or partially full: drop at feet
            leftovers.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item)
            );
            player.sendMessage(CommandMessages.command(ChatColor.YELLOW + "Your inventory was full, so the skull was dropped at your feet."));
        } else {
            player.sendMessage(CommandMessages.command(ChatColor.GREEN + "You have been given " +
                    ChatColor.GOLD + target.getName() + "'s head" + ChatColor.GREEN + "."));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();

            Bukkit.getOnlinePlayers().forEach(p -> {
                String name = p.getName();
                if (name.toLowerCase().startsWith(prefix)) {
                    matches.add(name);
                }
            });

            Collections.sort(matches, String.CASE_INSENSITIVE_ORDER);
            return matches;
        }

        return Collections.emptyList();
    }
}
