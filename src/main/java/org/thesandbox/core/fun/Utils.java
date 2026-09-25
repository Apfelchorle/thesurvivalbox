package org.thesandbox.core.fun;

import net.dv8tion.jda.api.JDA;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jspecify.annotations.NonNull;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.login.LoginService;
import org.thesandbox.core.util.HexColorUtil;
import org.thesandbox.core.util.PlayerDataListener;

import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Built to reduce redundancy
public class Utils {

    private static final Random random = new Random();
    private final PlayerDataListener playerDataListener;
    private static TheSandboxCore plugin;

    public Utils(TheSandboxCore plugin, PlayerDataListener playerDataListener) {
        Utils.plugin = plugin;
        this.playerDataListener = playerDataListener;
    }

    public static final Pattern ANY_URL = Pattern.compile("(?i)https?://[^\\s<>()]+", Pattern.CASE_INSENSITIVE);

    public static void dump(Throwable t, String msg) {
        Logger logger = plugin.getLogger();
        StackTraceElement[] stackTrace = t.getStackTrace();
        plugin.getLogger().severe("Throwable" + msg + " : " + t);
        logger.warning("Important" + msg + " : " + t.getMessage() + "\n" + t.getCause().getMessage());
        logger.severe("StackTrace " + msg + " : " + Arrays.stream(stackTrace));
    }

