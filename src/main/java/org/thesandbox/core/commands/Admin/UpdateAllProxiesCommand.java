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
import org.thesandbox.core.util.PterodactylBridge;

import java.awt.*;
import java.util.List;

public class UpdateAllProxiesCommand implements ISubCommand {

    private final TheSandboxCore plugin;
    private final DiscordBridge discord;
    private final UpdateLocalCommand localUpdateCommand;

    public UpdateAllProxiesCommand(TheSandboxCore plugin, DiscordBridge discord, UpdateLocalCommand localUpdateCommand) {
        this.plugin = plugin;
        this.discord = discord;
        this.localUpdateCommand = localUpdateCommand;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sandbox.superuser")) {
            Bukkit.getLogger().warning(sender.name() + " does not have permission to use this command.");
            sender.sendMessage(Component.text("You do not have permission to use this command!", NamedTextColor.RED));
            return true;
        }

        Bukkit.getLogger().warning(sender.name() + " ran up to here");

        localUpdateCommand.execute(sender, command, label, args);

        // 2. Ask the other proxy to update itself too.
        String panelUrl = plugin.getConfig().getString("update.remote-proxy.panel-url", "");
        String apiKey = plugin.getConfig().getString("update.remote-proxy.api-key", "");
        String remoteServerId = plugin.getConfig().getString("update.remote-proxy.server-id", "");

        if (panelUrl.isBlank() || apiKey.isBlank() || remoteServerId.isBlank()) {
            String msg = "Remote proxy not configured (update.remote-proxy.*) - only this proxy was updated.";
            sender.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
            discordBroadcast(msg, sender);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PterodactylBridge.sendConsoleCommand(panelUrl, apiKey, remoteServerId, "updatelocal");
                String msg = "Triggered /updatelocal on the remote proxy.";
                sender.sendMessage(Component.text(msg, NamedTextColor.GREEN));
                discordBroadcast(msg, sender);
            } catch (Exception e) {
                String msg = "Failed to trigger remote proxy update: " + e.getMessage();
                sender.sendMessage(Component.text(msg, NamedTextColor.RED));
                discordBroadcast(msg, sender);
                plugin.getLogger().severe("Remote proxy update trigger failed: " + e);
            }
        });

        return true;
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