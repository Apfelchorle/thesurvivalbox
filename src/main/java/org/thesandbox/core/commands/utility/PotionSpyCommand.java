package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.thesandbox.core.services.PotionSpyService; // <-- UPDATED PACKAGE
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;
import java.util.stream.Collectors;

public class PotionSpyCommand implements ISubCommand
{
    private final PotionSpyService service;

    public PotionSpyCommand(PotionSpyService service)
    {
        this.service = service;
    }

    private void msg(CommandSender s, String m) { s.sendMessage(m); }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length == 0)
        {
            if (!(sender instanceof Player p)) { msg(sender, CommandMessages.error(ChatColor.RED + "Only players can toggle PotionSpy.")); return true; }
            boolean state = service.toggle(p.getUniqueId());
            msg(sender, CommandMessages.command("PotionSpy is now " + (state ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.RESET + "."));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT))
        {
            case "on", "enable" -> {
                if (!(sender instanceof Player p)) { msg(sender, CommandMessages.error(ChatColor.RED + "Only players can enable PotionSpy.")); return true; }
                service.setEnabled(p.getUniqueId(), true);
                msg(sender, CommandMessages.command("PotionSpy is now " + ChatColor.GREEN + "enabled" + ChatColor.RESET + "."));
                return true;
            }
            case "off", "disable" -> {
                if (!(sender instanceof Player p)) { msg(sender, CommandMessages.error(ChatColor.RED + "Only players can disable PotionSpy.")); return true; }
                service.setEnabled(p.getUniqueId(), false);
                msg(sender, CommandMessages.command("PotionSpy is now " + ChatColor.RED + "disabled" + ChatColor.RESET + "."));
                return true;
            }
            case "history" -> {
                // /potionspy history [player] <page>
                if (args.length == 3)
                {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) { msg(sender, CommandMessages.error("Please specify a valid online player.")); return true; }

                    List<Map.Entry<ThrownPotion, Long>> list = new ArrayList<>(service.getPlayerThrownPotions(target));
                    if (list.isEmpty()) { msg(sender, CommandMessages.command("That player has not thrown any potions yet.")); return true; }

                    Collections.reverse(list);
                    int lastPage = (int)Math.ceil(list.size() / 5.0);
                    Integer page = parsePositiveInt(args[2]);
                    if (page == null || page < 1 || page > lastPage) { msg(sender, CommandMessages.error("Please specify a valid page between 1 and " + lastPage + ".")); return true; }

                    sendHistory(sender, list, page, lastPage, "History for " + target.getName());
                    return true;
                }
                else if (args.length == 2)
                {
                    // treat as global history page
                    List<Map.Entry<ThrownPotion, Long>> list = new ArrayList<>(service.getAllThrownPotions());
                    if (list.isEmpty()) { msg(sender, CommandMessages.command("No potions have been thrown yet.")); return true; }

                    Collections.reverse(list);
                    int lastPage = (int)Math.ceil(list.size() / 5.0);
                    Integer page = parsePositiveInt(args[1]);
                    if (page == null || page < 1 || page > lastPage) { msg(sender, CommandMessages.error("Please specify a valid page between 1 and " + lastPage + ".")); return true; }

                    sendHistory(sender, list, page, lastPage, "Global History");
                    return true;
                }
                else
                {
                    msg(sender, CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " history [player] <page>"));
                    return true;
                }
            }
        }

        msg(sender, CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <on|off|history>"));
        return true;
    }

    private void sendHistory(CommandSender sender, List<Map.Entry<ThrownPotion, Long>> entries, int page, int lastPage, String title)
    {
        final int pageSize = 5;
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, entries.size());
        List<Map.Entry<ThrownPotion, Long>> slice = entries.subList(from, to);

        sender.sendMessage(CommandMessages.command(ChatColor.DARK_GRAY + "------------------ " + ChatColor.YELLOW + "PotionSpy: " + title + ChatColor.DARK_GRAY + " ------------------"));
        for (Map.Entry<ThrownPotion, Long> e : slice)
        {
            ThrownPotion p = e.getKey();
            Player shooter = (p.getShooter() instanceof Player sp) ? sp : null;
            boolean troll = service.isTrollPotion(p);

            String line = ChatColor.RESET
                    + (shooter != null ? shooter.getName() : "Unknown")
                    + " splashed a potion at "
                    + ChatColor.YELLOW + "X:" + p.getLocation().getBlockX()
                    + " Y:" + p.getLocation().getBlockY()
                    + " Z:" + p.getLocation().getBlockZ() + ChatColor.RESET
                    + " in world '" + ChatColor.YELLOW + p.getWorld().getName() + ChatColor.RESET + "' about "
                    + ChatColor.YELLOW + timeAgo(e.getValue(), System.currentTimeMillis()) + ChatColor.RESET + " ago"
                    + (troll ? ChatColor.RED + " (most likely troll potion/potions)" : "") + ".";
            sender.sendMessage(CommandMessages.command(line));
        }
        sender.sendMessage(CommandMessages.command(ChatColor.DARK_GRAY + "-------------------- " + ChatColor.YELLOW + page + " / " + lastPage + ChatColor.DARK_GRAY + " --------------------"));
    }

    private Integer parsePositiveInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; } }

    private String timeAgo(long past, long now)
    {
        long seconds = Math.max(0, (now - past) / 1000L);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1) {
            return List.of("on","off","history").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "history".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            if ("1".startsWith(partial)) {
                List<String> out = new ArrayList<>(names);
                out.add("1");
                return out;
            }
            return names;
        }

        if (args.length == 3 && "history".equalsIgnoreCase(args[0])) {
            return List.of("1");
        }

        return Collections.emptyList();
    }
}