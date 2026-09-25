package org.thesandbox.core.commands.Admin;

//package org.thesandbox.core.commands.Admin;
//
//import com.google.gson.JsonArray;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
//import net.kyori.adventure.text.Component;
//import net.kyori.adventure.text.format.NamedTextColor;
//import org.bukkit.Bukkit;
//import org.bukkit.command.Command;
//import org.bukkit.command.CommandSender;
//import org.bukkit.entity.Player;
//import org.thesandbox.core.DiscordBridge;
//import org.thesandbox.core.TheSandboxCore;
//import org.thesandbox.core.commands.ISubCommand;
//
//import java.awt.*;
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.file.Files;
//import java.nio.file.StandardCopyOption;
//import java.util.List;
//import java.util.stream.Collectors;
//
//public class UpdateCommand implements ISubCommand {
//
//    private final TheSandboxCore plugin;
//    private final DiscordBridge discord;
//
//    String update_message;
//
//    public UpdateCommand(TheSandboxCore plugin, DiscordBridge discord) {
//        this.plugin = plugin;
//        this.discord = discord;
//    }
//
//    @Override
//    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
//        if (!sender.hasPermission("sandbox.superuser")) {
//            sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
//            return true;
//        }
//
//        String token = plugin.getConfig().getString("update.github-token", "");
//        String repo = plugin.getConfig().getString("update.repo", "");
//
//        if (token.isEmpty() || repo.isEmpty()) {
//            update_message = "Update is not configured (missing token or repo).";
//            discord_broadcast(update_message, sender);
//            sender.sendMessage(Component.text(update_message, NamedTextColor.RED));
//            return true;
//        }
//
//        sender.sendMessage(Component.text("Checking for updates...", NamedTextColor.YELLOW));
//
//        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
//            try {
//                String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
//                HttpURLConnection metaConn = openAuthedConnection(apiUrl, token);
//
//                String json;
//                try (BufferedReader reader = new BufferedReader(new InputStreamReader(metaConn.getInputStream()))) {
//                    json = reader.lines().collect(Collectors.joining());
//                }
//
//                String latestVersion = extractTagName(json);
//                long assetId = extractAssetId(json);
//
//                String lastKnownTag = plugin.getConfig().getString("update.last-installed-tag", "");
//
//                if (latestVersion.equals(lastKnownTag)) {
//                    update_message = "Already up to date! (" + latestVersion + ")";
//                    discord_broadcast(update_message, sender);
//                    sender.sendMessage(Component.text(update_message, NamedTextColor.GREEN));
//                    return;
//                }
//
//                String assetUrl = "https://api.github.com/repos/" + repo + "/releases/assets/" + assetId;
//                HttpURLConnection assetConn = openAuthedConnection(assetUrl, token);
//                assetConn.setRequestProperty("Accept", "application/octet-stream");
//
//                File updateFolder = plugin.getServer().getUpdateFolderFile();
//                if (!updateFolder.exists()) updateFolder.mkdirs();
//                File targetJar = new File(updateFolder, "thesandboxcore-1.0.0.jar");
//
//                try (InputStream in = assetConn.getInputStream()) {
//                    Files.copy(in, targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
//                }
//                update_message = "Downloaded update " + latestVersion + ". Restart to apply.";
//                sender.sendMessage(Component.text(update_message, NamedTextColor.GREEN));
//                discord_broadcast(update_message, sender);
//                plugin.getConfig().set("update.last-installed-tag", latestVersion);
//                plugin.saveConfig();
//
//            } catch (Exception e) {
//                update_message = "Update check failed: " + e.getMessage();
//                discord_broadcast(update_message, sender);
//                sender.sendMessage(Component.text(update_message, NamedTextColor.RED));
//                plugin.getLogger().severe("Update failed: " + e);
//            }
//        });
//
//        return true;
//    }
//
//    private HttpURLConnection openAuthedConnection(String urlStr, String token) throws Exception {
//        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
//        conn.setRequestProperty("Accept", "application/vnd.github+json");
//        conn.setRequestProperty("User-Agent", "TheSandboxCore-Updater");
//        conn.setRequestProperty("Authorization", "Bearer " + token);
//        return conn;
//    }
//
//    private String extractTagName(String json) {
//        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
//        return root.get("tag_name").getAsString();
//    }
//
//    private long extractAssetId(String json) {
//        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
//        JsonArray assets = root.getAsJsonArray("assets");
//
//        for (var element : assets) {
//            JsonObject asset = element.getAsJsonObject();
//            if (asset.get("name").getAsString().equals("thesandboxcore-1.0.0.jar")) {
//                return asset.get("id").getAsLong();
//            }
//        }
//
//        throw new IllegalStateException("Could not find thesandboxcore-1.0.0.jar in the latest release's assets.");
//    }
//
//
//
//    private void discord_broadcast(String message, CommandSender sender) {
//        if (sender instanceof Player player) {
//            discord.sendUpdateEmbeds(player, message);
//        } else { // console fallback (aka sender is not an instance of player)
//            String url = "https://cdn.discordapp.com/emojis/1189462346188472370.webp?size=48&name=PGokey&lossless=true";
//            discord.sendGenericEmbed(message,Color.MAGENTA, sender.getName(), url, "Logs");
//        }
//    }
//
//    @Override
//    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
//        return List.of();
//    }
//}


import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;

import java.util.List;

public class UpdateCommand implements ISubCommand {

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        Bukkit.dispatchCommand(sender, "updatelocal");
        sender.sendMessage(CommandMessages.error("This Command Has Been Deprecated in favor of /updatelocal & /updateallproxies"));
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}