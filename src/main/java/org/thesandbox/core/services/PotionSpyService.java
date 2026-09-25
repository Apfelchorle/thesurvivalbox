package org.thesandbox.core.services;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.thesandbox.core.util.PotionSpyRepository;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PotionSpyService implements Listener
{
    private final JavaPlugin plugin;
    private final PotionSpyRepository repo;

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<ThrownPotion>> recentlyThrownPotions = new ConcurrentHashMap<>();
    private final List<Map.Entry<ThrownPotion, Long>> allThrownPotions =
            Collections.synchronizedList(new ArrayList<>());

    // ------- Rank → tier mapping -------
    private enum Tier {
        DEFAULT(0), MOD(1), ADMIN(2), SRADMIN(3), SUPERUSER(4);
        final int v; Tier(int v){ this.v = v; }
    }

    private Tier getTier(Player p)
    {
        // Highest wins
        if (p == null) return Tier.DEFAULT;
        if (p.hasPermission("sandbox.superuser")) return Tier.SUPERUSER;
        if (p.hasPermission("sandbox.admin"))   return Tier.SRADMIN;
        if (p.hasPermission("sandbox.staff"))     return Tier.ADMIN;
        if (p.hasPermission("sandbox.staff"))       return Tier.MOD;
        return Tier.DEFAULT;
    }

    // resolve a PotionEffectType by trying multiple possible enum names (for cross-version compatibility)
    private static PotionEffectType tryNames(String... names)
    {
        for (String n : names)
        {
            PotionEffectType t = PotionEffectType.getByName(n);
            if (t != null) return t;
        }
        return null;
    }

    private static Set<PotionEffectType> buildBadEffects()
    {
        Set<PotionEffectType> set = new HashSet<>();
        Optional.ofNullable(tryNames("BLINDNESS")).ifPresent(set::add);
        Optional.ofNullable(tryNames("LEVITATION")).ifPresent(set::add);
        Optional.ofNullable(tryNames("CONFUSION", "NAUSEA")).ifPresent(set::add);
        Optional.ofNullable(tryNames("SLOW", "SLOWNESS")).ifPresent(set::add);
        Optional.ofNullable(tryNames("SLOW_DIGGING", "MINING_FATIGUE")).ifPresent(set::add);
        Optional.ofNullable(tryNames("HUNGER")).ifPresent(set::add);
        return Collections.unmodifiableSet(set);
    }

    private final Set<PotionEffectType> badPotionEffects = buildBadEffects();

    public PotionSpyService(JavaPlugin plugin, DataSource dataSource)
    {
        this.plugin = plugin;
        this.repo = new PotionSpyRepository(dataSource);

        // ensure schema + preload enabled users (async)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try
            {
                repo.ensureSchema();
                Set<UUID> pre = repo.getAllEnabled();
                if (!pre.isEmpty())
                {
                    Bukkit.getScheduler().runTask(plugin, () -> enabled.addAll(pre));
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("[PotionSpy] init failed: " + e.getMessage());
            }
        });

        // periodic summary every 2s
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (recentlyThrownPotions.isEmpty()) return;

            for (UUID shooterId : new ArrayList<>(recentlyThrownPotions.keySet()))
            {
                List<ThrownPotion> list = recentlyThrownPotions.remove(shooterId);
                if (list == null || list.isEmpty()) continue;

                Player shooter = Bukkit.getPlayer(shooterId);
                if (shooter == null) continue;

                ThrownPotion latest = list.get(list.size() - 1);
                int total = list.size();
                int troll = 0;
                for (ThrownPotion p : list) if (isTrollPotion(p)) troll++;

                String msg = ChatColor.DARK_GRAY + "[" + ChatColor.YELLOW + "PotionSpy" + ChatColor.DARK_GRAY + "] "
                        + ChatColor.RESET + shooter.getName() + " splashed "
                        + total + " " + (total == 1 ? "potion" : "potions")
                        + " at X:" + latest.getLocation().getBlockX()
                        + " Y:" + latest.getLocation().getBlockY()
                        + " Z:" + latest.getLocation().getBlockZ()
                        + " in world '" + latest.getWorld().getName() + "'"
                        + (troll > 0 ? ChatColor.RED + " (most likely troll " + (troll == 1 ? "potion" : "potions") + ")" : "")
                        + ChatColor.RESET + ".";

                // NEW: broadcast filtered by viewer/thrower tier
                broadcastToEnabled(msg, shooterId);
            }
        }, 40L, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event)
    {
        final UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try
            {
                Boolean state = repo.getState(uuid);
                if (Boolean.TRUE.equals(state))
                {
                    Bukkit.getScheduler().runTask(plugin, () -> enabled.add(uuid));
                }
                else if (state != null)
                {
                    Bukkit.getScheduler().runTask(plugin, () -> enabled.remove(uuid));
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("[PotionSpy] load state failed for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public boolean isEnabled(UUID uuid) { return enabled.contains(uuid); }

    public void setEnabled(UUID uuid, boolean state)
    {
        if (state) enabled.add(uuid); else enabled.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { repo.upsertState(uuid, state); }
            catch (Exception e) { plugin.getLogger().warning("[PotionSpy] save state failed: " + e.getMessage()); }
        });
    }

    public boolean toggle(UUID uuid)
    {
        boolean after = !isEnabled(uuid);
        setEnabled(uuid, after);
        return after;
    }

    // --------- UPDATED: tier-filtered broadcast ----------
    public void broadcastToEnabled(String message, UUID shooterId)
    {
        Player shooter = shooterId != null ? Bukkit.getPlayer(shooterId) : null;
        Tier shooterTier = getTier(shooter);

        for (UUID viewerId : enabled)
        {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) continue;

            Tier viewerTier = getTier(viewer);
            // viewer sees shooter if shooterTier <= viewerTier
            if (shooterTier.v <= viewerTier.v)
            {
                viewer.sendMessage(message);
            }
        }
    }
    // (Kept for any external usages; uses DEFAULT when shooter unknown)
    public void broadcastToEnabled(String message)
    {
        broadcastToEnabled(message, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) { handleThrownPotion(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLingeringPotionSplash(LingeringPotionSplashEvent event) { handleThrownPotion(event.getEntity()); }

    private void handleThrownPotion(ThrownPotion potion)
    {
        if (!(potion.getShooter() instanceof Player shooter)) return;

        recentlyThrownPotions.computeIfAbsent(shooter.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(potion);
        allThrownPotions.add(new AbstractMap.SimpleEntry<>(potion, System.currentTimeMillis()));

        List<ThrownPotion> list = recentlyThrownPotions.get(shooter.getUniqueId());
        if (list.size() > 128) list.remove(0);
        if (allThrownPotions.size() > 1024) allThrownPotions.remove(0);
    }

    public boolean isTrollPotion(ThrownPotion potion)
    {
        int bad = 0;
        for (PotionEffect eff : potion.getEffects())
        {
            if (badPotionEffects.contains(eff.getType()) && eff.getAmplifier() > 2 && eff.getDuration() > 200)
            {
                bad++;
            }
        }
        return bad > 0;
    }

    public List<Map.Entry<ThrownPotion, Long>> getPlayerThrownPotions(Player player)
    {
        List<Map.Entry<ThrownPotion, Long>> out = new ArrayList<>();
        synchronized (allThrownPotions)
        {
            for (Map.Entry<ThrownPotion, Long> e : allThrownPotions)
            {
                ThrownPotion p = e.getKey();
                if (p.getShooter() instanceof Player s && s.getUniqueId().equals(player.getUniqueId()))
                {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public List<Map.Entry<ThrownPotion, Long>> getAllThrownPotions()
    {
        synchronized (allThrownPotions)
        {
            return new ArrayList<>(allThrownPotions);
        }
    }
}