    public static Component legacyserializer(String text) {
        if (text.contains("&")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        }
        if (text.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(text);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public static TextColor getRandomChatColor() {
        Color awtColor = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        return TextColor.color(awtColor.getRGB());
    }

    public static void playSound(Player listener, Location location, Sound sound) {
        float randomPitch = randomDoubleRange(0.5, 2.0).floatValue();
        listener.playSound(randomOffset(location, 2.0), sound, SoundCategory.MASTER, 1.0F, randomPitch);
    }

    public static Location randomOffset(Location a, double magnitude) {
        return a.clone().add(
                randomDoubleRange(-1.0, 1.0) * magnitude,
                randomDoubleRange(-1.0, 1.0) * magnitude,
                randomDoubleRange(-1.0, 1.0) * magnitude
        );
    }

    public static Component fakePlayerMessage(String player, String message, String rank, NamedTextColor color) {
        return Component.text()
                .append(Component.text(rank, color, TextDecoration.BOLD))
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player, color))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(message, NamedTextColor.WHITE))
                .build();
    }


    public static @NonNull String AdventureAPI(String msg) {
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(msg)
        );
    }

    public static @NonNull String AdventureAPI(Component msg) {
        return LegacyComponentSerializer.legacySection().serialize(msg);
    }

    public static @NonNull String plainText(Component msg) {
        return PlainTextComponentSerializer.plainText().serialize(msg);
    }

    public static boolean hasUuid(Player player) {

        UUID usfl = Bukkit.getOfflinePlayer("usfl").getUniqueId();
        UUID ThePyroMan = Bukkit.getOfflinePlayer("ThePyroMan").getUniqueId();
        UUID Target = player.getUniqueId();

        return Target == usfl || Target == ThePyroMan;
    }

    public static void WeAllKnowWhatThisIs(TheSandboxCore plugin, Location location, Sound instrument) {
        float[] notes = { 1.0f, 1.0f, 1.2f, 1.5f, 1.2f, 2.0f };

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= notes.length) {
                    cancel();
                    return;
                }
                location.getWorld().playSound(location, instrument, SoundCategory.MASTER, 1.0f, notes[index]);
                index++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public static void debug(String message) {
        String info = "[THESANDBOXCORE]-[DEBUG] : " + message;
        plugin.getLogger().info(info);
    }

    // stolen from discord bridge
    public static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^>]+>");

    public static void sendASCII(String msg) {
        String coreArt = """
                  ____  ____  ____  _____
                 /   _\\/  _ \\/  __\\/  __/
                 |  /  | / \\||  \\/||  \\ \s
                 |  \\__| \\_/||    /|  /_\s
                 \\____/\\____/\\_/\\_\\\\____\\
                \s""";

        String out = coreArt + "\n" + msg;
        Bukkit.getConsoleSender().sendMessage(out);
    }

    public static String highlightDiscordMentionAmpersand(String amp, String mcName, String postColor) {
        if (amp == null || mcName == null || mcName.isEmpty()) return amp;
        if (postColor == null) postColor = "&r";

        String atPattern = "(?i)(?<!\\S)(@)(" + Pattern.quote(mcName) + ")(?!\\w)";
        amp = amp.replaceAll(atPattern, "&e$1$2" + postColor);

        String namePattern = "(?i)(?<![\\w@])(" + Pattern.quote(mcName) + ")(?!\\w)";
        amp = amp.replaceAll(namePattern, "&e$1" + postColor);

        amp = amp.replace("  ", " ");
        return amp;
    }

    public static boolean messageMentions(String content, String mcName) {
        if (content == null || mcName == null || mcName.isEmpty()) return false;
        String atPattern = "(?i)(?<!\\S)@(" + Pattern.quote(mcName) + ")(?!\\w)";
        String namePattern = "(?i)(?<!\\w)(" + Pattern.quote(mcName) + ")(?!\\w)";
        return Pattern.compile(atPattern).matcher(content).find()
                || Pattern.compile(namePattern).matcher(content).find();
    }

    public static BaseComponent[] legacy(String ampersandColored) {
        return TextComponent.fromLegacyText(HexColorUtil.translate(ampersandColored));
    }

    public static BaseComponent[] join(BaseComponent[]... arrays) {
        java.util.ArrayList<BaseComponent> list = new java.util.ArrayList<>();
        for (BaseComponent[] a : arrays) {
            if (a != null) {
                Collections.addAll(list, a);
            }
        }
        return list.toArray(new BaseComponent[0]);
    }

    public static List<String> extractAllUrls(String text) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (text != null && !text.isEmpty()) {
            Matcher m = ANY_URL.matcher(text);
            while (m.find()) out.add(Utils.trimTrailingUrlPunctuation(m.group()));
        }
        return out;
    }

    public static String stripAllUrls(String text) {
        if (text == null || text.isEmpty()) return "";
        return cleanupMediaSpacing(ANY_URL.matcher(text).replaceAll(""));
    }

    public static String stripMiniMessageTags(String s) {
        if (s == null) return "";
        return MINIMESSAGE_TAG.matcher(s).replaceAll("");
    }

    public static String replaceLinksWithMediaTag(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher m = ANY_URL.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String raw = m.group();
            String url = trimTrailingUrlPunctuation(raw);
            String trailing = raw.substring(url.length());
            m.appendReplacement(sb, Matcher.quoteReplacement("[Media](" + url + ")" + trailing));
        }
        m.appendTail(sb);
        return cleanupMediaSpacing(sb.toString());
    }

    public static String playerHeadUrl(Player p) {
        return "https://minotar.net/helm/" + p.getName() + "/64.png";
    }

    public static int[] parseCoords(String s) {
        try {
            String t = s.replace(",", " ").replace("(", " ").replace(")", " ").trim();
            String tl = t.toLowerCase(Locale.ENGLISH);

            int xi = tl.indexOf("x:");
            int yi = tl.indexOf("y:");
            int zi = tl.indexOf("z:");
            if (xi < 0 || yi < 0 || zi < 0) return null;

            int xEnd = yi > xi ? yi : t.length();
            int yEnd = zi > yi ? zi : t.length();
            int zEnd = t.length();

            int x = Integer.parseInt(t.substring(xi + 2, xEnd).replaceAll("[^\\d\\-]", "").trim());
            int y = Integer.parseInt(t.substring(yi + 2, yEnd).replaceAll("[^\\d\\-]", "").trim());
            int z = Integer.parseInt(t.substring(zi + 2, zEnd).replaceAll("[^\\d\\-]", "").trim());
            return new int[]{x, y, z};
        } catch (Exception e) {
            return null;
        }
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }

    public static boolean isStaffRank(LoginService.Rank r) {
        if (r == null) return false;
        return switch (r) {
            case OPERATOR, ADMIN, STAFF -> true;
            default -> false; // MB, VIP, and DEFAULT fall here
        };
    }

    public static String discordRankPrefix(LoginService.Rank r) {
        if (r == null) return "";
        return switch (r) {
            case OPERATOR -> "**OP** • ";
            case ADMIN -> "**ADMIN** • ";
            case STAFF -> "**STAFF** • ";
            case MB -> "**MB** • ";
            case VIP -> "**VIP** • ";
            default -> "";
        };
    }

    public static String stripAllColors(String s) {
        if (s == null) return "";
        // First remove MiniMessage tags like <red>, <bold>, <hover:...>, etc.
        String noMini = stripMiniMessageTags(s);
        // Then handle legacy & / § colors
        String translated = HexColorUtil.translate(noMini);
        return ChatColor.stripColor(translated);
    }

    public static String toLegacyHex(java.awt.Color c) {
        String hex = String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        return "&x&" + hex.charAt(0) + "&" + hex.charAt(1)
                + "&" + hex.charAt(2) + "&" + hex.charAt(3)
                + "&" + hex.charAt(4) + "&" + hex.charAt(5);
    }

    public static String antiPingEveryoneHere(String s) {
        if (s == null) return null;
        return s.replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere");
    }

    public static String cleanupMediaSpacing(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
                .replaceAll("(?i)(^|\\s)&\\s+(?=\\[Media])", "$1")
                .replaceAll("(?i)(^|\\s)&(?=\\[Media])", "$1")
                .replaceAll("(?i)\\[MEDIA\\]", "[Media]")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    public static String trimTrailingUrlPunctuation(String url) {
        if (url == null) return "";
        while (!url.isEmpty() && ".,!?;:".indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static boolean isMediaUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(png|jpe?g|gif|webp|bmp|mp4|mov|webm|m4v)(\\?.*)?$")
                || lower.contains("cdn.discordapp.com/attachments/")
                || lower.contains("media.discordapp.net/attachments/")
                || lower.contains("tenor.com/view/")
                || lower.contains("media.tenor.com/")
                || lower.contains("giphy.com/gifs/")
                || lower.contains("media.giphy.com/");
    }

    public static String firstClickableMediaUrl(java.util.List<String> mediaUrls) {
        if (mediaUrls == null) return null;
        for (String url : mediaUrls) {
            if (url != null && url.toLowerCase(Locale.ROOT).startsWith("http")) {
                return url;
            }
        }
        return null;
    }

    public static BaseComponent[] clickableMediaTagOrLegacy(String labelAmp, String url) {
        if (url == null || url.isBlank()) return legacy(labelAmp);
        return clickableMediaTag(labelAmp, url);
    }

    public static BaseComponent[] clickableMediaTag(String labelAmp, String url) {
        BaseComponent[] comps = legacy(labelAmp); // includes &e
        ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(HexColorUtil.translate("&6&oClick to view")).create());
        for (BaseComponent c : comps) {
            c.setClickEvent(click);
            c.setHoverEvent(hover);
        }
        return comps;
    }

    public LoginService.Rank resolveRankForName(String mcName) {
        try {
            Player p = Bukkit.getPlayerExact(mcName);
            if (p != null) {
                return plugin.getLoginService().getRank(p);
            }
        } catch (Throwable ignored) {
        }

        try {
            var lp = net.luckperms.api.LuckPermsProvider.get();
            var um = lp.getUserManager();
            UUID uuid = um.lookupUniqueId(mcName).get(3, java.util.concurrent.TimeUnit.SECONDS);
            if (uuid == null) return LoginService.Rank.DEFAULT;

            var user = um.loadUser(uuid).get(3, java.util.concurrent.TimeUnit.SECONDS);
            if (user == null) return LoginService.Rank.DEFAULT;

            String group = user.getPrimaryGroup();
            if (group == null) return LoginService.Rank.DEFAULT;

            switch (group.toLowerCase(java.util.Locale.ENGLISH)) {
                case "operator":
                    return LoginService.Rank.OPERATOR;
                case "administrator":
                    return LoginService.Rank.ADMIN;
                case "staff":
                    return LoginService.Rank.STAFF;
                case "masterbuilder":
                    return LoginService.Rank.MB;
                case "vip":
                    return LoginService.Rank.VIP;
                default:
                    return LoginService.Rank.DEFAULT;
            }
        } catch (Throwable ignored) {
            return LoginService.Rank.DEFAULT;
        }
    }

    public boolean dm(long discordId, String content, JDA jda) {
        if (jda == null) return false;
        try {
            jda.retrieveUserById(discordId).queue(user -> {
                user.openPrivateChannel().queue(ch -> ch.sendMessage(content).queue());
            });
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] DM failed for " + discordId + ": " + e.getMessage());
            return false;
        }
    }

    public String resolveLuckPermsPrefix(Player p) {
        try {
            var lp = net.luckperms.api.LuckPermsProvider.get();
            var user = lp.getUserManager().getUser(p.getUniqueId());
            if (user == null) return null;
            return user.getCachedData().getMetaData().getPrefix();
        } catch (Throwable ignored) {
            return null;
        }
    }



    /* end of stolen from discord bridge */

    public static Double randomDoubleRange(double min, double max) {
        return min + (random.nextDouble() * (max - min));
    }


    // will likely be useless for complex stuff but for times
    // when u just need to send colored message it'll prove usfl! (get it, get it)
    public static void SendMessage(Player player, String message, TextColor color) {
        Component msg = Component.empty();
        msg = msg.append(Component.text(message).color(color));
        player.sendMessage(msg);
    }
}