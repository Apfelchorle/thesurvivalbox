package org.thesandbox.core.listeners;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.thesandbox.core.DiscordBridge;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.fun.Utils;
import org.thesandbox.core.util.HexColorUtil;
import org.thesandbox.core.util.PlayerDataKeys;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatMentionFormatListener implements Listener
{
    private final TheSandboxCore core;
    private final ChatFilterEngine chatFilterEngine;

    // Adventure / MiniMessage
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacySection();

    public ChatMentionFormatListener(TheSandboxCore core, ChatFilterEngine chatFilterEngine)
    {
        this.core = core;
        this.chatFilterEngine = chatFilterEngine;
    }

    // Case-insensitive word-boundary @everyone
    private static final Pattern EVERYONE_PATTERN =
            Pattern.compile("(?i)(?<!\\w)@everyone(?!\\w)");

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event)
    {
        if (event.isCancelled()) return;

        final Player sender = event.getPlayer();
        final Component originalComponent = event.message();
        final String rawText = Utils.plainText(originalComponent);

        final ChatFilterEngine.Result filterResult = sender.hasPermission("sandbox.staff")
                ? null
                : chatFilterEngine.scan(rawText);

        // Guild chat toggle: route through GuildManager so /gchat and toggled chat share formatting, spies, and console logging.
        var guilds = core.getGuildManager();
        if (guilds != null && guilds.isInGuild(sender.getUniqueId()) && guilds.isGuildChatToggled(sender.getUniqueId())) {
            event.setCancelled(true);
            final String msg = rawText;
            Bukkit.getScheduler().runTask(core, () -> guilds.sendGuildChat(sender, msg));
            return;
        }

        // Detect if the player is trying to use MiniMessage (<red>Hello</red>, etc.)
        final boolean useMiniMessage = looksLikeMiniMessage(rawText);

        // ----- Color handling with &k restriction -----
        String sanitized = rawText;
        if (!sender.hasPermission("sandbox.staff")) {
            sanitized = sanitized.replaceAll("(?i)&k", "");
        }

        Component baseBodyComponent;
        if (useMiniMessage) {
            baseBodyComponent = miniMessage.deserialize(sanitized);
        } else {
            baseBodyComponent = Utils.legacyserializer(sanitized);
        }

        // If using MiniMessage, don't translate & → § (let MiniMessage handle things / plain text)
//        String colored;
//        if (useMiniMessage) {
//            colored = sanitized;
//        } else {
//            colored = HexColorUtil.translate(sanitized);
//            if (!sender.hasPermission("sandbox.staff")) {
//                colored = colored.replaceAll("(?i)\u00A7k", "");
//            }
//        }

        // We’ll do a custom rebroadcast to support per-recipient highlights + sounds.
        event.setCancelled(true);

        // Since this listener cancels and manually rebroadcasts normal chat, mirror it to Discord here.
        // If another plugin cancelled the event first (like ChatReaction), the early return above prevents mirroring.
        DiscordBridge discord = core.getDiscord();
        if (discord != null && discord.isReady()) {
            discord.sendPublicMessageFromMinecraft(sender, rawText);
        }

        final boolean staffEveryone = sender.hasPermission("sandbox.staff")
                && EVERYONE_PATTERN.matcher(rawText).find();

        // Display name with LuckPerms prefix + Essentials nickname (if any), with spacing fixes
        final Component headerComponent = getDisplayWithPrefixComponent(sender)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY));

        Bukkit.getScheduler().runTask(core, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers())
            {
                Component viewerBody = baseBodyComponent;

                if (filterResult != null && filterResult.triggered) {
                    boolean viewerWantsCensored = core.getDataListener() // however you expose PlayerDataListener from core
                            .get(viewer.getUniqueId(), PlayerDataKeys.CHATFILTER, true);
                    if (viewerWantsCensored) {
                        viewerBody = filterResult.censoredComponent;
                    }
                }

                // If sender is staff and used @everyone, highlight it for everyone
                boolean pingThisViewer = false;
                if (staffEveryone)
                {
                    viewerBody = highlightEveryone(baseBodyComponent);
                    pingThisViewer = true; // everyone gets the ping
                }
                // Then apply per-viewer name mention highlighting (@Name or bare Name)
                viewerBody = highlightMentionsFor(viewer, viewerBody);

                // Ping if they were mentioned directly, or if @everyone (by staff) was used
                if (!pingThisViewer && isMentioned(rawText, viewer.getName()))
                {
                    pingThisViewer = true;
                }

                viewer.sendMessage(headerComponent.append(viewerBody));

                if (pingThisViewer)
                {
                    try {
                        viewer.playSound(
                                viewer.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_PLING,
                                SoundCategory.MASTER,
                                1337F, // volume
                                0.9F   // pitch
                        );
                    } catch (Throwable ignored) {
                        // In case the server version lacks these constants, silently ignore.
                    }
                }
            }
            Bukkit.getConsoleSender().sendMessage(headerComponent.append(baseBodyComponent));
        });
    }

    /* ===== Display name building: LuckPerms prefix + Essentials nickname (fallback to Bukkit), with spacing fixes ===== */

    private Component getPlayerNameComponent(Player p) {
        String base = null;

        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("Essentials");
            if (pl instanceof Essentials ess && pl.isEnabled()) {
                User u = ess.getUser(p);
                if (u != null) {
                    String nickname = u.getNickname();
                    if (nickname != null && !nickname.isBlank()) {
                        base = nickname;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (base == null || base.isBlank()) {
            String displayName = p.getDisplayName();
            base = displayName.isBlank() ? p.getName() : displayName;
        }

        if (base.contains("&")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(base);
        }

        if (base.indexOf('§') >= 0) {
            return LegacyComponentSerializer.legacySection().deserialize(base);
        }

        return Component.text(base, NamedTextColor.GRAY);
    }

    private Component getGuildPrefixComponent(Player p) {
        try {
            var guilds = core.getGuildManager();
            if (guilds == null) return Component.empty();

            var g = guilds.guildOf(p.getUniqueId());
            if (g == null || g.tag() == null || g.tag().isEmpty()) return Component.empty();
            String tag = g.tag();
            if (tag.contains("§")) {
                return LegacyComponentSerializer.legacySection().deserialize(tag);
            }
            return LegacyComponentSerializer.legacyAmpersand().deserialize(tag);
        } catch (Throwable ignored) {
            return Component.empty();
        }
    }

    private Component getDisplayWithPrefixComponent(Player p) {
        Component guildPrefix = getGuildPrefixComponent(p);
        Component lpPrefix = getLuckPermsPrefixComponent(p);
        Component nameComponent = getPlayerNameComponent(p);

        TextComponent.Builder builder = Component.text();

        if (!Component.empty().equals(guildPrefix)) {
            builder.append(guildPrefix).append(Component.space());
        }
        if (!Component.empty().equals(lpPrefix)) {
            builder.append(lpPrefix).append(Component.space());
        }

        builder.append(nameComponent);
        return builder.build();
    }
    private String getDisplayWithPrefix(Player p)
    {
        String base = getEssentialsDisplayName(p);

        if (base == null || base.isEmpty()) {
            base = p.getName();
        }

        if (hasColorCode(base)) {
            base = base;
        } else {
            base = rankNameColor(p) + stripLegacyColorsKeepFormatting(base);
        }

        base = ltrimSpacesAfterLeadingColorCodes(base);

        String guildPrefix = getGuildPrefixColorized(p);
        String lpPrefix = getLuckPermsPrefixColorized(p);

        String prefix = joinPrefixes(guildPrefix, lpPrefix);

        if (prefix == null || prefix.isEmpty()) {
            return base;
        }

        if (!hasVisibleText(prefix)) {
            return prefix + base;
        }

        return joinWithSingleSpace(prefix, base);
    }

    // helper function
    private static boolean hasColorCode(String s) {
        if (s == null) return false;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '§') {
                char c = Character.toLowerCase(s.charAt(i + 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) return true;
            }
        }
        return false;
    }

    private static String stripLegacyColorsKeepFormatting(String input)
    {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder output = new StringBuilder();

        for (int i = 0; i < input.length(); i++)
        {
            char c = input.charAt(i);

            if (c == '§' && i + 1 < input.length())
            {
                char code = Character.toLowerCase(input.charAt(i + 1));

                if (code == 'x')
                {
                    i++;

                    for (int j = 0; j < 6; j++)
                    {
                        if (i + 2 < input.length() && input.charAt(i + 1) == '§') {
                            i += 2;
                        }
                    }

                    continue;
                }

                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f'))
                {
                    i++;
                    continue;
                }

                if (code >= 'k' && code <= 'o')
                {
                    output.append('§').append(code);
                    i++;
                    continue;
                }

                if (code == 'r')
                {
                    i++;
                    continue;
                }
            }

            output.append(c);
        }

        return output.toString();
    }

    private String getEssentialsDisplayName(Player p)
    {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("Essentials");
            if (pl instanceof Essentials ess && pl.isEnabled())
            {
                User u = ess.getUser(p);
                if (u != null)
                {
                    // Read the actual stored Essentials nickname. User#getDisplayName() can
                    // return the account name when Essentials' change-displayname option is
                    // disabled, which made chat appear to ignore nicknames.
                    String nickname = u.getNickname();
                    if (nickname != null && !nickname.isBlank()) {
                        return HexColorUtil.translate(nickname);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through to Bukkit display name.
        }

        String displayName = p.getDisplayName();
        return displayName.isBlank() ? p.getName() : displayName;
    }


    private String rankNameColor(Player p)
    {
        try {
            if (core.getLoginService() == null) return ChatColor.GRAY.toString();
            return colorizeLegacy(core.getLoginService().getRank(p).color);
        } catch (Throwable ignored) {
            return ChatColor.GRAY.toString();
        }
    }

    /** Returns only the guild tag. Guild rank prefixes are handled through the player tag/LuckPerms prefix slot. */
    private String getGuildPrefixColorized(Player p)
    {
        try {
            var guilds = core.getGuildManager();
            if (guilds == null) return null;
            var g = guilds.guildOf(p.getUniqueId());
            if (g == null) return null;

            return colorizeLegacy(g.tag());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String colorizeLegacy(String s) {
        if (s == null || s.isEmpty()) return null;
        return HexColorUtil.translate(s);
    }

    private static String joinPrefixes(String first, String second) {
        if (first == null || first.isEmpty()) return second;
        if (second == null || second.isEmpty()) return first;
        if (!hasVisibleText(first)) return first + second;
        if (!hasVisibleText(second)) return first + second;
        return joinWithSingleSpace(first, second);
    }

    /**
     * Use LuckPermsProvider to get the user's cached prefix (synchronous, from cache).
     * Converts '&' color codes to section signs. If the prefix already contains '§', it's returned as-is.
     */
    private String getLuckPermsPrefixColorized(Player p)
    {
        try {
            var lp = LuckPermsProvider.get();
            var lpUser = lp.getUserManager().getUser(p.getUniqueId());
            if (lpUser == null) return null;
            String prefix = lpUser.getCachedData().getMetaData().getPrefix();
            if (prefix == null || prefix.isEmpty()) return null;
            return HexColorUtil.translate(prefix);
        } catch (Throwable ignored) {
            return null; // LP not present or unexpected issue
        }
    }

    private Component getLuckPermsPrefixComponent(Player p) {
        try {
            var lp = LuckPermsProvider.get();
            var lpUser = lp.getUserManager().getUser(p.getUniqueId());
            if (lpUser == null) return Component.empty();
            String prefix = lpUser.getCachedData().getMetaData().getPrefix();
            if (prefix == null || prefix.isEmpty()) return Component.empty();

            if (prefix.contains("§")) {
                return LegacyComponentSerializer.legacySection().deserialize(prefix);
            }
            return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
        } catch (Throwable ignored) {
            return Component.empty();
        }
    }

    /* ===== Mention helpers ===== */

    private boolean isMentioned(String message, String playerName)
    {
        if (message == null || playerName == null) return false;
        // Two patterns: @Name and bare Name, both word-bound
        Pattern atPattern   = Pattern.compile("(?i)(?<!\\w)@" + Pattern.quote(playerName) + "(?!\\w)");
        Pattern barePattern = Pattern.compile("(?i)(?<!\\w)"  + Pattern.quote(playerName) + "(?!\\w)");
        return atPattern.matcher(message).find() || barePattern.matcher(message).find();
    }


    private Component highlightMentionsFor(Player target, Component message) {
        if (target == null || message == null) return message;

        final String name = target.getName();
        Pattern atPattern = Pattern.compile("(?i)(?<!\\w)@" + Pattern.quote(name) + "(?!\\w)");
        Pattern barePattern = Pattern.compile("(?i)(?<!\\w)" + Pattern.quote(name) + "(?!\\w)");

        // Replace @Name
        message = message.replaceText(builder -> builder
                .match(atPattern)
                .replacement(Component.text("@" + name, NamedTextColor.YELLOW))
        );

        // Replace bare Name
        message = message.replaceText(builder -> builder
                .match(barePattern)
                .replacement(Component.text(name, NamedTextColor.YELLOW))
        );

        return message;
    }

    private Component highlightEveryone(Component message) {
        return message.replaceText(builder -> builder
                .match(EVERYONE_PATTERN)
                .replacement(Component.text("@everyone", NamedTextColor.YELLOW))
        );
    }

    private String highlightMentionsFor(Player target, String message, boolean useMiniMessage)
    {
        if (message == null || target == null) return message;
        final String name = target.getName();

        if (useMiniMessage) {
            // MiniMessage highlighting: <yellow>@Name</yellow>, <yellow>Name</yellow>
            String out = replaceMention(
                    message,
                    Pattern.compile("(?i)(?<!\\w)@" + Pattern.quote(name) + "(?!\\w)"),
                    m -> "<yellow>@" + name + "</yellow>"
            );
            out = replaceMention(
                    out,
                    Pattern.compile("(?i)(?<!\\w)" + Pattern.quote(name) + "(?!\\w)"),
                    m -> "<yellow>" + name + "</yellow>"
            );
            return out;
        } else {
            // Legacy highlighting with ChatColor
            final String reset  = ChatColor.RESET.toString();
            final String yellow = ChatColor.YELLOW.toString();

            String out = replaceMention(
                    message,
                    Pattern.compile("(?i)(?<!\\w)@" + Pattern.quote(name) + "(?!\\w)"),
                    m -> yellow + "@" + name + reset
            );
            out = replaceMention(
                    out,
                    Pattern.compile("(?i)(?<!\\w)" + Pattern.quote(name) + "(?!\\w)"),
                    m -> yellow + name + reset
            );

            return out;
        }
    }

    @FunctionalInterface
    private interface Replacer { String apply(Matcher m); }

    private String replaceMention(String input, Pattern pattern, Replacer replacer)
    {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find())
        {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /* ===== Spacing / color utility helpers ===== */

    private static String joinWithSingleSpace(String left, String right) {
        if (left == null || left.isEmpty())  return right == null ? "" : right;
        if (right == null || right.isEmpty()) return left;
        // strip trailing spaces from left
        int li = left.length();
        while (li > 0 && left.charAt(li - 1) == ' ') li--;
        left = left.substring(0, li);
        // strip leading spaces from right
        int rj = 0;
        while (rj < right.length() && right.charAt(rj) == ' ') rj++;
        right = right.substring(rj);
        return left + " " + right;
    }

    // Remove spaces that appear immediately after any leading '§x' style color/format codes
    private static String ltrimSpacesAfterLeadingColorCodes(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder codes = new StringBuilder();
        int i = 0;
        while (i + 1 < s.length() && s.charAt(i) == '§' && isColorCodeChar(s.charAt(i + 1))) {
            codes.append('§').append(s.charAt(i + 1));
            i += 2;
        }
        // skip spaces immediately after the codes
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return codes.append(s.substring(i)).toString();
    }

    private static boolean isColorCodeChar(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o') || c == 'r';
    }

    private static String stripColorCodes(String s) {
        if (s == null) return "";
        return HexColorUtil.stripColors(s);
    }

    private static boolean hasVisibleText(String s) {
        return !stripColorCodes(s).trim().isEmpty();
    }

    // Very simple heuristic: if the message contains both '<' and '>', assume MiniMessage-style
    private boolean looksLikeMiniMessage(String s) {
        if (s == null) return false;
        return s.contains("<") && s.contains(">");
    }
}