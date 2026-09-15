package org.thesandbox.core.commands.Fun;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Collections;
import java.util.List;

public class GlowCommand implements ISubCommand
{
    private final JavaPlugin plugin;

    public GlowCommand(JavaPlugin plugin)
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

        // Toggle glowing
        boolean nowGlowing;
        if (player.hasPotionEffect(PotionEffectType.GLOWING))
        {
            player.removePotionEffect(PotionEffectType.GLOWING);
            nowGlowing = false;
        }
        else
        {
            // Long duration, no particles, not ambient
            PotionEffect glow = new PotionEffect(PotionEffectType.GLOWING, 1_000_000, 0, false, false, false);
            player.addPotionEffect(glow);
            nowGlowing = true;
        }

        player.sendMessage(CommandMessages.command(ChatColor.GRAY + "You " + (nowGlowing ? "now" : "no longer") + " have the glowing outline effect."));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return Collections.emptyList();
    }
}