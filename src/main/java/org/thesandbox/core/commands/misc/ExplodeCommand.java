package org.thesandbox.core.commands.misc;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExplodeCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public ExplodeCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length < 1)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player not found: " + args[0]));
            return true;
        }

        launchUpThenExplode(sender, target);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
            {
                if (p.getName().toLowerCase().startsWith(prefix))
                {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    /* ---------------- internals ---------------- */

    private void launchUpThenExplode(CommandSender executor, Player target)
    {
        final int periodTicks = 2;             // every 2 ticks
        final int steps = 7;                   // ~0.7 seconds of ascent
        final double initialBoost = 0.9;       // smaller initial kick
        final double sustainBoost = 0.35;      // sustain each tick
        final double maxXZDamp = 0.85;         // damp horizontal motion

        safeDisableFlight(target);
        target.setVelocity(new Vector(0, initialBoost, 0));

        playSoundSafe(target, target.getLocation(), fireworkLaunchSound(), 1.0f, 1.0f);

        new BukkitRunnable()
        {
            int i = 0;

            @Override
            public void run()
            {
                if (!target.isOnline() || target.isDead())
                {
                    cancel();
                    return;
                }

                if (i < steps)
                {
                    Vector curr = target.getVelocity();
                    Vector straightUp = new Vector(0, sustainBoost, 0);
                    curr.setX(curr.getX() * maxXZDamp);
                    curr.setZ(curr.getZ() * maxXZDamp);
                    target.setVelocity(curr.add(straightUp));

                    Location l = target.getLocation().clone().add(0, 0.5, 0);
                    spawnParticleSafe(l, fireworkTrailParticle(), 14, 0.2, 0.2, 0.2, 0.01);
                    spawnParticleSafe(l, smokeTrailParticle(), 8, 0.3, 0.3, 0.3, 0.01);

                    i++;
                    return;
                }

                // Explosion phase
                Location boomAt = target.getLocation();
                try { target.getWorld().createExplosion(boomAt, 0.0f, false, false, null); } catch (Throwable ignored) {
                    try { target.getWorld().createExplosion(boomAt, 0.0f, false, false); } catch (Throwable ignored2) {
                        try { target.getWorld().createExplosion(boomAt, 0.0f, false); } catch (Throwable ignored3) {}
                    }
                }
                // Bigger visual explosion
                spawnParticleSafe(boomAt, bigExplosionParticle(), 60, 1.5, 1.5, 1.5, 0.1);
                playSoundSafe(target, boomAt, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.9f);

                try { target.setHealth(0.0D); }
                catch (Throwable ignored) {
                    try { target.damage(1000.0, (executor instanceof Entity) ? (Entity) executor : null); }
                    catch (Throwable ignored2) { target.damage(1000.0); }
                }

                executor.sendMessage(CommandMessages.command(ChatColor.GRAY + "Exploded " + target.getName() + "."));

                cancel();
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }

    private void safeDisableFlight(Player p)
    {
        try { p.setAllowFlight(false); } catch (Throwable ignored) {}
        try { p.setFlying(false); } catch (Throwable ignored) {}
        try { p.setGliding(false); } catch (Throwable ignored) {}
    }

    /* ----- version-safe helpers ----- */

    private Particle bigExplosionParticle()
    {
        String[] candidates = { "EXPLOSION_HUGE", "EXPLOSION_LARGE", "EXPLOSION_EMITTER", "EXPLOSION" };
        for (String n : candidates) {
            try { return Particle.valueOf(n); } catch (IllegalArgumentException ignored) {}
        }
        return Particle.CLOUD;
    }

    private Particle fireworkTrailParticle()
    {
        String[] candidates = { "FIREWORKS_SPARK", "FIREWORK", "CRIT" };
        for (String n : candidates) {
            try { return Particle.valueOf(n); } catch (IllegalArgumentException ignored) {}
        }
        return Particle.CRIT;
    }

    private Particle smokeTrailParticle()
    {
        String[] candidates = { "SMOKE_NORMAL", "SMALL_SMOKE", "SMOKE", "POOF" };
        for (String n : candidates) {
            try { return Particle.valueOf(n); } catch (IllegalArgumentException ignored) {}
        }
        return Particle.CLOUD;
    }

    private Sound fireworkLaunchSound()
    {
        String[] candidates = {"entity.firework_rocket.launch", "entity.firework.launch", "firework.launch"};
        for (String n : candidates) {
            NamespacedKey key = NamespacedKey.minecraft(n);
            Sound sound = Registry.SOUNDS.get(key);
            try {
                return sound;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Sound.ENTITY_BAT_TAKEOFF;
    }

    private void spawnParticleSafe(Location loc, Particle p, int count, double ox, double oy, double oz, double extra)
    {
        try { loc.getWorld().spawnParticle(p, loc, count, ox, oy, oz, extra); }
        catch (Throwable ignored) {}
    }

    private void playSoundSafe(Player ctx, Location loc, Sound s, float vol, float pitch)
    {
        try { loc.getWorld().playSound(loc, s, vol, pitch); }
        catch (Throwable ignored) {}
    }
}