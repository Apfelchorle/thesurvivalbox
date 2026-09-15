package org.thesandbox.core.commands.utility;

import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

public class DeafenCommand implements ISubCommand
{
    private static final double STEPS = 10.0;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private final JavaPlugin plugin;

    public DeafenCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {

        Player player = (Player) sender;

        // Schedule a short sweep of random sounds around the player
        for (double percent = 0.0; percent <= 1.00001; percent += (1.0 / STEPS))
        {
            final float pitch = (float)(percent * 2.0); // 0.0 -> 2.0
            final Location playAt = randomOffset(player.getLocation());

            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        // Pick a random valid sound and play it loudly nearby
                        Sound sound = randomSound();
                        player.playSound(playAt, sound, 100.0f, pitch);
                    }
                    catch (Throwable ignored)
                    {
                        // If a sound constant is invalid on some versions, ignore and continue
                    }
                }
            }.runTaskLater(plugin, Math.round(20.0 * percent * 2.0)); // up to ~2 seconds
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }

    /* ---------------- internals ---------------- */

    private static Location randomOffset(Location base)
    {
        // Offset within roughly a 5-block cube around the player
        double dx = randomUnit() * 5.0;
        double dy = randomUnit() * 5.0;
        double dz = randomUnit() * 5.0;
        return base.clone().add(dx, dy, dz);
    }

    /** Returns a double in [-1.0, 1.0]. */
    private static double randomUnit()
    {
        // Correct symmetric range: [-1, 1]
        return -1.0 + (RANDOM.nextDouble() * 2.0);
    }

    private static final Sound[] CACHED_SOUNDS = Registry.SOUNDS.stream().toArray(Sound[]::new);

    private static Sound randomSound()
    {
        return CACHED_SOUNDS[RANDOM.nextInt(CACHED_SOUNDS.length)];
    }
}