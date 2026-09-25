package org.thesandbox.core.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.util.GenericDataKeys;
import org.thesandbox.core.util.PluginConfigManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFilterEngine {

    private final PluginConfigManager configManager;
    private final TheSandboxCore plugin;

    public ChatFilterEngine(PluginConfigManager configManager, TheSandboxCore plugin) {
        this.configManager = configManager;
        this.plugin = plugin;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "words.yml");
    }

    public Result scan(String plain) {
        List<String> blockedWords = configManager.getOrCreateCustom(GenericDataKeys.CHATFILTER, GenericDataKeys.BADWORDS, file());

        List<String> detectedBadWords = new ArrayList<>();
        StringBuilder combinedRegex = new StringBuilder();
        for (String word : blockedWords) {
            if (word == null || word.isBlank()) continue;
            if (!combinedRegex.isEmpty()) combinedRegex.append("|");
            combinedRegex.append("(").append(buildBypassTolerantRegex(word)).append(")");
        }

        if (combinedRegex.isEmpty()) {
            return new Result(false, plain, plain, null, detectedBadWords);
        }

        Pattern pattern = Pattern.compile(combinedRegex.toString(), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(plain);

        boolean triggered = false;
        while (matcher.find()) {
            detectedBadWords.add(matcher.group());
            triggered = true;
        }

        if (!triggered) {
            return new Result(false, plain, plain, null, detectedBadWords);
        }
        String censoredPlain = pattern.matcher(plain).replaceAll("&4****");
        Component censored = buildCensoredComponent(plain, pattern, detectedBadWords);
        return new Result(true, plain, censoredPlain, censored, detectedBadWords);
    }

    private Component buildCensoredComponent(String originalText, Pattern pattern, List<String> badWords) {
        TextComponent.Builder builder = Component.text();
        Matcher matcher = pattern.matcher(originalText);

        int lastEnd = 0;
        int wordIndex = 0;

        while (matcher.find() && wordIndex < badWords.size()) {
            builder.append(Component.text(originalText.substring(lastEnd, matcher.start())));

            String originalBadWord = badWords.get(wordIndex++);

            Component censorToken = Component.text("****")
                    .color(TextColor.fromHexString("#FF5555"))
                    .hoverEvent(HoverEvent.showText(Component.text("Censored Word: " + originalBadWord, NamedTextColor.RED)));

            builder.append(censorToken);
            lastEnd = matcher.end();
        }

        if (lastEnd < originalText.length()) {
            builder.append(Component.text(originalText.substring(lastEnd)));
        }

        return builder.build();
    }

    private String buildBypassTolerantRegex(String word) {
        StringBuilder regex = new StringBuilder();
        for (char c : word.toCharArray()) {
            String escaped = Pattern.quote(String.valueOf(c));
            regex.append(escaped).append("+");
            regex.append("[\\s._\\-*]*");
        }
        return regex.toString();
    }

    public String generateGrawlix(int wordLength) {
        String symbols = "$%#&!";
        java.util.Random random = new java.util.Random();
        int variance = random.nextInt(5) - 2;
        int finalLength = Math.max(3, wordLength + variance);
        StringBuilder result = new StringBuilder(finalLength);
        for (int i = 0; i < finalLength; i++) {
            result.append(symbols.charAt(random.nextInt(symbols.length())));
        }
        return result.toString();
    }


    public static class Result {
        public final boolean triggered;
        public final String plain;
        public final String censoredPlain;
        public final Component censoredComponent;
        public final List<String> detectedBadWords;

        Result(boolean triggered, String plain, String censoredPlain, Component censoredComponent, List<String> detectedBadWords) {
            this.triggered = triggered;
            this.plain = plain;
            this.censoredPlain = censoredPlain;
            this.censoredComponent = censoredComponent;
            this.detectedBadWords = detectedBadWords;
        }
    }
}