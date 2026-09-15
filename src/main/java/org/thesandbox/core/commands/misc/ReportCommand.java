package org.thesandbox.core.commands.misc;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.DiscordBridge;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReportCommand implements ISubCommand
{
    private static final long COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutes
    private final Map<String, Long> lastReport = new ConcurrentHashMap<>(); // reporterUUID:targetUUID -> time

    private final TheSandboxCore core;

    public ReportCommand(TheSandboxCore core)
    {
        this.core = core;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player reporter))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can use /" + label + "."));
            return true;
        }

        if (args.length < 2)
        {
            reporter.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <user> <reason>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null)
        {
            reporter.sendMessage(CommandMessages.error(ChatColor.RED + "That player is not online."));
            return true;
        }

        // No self-reports
        if (reporter.getUniqueId().equals(target.getUniqueId()))
        {
            reporter.sendMessage(CommandMessages.error("You cannot report yourself."));
            return true;
        }

        // Cooldown: same reporter->target within 5 minutes
        String key = reporter.getUniqueId() + ":" + target.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastReport.get(key);
        if (last != null && (now - last) < COOLDOWN_MS)
        {
            reporter.sendMessage(CommandMessages.error("You have already submitted a report on this player."));
            return true;
        }
        lastReport.put(key, now);

        // Disallow reporting staff members
        if (target.hasPermission("sandbox.staff"))
        {
            reporter.sendMessage(CommandMessages.error("You cannot report a staff member. If you need to report a staff member, " +
                    "open a \"Staff Report\" ticket in our Discord server. https://discord.the-sandbox.org"));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (reason.isEmpty())
        {
            reporter.sendMessage(CommandMessages.error(ChatColor.RED + "Please provide a reason."));
            return true;
        }

        // Acknowledge to reporter
        reporter.sendMessage(CommandMessages.command("Your report has been logged. Thank you for keeping our server safe."));

        // Prepare details for in-game notice + Discord embed
        Location loc = target.getLocation();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Send to Discord as an embed
        DiscordBridge bridge = core.getDiscord();
        if (bridge != null && bridge.isReady())
        {
            bridge.sendReportEmbed(reporter.getName(), target.getName(), reason, loc);
        }

        // Build rich in-game staff message with clickable names that run /tpo <name>
        TextComponent prefix = cc("&c&lServer &8» &c");
        TextComponent p1 = clickableTeleport(reporter.getName());
        TextComponent mid1 = raw(" §creported ");
        TextComponent p2 = clickableTeleport(target.getName());
        TextComponent mid2 = raw(" §cfor: ");
        TextComponent rsn = txt(reason, net.md_5.bungee.api.ChatColor.RED);

        TextComponent full = new TextComponent("");
        full.addExtra(prefix);
        full.addExtra(p1);
        full.addExtra(mid1);
        full.addExtra(p2);
        full.addExtra(mid2);
        full.addExtra(rsn);

        // Send to online staff (sandbox.staff)
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (core.getShushService() != null && core.getShushService().isEnabled(viewer.getUniqueId())) continue;
            if (viewer.hasPermission("sandbox.staff")) {
                viewer.spigot().sendMessage(full);
            }
        }

        // Log to console
        Bukkit.getConsoleSender().sendMessage(CommandMessages.server(reporter.getName() + " reported " + target.getName()
                + " for: (" + reason + ")."));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String partial = args[0].toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return java.util.Collections.emptyList();
    }

    /* ================= helpers ================= */

    private TextComponent clickableTeleport(String playerName)
    {
        TextComponent c = new TextComponent(playerName);
        c.setColor(net.md_5.bungee.api.ChatColor.RED);
        c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Click to Teleport")
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .create()));
        // IMPORTANT: Use existing /tpo command (no extra args)
        c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpo " + playerName));
        return c;
    }

    private static TextComponent raw(String s)
    {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', s));
    }

    private static TextComponent cc(String s)
    {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', s));
    }

    private static TextComponent txt(String s, net.md_5.bungee.api.ChatColor color)
    {
        TextComponent t = new TextComponent(ChatColor.translateAlternateColorCodes('&', s));
        t.setColor(color);
        return t;
    }
}
