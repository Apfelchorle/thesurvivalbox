package org.thesandbox.core.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SayCommand implements ISubCommand
{
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (args.length == 0)
        {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <message>"));
            return true;
        }

        final String rawMessage = String.join(" ", args);
        // Support & color codes (e.g. &d, &l, etc.)
        final String coloredMessage = ChatColor.translateAlternateColorCodes('&', rawMessage);

        final String senderName = (sender instanceof Player p) ? p.getName() : "CONSOLE";
        //refrence message :  • Server » usfl » test
        final String prefix = "&6 " + senderName + "&r&8 » &r";

        // Detect @everyone (case-insensitive)
        final boolean pingEveryone = containsIgnoreCase(coloredMessage, "@everyone");

        for (Player viewer : Bukkit.getOnlinePlayers())
        {
            String perViewer = coloredMessage;

            // Highlight @everyone for ALL viewers (yellow)
            if (pingEveryone)
            {
                perViewer = highlightInsensitive(perViewer, "@everyone",
                        ChatColor.YELLOW, ChatColor.LIGHT_PURPLE);
            }

            // Highlight THIS viewer's @Name (yellow)
            perViewer = highlightInsensitive(perViewer, "@" + viewer.getName(),
                    ChatColor.YELLOW, ChatColor.LIGHT_PURPLE);

            // Highlight THIS viewer's bare Name (yellow)
            perViewer = highlightInsensitive(perViewer, viewer.getName(),
                    ChatColor.YELLOW, ChatColor.LIGHT_PURPLE);

            // Send message (baseline magenta remains unless sender’s & codes override it)
            viewer.sendMessage(CommandMessages.server("") + prefix + perViewer);

            // Play sound if @everyone OR their @Name OR their bare Name was mentioned
            if (pingEveryone
                    || containsIgnoreCase(coloredMessage, "@" + viewer.getName())
                    || containsIgnoreCase(coloredMessage, viewer.getName()))
            {
                viewer.playSound(viewer.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1337F, 0.9F);
            }
        }

        // Also log to console (with color)
        Bukkit.getConsoleSender().sendMessage(CommandMessages.server("") + prefix + coloredMessage);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // Offer completions for the *last token* of the message:
        // - Player names (case-insensitive)
        // - "@everyone" (always available; matches prefix if user typed '@e' etc.)
        if (args.length == 0)
        {
            return Collections.emptyList();
        }

        String last = args[args.length - 1];
        boolean wantsAt = last.startsWith("@");
        String fragment = wantsAt ? last.substring(1) : last; // Strip '@' when matching names
        String fragLower = fragment.toLowerCase(Locale.ENGLISH);

        List<String> names = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ENGLISH).startsWith(fragLower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        List<String> out = new ArrayList<>(names.size() + 1);

        // If they're typing with '@', suggest '@Name', else suggest bare Name
        if (wantsAt)
        {
            for (String n : names) out.add("@" + n);
        }
        else
        {
            out.addAll(names);
        }

        // Suggest @everyone, respecting whether the user started with '@'
        String everyoneSuggest = "@everyone";
        String compare = last.toLowerCase(Locale.ENGLISH);
        if (everyoneSuggest.toLowerCase(Locale.ENGLISH).startsWith(compare)
                || "everyone".startsWith(compare))
        {
            if (!out.contains("@everyone")) out.add("@everyone");
        }

        out.sort(Comparator.comparing(String::toLowerCase, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /* ================= helpers ================= */

    private static boolean containsIgnoreCase(String text, String needle)
    {
        if (text == null || needle == null) return false;
        return text.toLowerCase(Locale.ENGLISH).contains(needle.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Case-insensitive highlight of all occurrences of 'term'.
     * - If 'term' contains non-word chars (like '@'), we do a plain case-insensitive find.
     * - Otherwise we use word boundaries so "Ann" doesn't match "Annika".
     * Inserts startColor before the match and resetColor after, preserving surrounding text.
     * Runs after color-code translation.
     */
    private static String highlightInsensitive(String input, String term,
                                               ChatColor startColor, ChatColor resetColor)
    {
        if (input == null || term == null || term.isEmpty()) return input;

        // Choose pattern: with or without word boundaries
        boolean hasNonWord = Pattern.compile("\\W").matcher(term).find();
        String core = Pattern.quote(term);
        String pattern = hasNonWord ? "(?i)" + core : "(?i)\\b" + core + "\\b";

        Matcher m = Pattern.compile(pattern).matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find())
        {
            String matched = m.group();
            m.appendReplacement(sb,
                    Matcher.quoteReplacement(startColor.toString() + matched + resetColor));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}