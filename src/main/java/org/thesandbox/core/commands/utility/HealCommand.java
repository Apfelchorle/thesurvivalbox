package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HealCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public HealCommand(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        // Console must specify a target
        if (!(sender instanceof Player) && args.length < 1)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <player>"));
            return true;
        }

        if (args.length == 0)
        {
            // Self-heal: always allowed (no extra message here; healOne will notify)
            Player self = (Player) sender;
            healOne(sender, self, /*tellTarget=*/true, /*tellSender=*/false);
            return true;
        }

        // Healing someone else requires sandbox.staff (console always allowed)
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null)
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Player not found: " + args[0]));
            return true;
        }

        if (sender instanceof Player actor)
        {
            if (!actor.hasPermission("sandbox.staff") && !actor.isOp())
            {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "You must be a staff member to heal other players!"));
                return true;
            }
        }

        boolean ok = healOne(sender, target, /*tellTarget=*/true, /*tellSender=*/true);
        if (ok && !(sender instanceof Player && sender.equals(target)))
        {
            sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Healed " + target.getName() + "."));
        }
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
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }

    /* ---------------- internals ---------------- */

    private boolean healOne(CommandSender actor, Player target, boolean tellTarget, boolean tellSender)
    {
        if (target.isDead() || target.getHealth() <= 0.0D)
        {
            if (tellSender) actor.sendMessage(CommandMessages.error(ChatColor.RED + target.getName() + " cannot be healed while dead."));
            return false;
        }

        double max = getMaxHealth(target);
        double needed = Math.max(0.0D, max - target.getHealth());

        EntityRegainHealthEvent event = new EntityRegainHealthEvent(target, needed, EntityRegainHealthEvent.RegainReason.CUSTOM);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
        {
            return false;
        }

        double newHealth = Math.min(max, target.getHealth() + event.getAmount());
        try { target.setHealth(newHealth); } catch (Throwable ignored) {}

        try { target.setFoodLevel(20); } catch (Throwable ignored) {}
        try { target.setSaturation(20.0f); } catch (Throwable ignored) {}
        try { target.setFireTicks(0); } catch (Throwable ignored) {}
        try { target.setRemainingAir(target.getMaximumAir()); } catch (Throwable ignored) {}

        try {
            for (PotionEffect effect : target.getActivePotionEffects())
            {
                target.removePotionEffect(effect.getType());
            }
        } catch (Throwable ignored) {}

        if (tellTarget) target.sendMessage(CommandMessages.command(ChatColor.GREEN + "You have been healed!"));

        return true;
    }

    private double getMaxHealth(Player p)
    {
        try
        {
            if (p.getAttribute(Attribute.MAX_HEALTH) != null)
            {
                return p.getAttribute(Attribute.MAX_HEALTH).getValue();
            }
        }
        catch (Throwable ignored) {}
        return 20.0D;
    }
}