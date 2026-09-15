package org.thesandbox.core.commands.Admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.DiscordBridge;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.util.GitHubReleaseUpdater;
import org.thesandbox.core.util.UpdateTarget;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpdateLocalCommand implements ISubCommand {

    private final TheSandboxCore plugin;
    private final DiscordBridge discord;
    private final GitHubReleaseUpdater updater = new GitHubReleaseUpdater();

    public UpdateLocalCommand(TheSandboxCore plugin, DiscordBridge discord) {
        this.plugin = plugin;
        this.discord = discord;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sandbox.superuser")) {
            sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
            return true;
        }

        List<UpdateTarget> targets = loadTargets();

        if (targets.isEmpty()) {
            String msg = "No update targets configured (update.targets is empty).";
            sender.sendMessage(Component.text(msg, NamedTextColor.RED));
            discordBroadcast(msg, sender);
            return true;
        }

        sender.sendMessage(Component.text(
                "Checking " + targets.size() + " target(s) for updates...", NamedTextColor.YELLOW));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runUpdates(targets, sender));

        return true;
    }

    private void runUpdates(List<UpdateTarget> targets, CommandSender sender) {
        File updateFolder = plugin.getServer().getUpdateFolderFile();
        List<String> results = new ArrayList<>();
        boolean anyFailed = false;

        for (UpdateTarget target : targets) {
            if (target.repo() == null || target.repo().isBlank()) {
                results.add("[" + target.id() + "] Skipped - no repo configured");
                continue;
            }

            String tagKey = "update.tag-state." + target.id();
            String currentTag = plugin.getConfig().getString(tagKey, "");

            try {
                GitHubReleaseUpdater.Result result = updater.checkAndUpdate(target, currentTag, updateFolder);
                results.add(result.message);

                if (result.updated) {
                    plugin.getConfig().set(tagKey, result.newTag);
                }
            } catch (Exception e) {
                anyFailed = true;
                results.add("[" + target.id() + "] FAILED - " + e.getMessage());
                plugin.getLogger().severe("Update failed for " + target.id() + " (" + target.repo() + "): " + e);
            }
        }

        plugin.saveConfig();

        String summary = String.join("\n", results);
        NamedTextColor color = anyFailed ? NamedTextColor.RED : NamedTextColor.GREEN;

        sender.sendMessage(Component.text(summary, color));
        discordBroadcast(summary, sender);
    }

    private List<UpdateTarget> loadTargets() {
        List<UpdateTarget> targets = new ArrayList<>();
        List<Map<?, ?>> rawTargets = plugin.getConfig().getMapList("update.targets");

        for (Map<?, ?> raw : rawTargets) {
            String id = asString(raw.get("id"));
            String repo = asString(raw.get("repo"));
            String token = asString(raw.get("github-token"));
            String jarName = asString(raw.get("jar-name"));

            if (id == null || repo == null || jarName == null) {
                plugin.getLogger().warning("Skipping malformed update target entry: " + raw);
                continue;
            }

            targets.add(new UpdateTarget(id, repo, token, jarName));
        }

        return targets;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private void discordBroadcast(String message, CommandSender sender) {
        if (sender instanceof Player player) {
            discord.sendUpdateEmbeds(player, message);
        } else {
            String url = "https://cdn.discordapp.com/emojis/1189462346188472370.webp?size=48&name=PGokey&lossless=true";
            discord.sendGenericEmbed(message, Color.MAGENTA, sender.getName(), url, "Logs");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}