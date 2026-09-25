package org.thesandbox.core.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexColorUtil {
    private static final Pattern AMP_HEX = Pattern.compile("(?i)&?#([0-9a-f]{6})");
    private static final Pattern SECTION_HEX = Pattern.compile("(?i)§#([0-9a-f]{6})");
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)(?:§x(?:§[0-9a-f]){6}|&x(?:&[0-9a-f]){6})");

    public static String toLegacyHex(String hex) {
        if (hex == null) return "";
        hex = hex.replace("#", "");
        if (hex.length() != 6 || !hex.matches("(?i)[0-9a-f]{6}")) return "";
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append('§').append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /**
     * Converts normal legacy color codes and hex colors into Bukkit legacy section codes.
     * Supports:
     *   &a, &l, &r, etc.
     *   &#7200ff
     *   #7200ff
     *   §#7200ff
     */
    public static String translate(String input) {
        if (input == null || input.isEmpty()) return input;

        String out = input;
        out = replaceHex(out, SECTION_HEX);
        out = replaceHex(out, AMP_HEX);
        out = ChatColor.translateAlternateColorCodes('&', out);
        return out;
    }

    private static String replaceHex(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Removes standard legacy color codes and legacy hex color sequences. */
    public static String stripColors(String input) {
        if (input == null) return "";
        String out = translate(input);
        out = LEGACY_HEX.matcher(out).replaceAll("");
        out = out.replaceAll("(?i)[§&][0-9A-FK-OR]", "");
        out = out.replaceAll("(?i)&?#[0-9A-F]{6}", "");
        out = out.replaceAll("(?i)§#[0-9A-F]{6}", "");
        return out;
    }
}
