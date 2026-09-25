package org.thesandbox.core.tags;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.thesandbox.core.util.HexColorUtil;
import org.thesandbox.core.guilds.Guild;
import org.thesandbox.core.guilds.GuildManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * If a player leaves (or joins) and they do NOT have a saved tag in MySQL,
 * remove any LuckPerms prefix nodes at our tag priority (clears NORMAL + TRANSIENT stores).
 */
public class TagCleanupListener implements Listener
{
    // Must match the priority used by your TagCommand when setting the LP prefix.
    private static final int PREFIX_PRIORITY = 200;

    private final Plugin plugin;
    private final TagService tagService;
    private final GuildManager guildManager;

    public TagCleanupListener(Plugin plugin, TagService tagService, GuildManager guildManager)
    {
        this.plugin = plugin;
        this.tagService = tagService;
        this.guildManager = guildManager;
    }

    public void register()
    {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        final String username = event.getPlayer().getName();
        final UUID uuid = event.getPlayer().getUniqueId();

        // Saved tag? Leave it alone.
        String saved = tagService.getSavedTag(username);
        if (saved != null && !saved.isBlank())
        {
            return;
        }

        cleanupUserPrefix(uuid, username, "quit");
    }

    // Safety net: if anything re-applied the tag on login, strip it again when no saved tag exists.
    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        final String username = event.getPlayer().getName();
        final UUID uuid = event.getPlayer().getUniqueId();

        String saved = tagService.getSavedTag(username);
        if (saved != null && !saved.isBlank())
        {
            ensureExpectedPrefix(uuid, username, saved, "saved tag");
            return;
        }

        String guildRankPrefix = getGuildRankPrefix(uuid);
        if (guildRankPrefix != null && !guildRankPrefix.isBlank())
        {
            ensureExpectedPrefix(uuid, username, guildRankPrefix, "guild rank prefix");
            return;
        }

        cleanupUserPrefix(uuid, username, "join");
    }


    /**
     * On join, make sure the LuckPerms prefix at our tag priority exactly matches
     * either the saved /tag value or, if there is no saved tag, the player's guild rank prefix.
     */
    private void ensureExpectedPrefix(UUID uuid, String username, String tagValue, String sourceName)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try
            {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                if (user == null)
                {
                    return;
                }

                String expected = normalizeTag(tagValue);
                String current = currentPrefixAtPriority(user);

                if (expected.equals(current))
                {
                    return;
                }

                removePrefixNodesAtPriority(user.data());
                removePrefixNodesAtPriority(user.transientData());
                user.data().add(PrefixNode.builder(expected, PREFIX_PRIORITY).build());
                user.getCachedData().invalidate();
                um.saveUser(user).get(5, TimeUnit.SECONDS);
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("[TagCleanup] Failed to sync " + sourceName + " for " + username + ": " + e.getMessage());
            }
        });
    }

    private String currentPrefixAtPriority(User user)
    {
        for (Node n : user.data().toCollection())
        {
            if (n instanceof PrefixNode pn && pn.getPriority() == PREFIX_PRIORITY)
            {
                return pn.getMetaValue();
            }
        }
        for (Node n : user.transientData().toCollection())
        {
            if (n instanceof PrefixNode pn && pn.getPriority() == PREFIX_PRIORITY)
            {
                return pn.getMetaValue();
            }
        }
        return null;
    }

    private String normalizeTag(String savedTag)
    {
        String out = HexColorUtil.translate(savedTag == null ? "" : savedTag);
        out = out.replaceAll("\\s+$", "");
        out = out.replaceAll("(?i)(?:&[0-9A-FK-OR]|§[0-9A-FK-OR])+$", "");
        out = out.replaceAll("(?i)(?:§x(§[0-9A-F]){6})+$", "");
        out = out.replaceAll("(?i)(?:&#[0-9A-F]{6})+$", "");
        return out + org.bukkit.ChatColor.RESET;
    }

    private String getGuildRankPrefix(UUID uuid)
    {
        try
        {
            if (guildManager == null) return null;
            Guild guild = guildManager.guildOf(uuid);
            if (guild == null) return null;
            String prefix = guild.visiblePrefixFor(uuid);
            return (prefix == null || prefix.isBlank()) ? null : prefix;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private void cleanupUserPrefix(UUID uuid, String username, String phase)
    {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try
            {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                if (user == null)
                {
                    return;
                }

                // NORMAL store
                int removedNormal = removePrefixNodesAtPriority(user.data());
                // TRANSIENT store
                int removedTransient = removePrefixNodesAtPriority(user.transientData());

                if (removedNormal > 0 || removedTransient > 0)
                {
                    // Invalidate cached meta so changes reflect immediately in chat
                    user.getCachedData().invalidate();

                    um.saveUser(user).get(5, TimeUnit.SECONDS);
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("[TagCleanup] Failed to clear non-saved tag for " + username + ": " + e.getMessage());
            }
        });
    }

    private int removePrefixNodesAtPriority(NodeMap map)
    {
        List<Node> toRemove = new ArrayList<>();
        for (Node n : map.toCollection())
        {
            if (n instanceof PrefixNode pn && pn.getPriority() == PREFIX_PRIORITY)
            {
                toRemove.add(n);
            }
        }

        int removed = 0;
        for (Node n : toRemove)
        {
            DataMutateResult res = map.remove(n);
            if (res != null && res.wasSuccessful())
            {
                removed++;
            }
        }
        return removed;
    }
}