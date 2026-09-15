package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntityWipeCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public EntityWipeCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        EntityType targetType = null;
        boolean wipeAll = false;

        if (args.length == 0) {
            // /entitywipe → wipe ALL non-player entities
            wipeAll = true;
        } else {
            // /entitywipe <entity> → wipe only that entity type
            try {
                targetType = EntityType.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + args[0] + " is not a valid entity type."));
                return true;
            }

            if (targetType == EntityType.PLAYER) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player entities cannot be wiped."));
                return true;
            }
        }

        // Broadcast action line in red
        final String who = (sender instanceof Player) ? sender.getName() : "CONSOLE";
        final String noun = wipeAll
                ? "entities"
                : prettify(targetType.name()) + "s";

        Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + who + " - Wiping all " + noun));

        // Perform the wipe
        int removed = wipeAll
                ? wipeAllEntities()
                : wipeEntitiesOfType(targetType);

        // Singular/plural message back to sender
        if (removed == 1) {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "1 " + (wipeAll ? "entity" : prettify(targetType.name())) + " removed."));
        } else {
            sender.sendMessage(CommandMessages.command("" + ChatColor.GRAY + removed + " " + (wipeAll ? "entities" : prettify(targetType.name()) + "s") + " removed."));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1) {
            String prefix = args[0].toUpperCase();
            List<String> out = new ArrayList<>(getAllEntityNames());
            out.removeIf(s -> !s.startsWith(prefix));
            return out;
        }
        return Collections.emptyList();
    }

    /* ===================== helpers ===================== */

    /** List of all entity names (except PLAYER/UNKNOWN) for tab-complete. */
    private static List<String> getAllEntityNames()
    {
        List<String> names = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            if (t == null) continue;
            if (t == EntityType.PLAYER || t == EntityType.UNKNOWN) continue;
            names.add(t.name());
        }
        Collections.sort(names);
        return names;
    }

    /** Removes all non-player entities in all worlds. */
    private int wipeAllEntities()
    {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Player) continue;
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Removes all entities of a specific type (never players). */
    private int wipeEntitiesOfType(EntityType type)
    {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            Class<? extends Entity> clazz = getEntityClassSafe(type);
            if (clazz != null) {
                for (Entity e : w.getEntitiesByClass(clazz)) {
                    if (e instanceof Player) continue;
                    if (e.getType() == type) {
                        e.remove();
                        removed++;
                    }
                }
            } else {
                for (Entity e : w.getEntities()) {
                    if (e.getType() == type && !(e instanceof Player)) {
                        e.remove();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private static Class<? extends Entity> getEntityClassSafe(EntityType type)
    {
        try {
            return type.getEntityClass();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String prettify(String enumName)
    {
        String lower = enumName.toLowerCase().replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}