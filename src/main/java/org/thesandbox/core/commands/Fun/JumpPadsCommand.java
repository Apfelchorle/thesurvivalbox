package org.thesandbox.core.commands.Fun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.util.PlayerDataListener;

import java.util.*;
import java.util.stream.Collectors;

public class JumpPadsCommand implements Listener, ISubCommand {

    public static final double DAMPING_COEFFICIENT = 0.8;
    private static final String DEFAULT_MODE = JumpPadMode.OFF.name();
    private final Map<Player, Boolean> pushMap = new HashMap<>();
    private final TheSandboxCore plugin;
    private final PlayerDataListener dataListener;
    private final double strength = 0.4;

    public JumpPadsCommand(TheSandboxCore plugin, PlayerDataListener dataListener)
    {
        this.plugin = plugin;
        this.dataListener = dataListener;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private JumpPadMode getModeFor(Player player) {
        String stored = dataListener.get(player.getUniqueId(), "jumppadmode", DEFAULT_MODE);
        try {
            return JumpPadMode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return JumpPadMode.OFF;
        }
    }

    private void setModeFor(Player player, JumpPadMode mode) {
        dataListener.set(player.getUniqueId(), "jumppadmode", mode.name());
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        final String who = (sender instanceof Player) ? sender.getName() : "CONSOLE"; // i have no idea why this is here

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can execute this command!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0)
        {
            JumpPadMode current = getModeFor(player);
            player.sendMessage(Component.text("Your jumppads mode: " + current.getLabel(), NamedTextColor.YELLOW));
            return true;
        }

        JumpPadMode target;
        try
        {
            target = JumpPadMode.fromString(args[0]);
        }
        catch (IllegalArgumentException e)
        {
            String validModes = Arrays.stream(JumpPadMode.values())
                    .map(m -> m.name().toLowerCase())
                    .collect(Collectors.joining(", "));
            player.sendMessage(Component.text("Invalid mode: " + args[0] + " (valid: " + validModes + ")", NamedTextColor.RED));
            return true;
        }

        setModeFor(player, target);
        player.sendMessage(Component.text("Your jumppads mode is now: " + target.getLabel(), NamedTextColor.GREEN));
        return true;
    }


    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> list = Arrays.stream(JumpPadMode.values())
                    .map(m -> m.name().toLowerCase())
                    .collect(Collectors.toList());
            list.removeIf(s -> !s.startsWith(prefix));
            return list;
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event)
    {
        if (!event.hasExplicitlyChangedBlock())
        {
            return;
        }

        final Player player = event.getPlayer();
        final JumpPadMode mode = getModeFor(player);

        if (mode == JumpPadMode.OFF || player.getGameMode() == GameMode.SPECTATOR)
        {
            return;
        }

        final Block block = event.getTo().getBlock();
        final boolean onWool = Tag.WOOL.isTagged(block.getRelative(0, -1, 0).getType());
        final Vector velocity = player.getVelocity().clone();
        boolean changed = false;

        Boolean canPush = pushMap.get(player);
        if (canPush == null)
        {
            canPush = true;
        }

        if (onWool)
        {
            if (canPush)
            {
                velocity.setY(strength + 0.85);
                changed = true;
            }
            canPush = false;
        }
        else
        {
            canPush = true;
        }
        pushMap.put(player, canPush);

        if (mode == JumpPadMode.NORMAL_AND_SIDEWAYS)
        {
            if (Tag.WOOL.isTagged(block.getRelative(1, 0, 0).getType()))
            {
                velocity.add(new Vector(-DAMPING_COEFFICIENT * strength, 0.0, 0.0));
                changed = true;
            }

            if (Tag.WOOL.isTagged(block.getRelative(-1, 0, 0).getType()))
            {
                velocity.add(new Vector(DAMPING_COEFFICIENT * strength, 0.0, 0.0));
                changed = true;
            }

            if (Tag.WOOL.isTagged(block.getRelative(0, 0, 1).getType()))
            {
                velocity.add(new Vector(0.0, 0.0, -DAMPING_COEFFICIENT * strength));
                changed = true;
            }

            if (Tag.WOOL.isTagged(block.getRelative(0, 0, -1).getType()))
            {
                velocity.add(new Vector(0.0, 0.0, DAMPING_COEFFICIENT * strength));
                changed = true;
            }
        }

        if (changed)
        {
            player.setFallDistance(0.0f);
            player.setVelocity(velocity);
        }
    }

    public enum JumpPadMode
    {
        OFF("Off", "off", "disabled"),
        NORMAL("Madgeek", "normal", "adhd", "coffee", "on", "enabled"),
        NORMAL_AND_SIDEWAYS("Normal and Sideways", "both");

        private final String label;
        private final List<String> alternateNames;

        JumpPadMode(String label)
        {
            this.label = label;
            this.alternateNames = Collections.emptyList();
        }

        JumpPadMode(String label, String... alternativeNames)
        {
            this.label = label;
            this.alternateNames = Arrays.stream(alternativeNames).toList();
        }

        public String getLabel()
        {
            return label;
        }

        public List<String> getAlternateNames()
        {
            return alternateNames;
        }

        public boolean isOn()
        {
            return this != OFF;
        }

        public static JumpPadMode fromString(String input)
        {
            return Arrays.stream(values())
                    .filter(value -> value.getLabel().equalsIgnoreCase(input)
                            || value.getAlternateNames().stream().anyMatch(name -> name.equalsIgnoreCase(input))
                            || value.name().equalsIgnoreCase(input))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid mode: " + input));
        }
    }
}