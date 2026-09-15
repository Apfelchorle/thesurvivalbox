package org.thesandbox.core;

import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent; // <-- NEW
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.thesandbox.core.discord.model.ReportRecord;
import org.thesandbox.core.login.LoginService;
import org.thesandbox.core.util.PlayerDataKeys;
import org.thesandbox.core.util.PlayerDataListener;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordBridge extends ListenerAdapter
{
    private final TheSandboxCore plugin;
    private JDA jda;
    private final PlayerDataListener playerDataListener;

    // Channels
    private TextChannel staffChannel;

    private TextChannel schemuploadsChannel;

    private TextChannel logsChannel;
    private TextChannel chatChannel;              // public chat
    private TextChannel reportsChannel;
    private TextChannel archivedReportsChannel;

    // ===== Managed Discord role IDs (rank roles) =====
    private static final String ROLE_MB      = "1545155091303243807"; // Master Builder [NOTE: Please DO NOT EDIT THE ROLE IN DISCORD!!!]
    private static final String ROLE_MOD     = "1395155320539578510"; // Moderator [Doesnt Exist]
    private static final String ROLE_ADMIN   = "1475574066499817514"; // Admin [Me & Livi have this role]
    private static final String ROLE_SRADMIN = "1160233133837385809"; // Senior Admin [This is OP in discord]
    private static final String ROLE_DEV     = "1397614604640587837"; // Developer [Doesnt Exist]

    // Verified role (configurable with fallback)
    private static final String DEFAULT_VERIFIED_ROLE = "1415327115150360617";
    private static final String DEFAULT_MEMBER_ROLE_ID = "1227366929426157578";

    // ===== Reports indexing (original style) =====
    private final Map<String, ReportRecord> byId = new HashMap<>();
    private final Map<String, String> byMessageId = new HashMap<>();
    private final Map<String, Deque<String>> byPlayer = new HashMap<>();

    // Constructors
    public DiscordBridge(TheSandboxCore plugin, PlayerDataListener playerDataListener) {
        this.plugin = plugin; this.playerDataListener = playerDataListener;
    }

    public boolean start() {
        try {
            if (!plugin.getConfig().getBoolean("discord.enabled", false)) {
                plugin.getLogger().info("[Discord] Disabled by config.");
                return false;
            }

            String token = cfg("discord.token", "discord.token");
            if (token == null || token.isEmpty()) {
                plugin.getLogger().warning("[Discord] No token configured.");
                return false;
            }

            this.jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS, // needed for member join events + role ops
                            GatewayIntent.DIRECT_MESSAGES
                    )
                    .addEventListeners(this)
                    .build();

            this.jda.awaitReady();

            // Clear any old GLOBAL commands then register per-guild (shows in channels fast)
            try { jda.updateCommands().addCommands().queue(); } catch (Throwable ignored) {}

            for (Guild g : jda.getGuilds()) {
                registerGuildCommands(g);
            }

            // Prime channels so misconfigs log on startup
            getStaffChannel();
            getChatChannel();
            getReportsChannel();
            getArchivedReportsChannel();
            applyChatBridgePermissionState();

            // Listeners
            Bukkit.getPluginManager().registerEvents(new PublicChatBridgeListener(plugin, this), plugin);
            Bukkit.getPluginManager().registerEvents(new ServerActivityListener(), plugin);

            // ✅ Server start embed (to public chat channel)
            sendServerStartEmbed();

            // 🔄 Auto-archive any leftover report messages
            autoArchiveExistingReportsOnStartup();

            plugin.getLogger().info("[Discord] Connected.");
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[Discord] Startup interrupted.");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] Failed to start: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        try {
            sendServerStopEmbedBlocking();
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] Failed to send stop embed: " + e.getMessage());
        }

        try {
            if (jda != null) jda.shutdownNow();
        } catch (Exception ignored) {}

        jda = null;
        schemuploadsChannel = null;
        staffChannel = null;
        logsChannel = null;
        chatChannel = null;
        reportsChannel = null;
        archivedReportsChannel = null;
    }

    public boolean isReady() {
        return jda != null;
    }

    /* --------------------------- Command registration --------------------------- */
    private void registerGuildCommands(Guild g) {
        g.updateCommands().addCommands(

                /* /console */
                Commands.slash("console", "run any command ingame with console authority")
                        .addOptions(
                                new OptionData(OptionType.STRING, "command", "command to run", true)
                        )
                        .setGuildOnly(true),

                /* /update */
                Commands.slash("update", "Run /update ingame")
                        .setGuildOnly(true),

                // /uploadschem
                Commands.slash("uploadschem", "Upload A Schem File To The Server!")
                        .addOptions(
                                new OptionData(OptionType.ATTACHMENT, "schem", "a .schem file", true)
                        )
                        .setGuildOnly(true),

                // /list – show online player list
                Commands.slash("list", "Show the online player list, formatted like /list in-game")
                        .setGuildOnly(true),

                // /masterbuilder – set a player's rank to masterbuilder (everyone can use)
                Commands.slash("masterbuilder", "Set a player's rank to Master Builder")
                        .addOptions(
                                new OptionData(OptionType.STRING, "username", "Minecraft username to promote", true)
                        )
                        .setGuildOnly(true),

                // /chatbridge – admin-only toggle for MC <-> Discord public/staff chat bridging
                Commands.slash("chatbridge", "Enable, disable, or view Minecraft/Discord chat bridging")
                        .addOptions(
                                new OptionData(OptionType.STRING, "state", "enable, disable, or status", true)
                                        .addChoice("enable", "enable")
                                        .addChoice("disable", "disable")
                                        .addChoice("status", "status")
                        )
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),

                // /devtoggle discord – admin-only Discord-side equivalent of the in-game /devtoggle discord
                Commands.slash("devtoggle", "Developer toggles for Minecraft/Discord systems")
                        .addOptions(
                                new OptionData(OptionType.STRING, "target", "system to toggle", true)
                                        .addChoice("discord", "discord"),
                                new OptionData(OptionType.STRING, "action", "toggle, enable, disable, or status", true)
                                        .addChoice("toggle", "toggle")
                                        .addChoice("enable", "enable")
                                        .addChoice("disable", "disable")
                                        .addChoice("status", "status")
                        )
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue(
                __ -> plugin.getLogger().info("[Discord] Registered slash cmds in: " + g.getName()),
                err -> plugin.getLogger().warning("[Discord] Slash cmd register failed in " + g.getName() + ": " + err.getMessage())
        );
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        registerGuildCommands(event.getGuild());
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        registerGuildCommands(event.getGuild());
    }

    /* --------------------------- NEW: Auto-restore on member rejoin --------------------------- */
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        // Currently unused, but kept for future auto-restore logic
    }

    private Role getVerifiedRole(Guild g) {
        String verifiedRoleId = plugin.getConfig().getString("discord.verified-role-id", DEFAULT_VERIFIED_ROLE);
        if (verifiedRoleId == null || verifiedRoleId.isEmpty()) return null;
        return g.getRoleById(verifiedRoleId);
    }

    /**
     * Reflection-based lookup so this class doesn't depend on a specific VerificationService API.
     * It tries common method names:
     *  - Optional<?> findByDiscordId(long)
     *  - Optional<?> getLinkedByDiscordId(long)
     *  - Optional<?> lookupByDiscordId(long)
     *  - Optional<?> getLinkByDiscordId(long)
     * And then extracts fields/getters "mcName"/"name" and "uuid"/"uniqueId".
     */
    private UUID tryExtractUuid(Object record) {
        // Try common getters
        String[] getters = {"getUuid", "uuid", "getUniqueId", "uniqueId"};
        for (String g : getters) {
            try {
                Method m = record.getClass().getMethod(g);
                Object v = m.invoke(record);
                if (v instanceof UUID) return (UUID) v;
                if (v instanceof String s) return UUID.fromString(s);
            } catch (Throwable ignored) {}
        }

        // Try fields
        String[] fields = {"uuid", "uniqueId"};
        for (String f : fields) {
            try {
                Field fld = record.getClass().getDeclaredField(f);
                fld.setAccessible(true);
                Object v = fld.get(record);
                if (v instanceof UUID) return (UUID) v;
                if (v instanceof String s) return UUID.fromString(s);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private String tryExtractName(Object record) {
        // Try getters
        String[] getters = {"getMcName", "mcName", "getName", "name"};
        for (String g : getters) {
            try {
                Method m = record.getClass().getMethod(g);
                Object v = m.invoke(record);
                if (v != null) return String.valueOf(v);
            } catch (Throwable ignored) {}
        }

        // Try fields
        String[] fields = {"mcName", "name"};
        for (String f : fields) {
            try {
                Field fld = record.getClass().getDeclaredField(f);
                fld.setAccessible(true);
                Object v = fld.get(record);
                if (v != null) return String.valueOf(v);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /* --------------------------- Config helpers --------------------------- */
    private String cfg(String hyphen, String underscoreFallback) {
        String v = plugin.getConfig().getString(hyphen);
        if (v == null || v.isEmpty())
            v = plugin.getConfig().getString(underscoreFallback, "");
        return v == null ? "" : v;
    }

    private TextChannel getStaffChannel() {
        if (jda == null) return null;
        if (staffChannel != null) return staffChannel;
        String id = cfg("discord.channel-id", "discord.channel_id");
        if (id == null || id.isEmpty()) return null;
        staffChannel = jda.getTextChannelById(id);
        if (staffChannel == null) plugin.getLogger().warning("[Discord] Staff channel not found: " + id);
        return staffChannel;
    }

    private TextChannel getSchemUploadsChannel() {
        if (jda == null) return null;
        if (schemuploadsChannel != null) return schemuploadsChannel;
        String id = cfg("discord.schem-uploads", "discord.schem_uploads");
        if (id == null || id.isEmpty()) return null;
        schemuploadsChannel = jda.getTextChannelById(id);
        if (schemuploadsChannel == null) plugin.getLogger().warning("[Discord] Schem uploads channel not found: " + id);
        return schemuploadsChannel;
    }

    private TextChannel getStaffLogsChannel() {
        if (jda == null) return null;
        if (logsChannel != null) return logsChannel;
        String id = cfg("discord.logs-channel-id", "discord.logs_channel_id");
        if (id == null || id.isEmpty()) return null;
        logsChannel = jda.getTextChannelById(id);
        if (logsChannel == null) plugin.getLogger().warning("[Discord] Logs channel not found: " + id);
        return logsChannel;
    }

    private TextChannel getChatChannel() {
        if (jda == null) return null;
        if (chatChannel != null) return chatChannel;

        String id = cfg("discord.chat-channel-id", "discord.chat_channel_id");

        // Backwards compatibility: older configs only used discord.channel-id.
        // If a separate public chat channel is not configured, use that channel too.
        if (id == null || id.isEmpty()) {
            id = cfg("discord.channel-id", "discord.channel_id");
        }

        if (id == null || id.isEmpty()) {
            plugin.getLogger().warning("[Discord] No public chat channel configured. Set discord.chat-channel-id, or discord.channel-id as fallback.");
            return null;
        }

        chatChannel = jda.getTextChannelById(id);
        if (chatChannel == null) plugin.getLogger().warning("[Discord] Chat channel not found: " + id);
        return chatChannel;
    }

    public void applyChatBridgePermissionState() {
        TextChannel ch = getChatChannel();
        if (ch == null) return;

        String memberRoleId = plugin.getConfig().getString("discord.chat-bridge.member-role-id", DEFAULT_MEMBER_ROLE_ID);
        if (memberRoleId == null || memberRoleId.isBlank()) return;

        Role memberRole = ch.getGuild().getRoleById(memberRoleId);
        if (memberRole == null) {
            plugin.getLogger().warning("[Discord] Member role not found for chat bridge permission toggle: " + memberRoleId);
            return;
        }

        boolean enabled = plugin.isDiscordChatBridgeEnabled();
        if (enabled) {
            ch.upsertPermissionOverride(memberRole)
                    .grant(Permission.MESSAGE_SEND)
                    .queue(
                            ok -> plugin.getLogger().info("[Discord] Chat bridge enabled; granted Send Messages for Member role in MC chat channel."),
                            err -> plugin.getLogger().warning("[Discord] Failed to restore Member Send Messages permission: " + err.getMessage())
                    );
        } else {
            ch.upsertPermissionOverride(memberRole)
                    .deny(Permission.MESSAGE_SEND)
                    .queue(
                            ok -> plugin.getLogger().info("[Discord] Chat bridge disabled; denied Send Messages for Member role in MC chat channel."),
                            err -> plugin.getLogger().warning("[Discord] Failed to deny Member Send Messages permission: " + err.getMessage())
                    );
        }
    }

    private TextChannel getReportsChannel() {
        if (jda == null) return null;
        if (reportsChannel != null) return reportsChannel;
        String id = cfg("discord.reports-channel-id", "discord.reports_channel_id");
        if (id == null || id.isEmpty()) return null;
        reportsChannel = jda.getTextChannelById(id);
        if (reportsChannel == null) plugin.getLogger().warning("[Discord] Reports channel not found: " + id);
        return reportsChannel;
    }

    private TextChannel getArchivedReportsChannel() {
        if (jda == null) return null;
        if (archivedReportsChannel != null) return archivedReportsChannel;
        String id = cfg("discord.archived-reports-channel-id", "discord.archived_reports_channel_id");
        if (id == null || id.isEmpty()) return null;
        archivedReportsChannel = jda.getTextChannelById(id);
        if (archivedReportsChannel == null) plugin.getLogger().warning("[Discord] Archived reports channel not found: " + id);
        return archivedReportsChannel;
    }

    /* -------------------- Staff chat: MC -> Discord -------------------- */
    public void sendStaffMessageFromMinecraft(String groupTag, String name, String message)
    {
        if (!plugin.isDiscordChatBridgeEnabled()) return;
        TextChannel ch = getStaffChannel();
        if (ch == null) return;

        String g = ChatColor.stripColor(groupTag == null ? "Player" : groupTag).trim();
        String n = ChatColor.stripColor(name == null ? "" : name).trim();
        String m = replaceLinksWithMediaTag(ChatColor.stripColor(message == null ? "" : message)).trim();

        String line = "**[" + g + "] " + n + "** » " + m;
        String safeLine = antiPingEveryoneHere(line);

        boolean embeds = plugin.getConfig().getBoolean("discord.use-embeds", false);
        if (embeds) {
            ch.sendMessageEmbeds(new EmbedBuilder()
                    .setDescription(safeLine)
                    .build()).queue();
        } else {
            ch.sendMessage(safeLine).queue();
        }
    }

    /* ---------------------- Public chat: MC -> Discord ---------------------- */
    public void sendPublicMessageFromMinecraft(Player player, String message) {
        if (!plugin.isDiscordChatBridgeEnabled()) return;
        TextChannel ch = getChatChannel();
        if (ch == null) return;

        String template = plugin.getConfig().getString(
                "discord.chat.mc-to-discord-format",
                "**%rank%%player%** » %message%"
        );

        // Discord should show the server rank, not the player's guild/tag/LuckPerms prefix.
        // Example: [Dev] pixelpaladinn instead of [Boss] pixelpaladinn.
        String rankPrefix = rankPrefixForDiscord(player);

        String out = template
                // Keep this replacement for older configs that used %luckperms_prefix%; it now resolves to the real rank.
                .replace("%luckperms_prefix%", rankPrefix)
                .replace("%rank%", rankPrefix)
                .replace("%rank_prefix%", rankPrefix)
                .replace("%player%", player.getName())
                .replace("%message%", replaceLinksWithMediaTag(stripAllColors(message)));

        String safeOut = antiPingEveryoneHere(out);

        if (plugin.getConfig().getBoolean("discord.use-embeds", false)) {
            ch.sendMessageEmbeds(new EmbedBuilder().setDescription(safeOut).build()).queue(
                    ok -> {},
                    err -> {
                        plugin.getLogger().warning("[Discord] Failed to send public chat embed, trying plain message: " + err.getMessage());
                        ch.sendMessage(safeOut).queue(
                                ok2 -> {},
                                err2 -> plugin.getLogger().warning("[Discord] Failed to send public chat message: " + err2.getMessage())
                        );
                    }
            );
        } else {
            ch.sendMessage(safeOut).queue(
                    ok -> {},
                    err -> plugin.getLogger().warning("[Discord] Failed to send public chat message: " + err.getMessage())
            );
        }
    }

    // erm,  class --> public this ---> private that
    public void sendUpdateEmbeds(Player player, String message) {
        sendUpdateEmbed(message, player);
    }
    public void sendVanishEmbeds(Player player, String message) { sendVanishEmbed(player, message); }
    //

    private String rankPrefixForDiscord(Player player) {
        LoginService.Rank rank = LoginService.Rank.DEFAULT;
        try {
            if (plugin.getLoginService() != null) {
                rank = plugin.getLoginService().getRank(player);
            }
        } catch (Throwable ignored) {
            // Fall back to direct permission checks if LoginService is unavailable.
            if (player != null) {
                if (player.hasPermission("sandbox.operator")) rank = LoginService.Rank.OPERATOR;
                else if (player.hasPermission("sandbox.admin")) rank = LoginService.Rank.ADMIN;
                else if (player.hasPermission("sandbox.staff")) rank = LoginService.Rank.STAFF;
                else if (player.hasPermission("sandbox.mb")) rank = LoginService.Rank.MB;
                else if (player.hasPermission("sandbox.vip")) rank = LoginService.Rank.VIP;
            }
        }

        return discordRankPrefix(rank);
    }

    /* ---------------------- Discord -> MC (staff & public) ---------------------- */
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        String channelId = event.getChannel().getId();

        // --- Staff channel -> in-game staff chat ---
        TextChannel staff = getStaffChannel();
        if (staff != null && Objects.equals(channelId, staff.getId())) {
            if (!plugin.isDiscordChatBridgeEnabled()) return;
            String template = plugin.getConfig().getString(
                    "staffchat-from-discord-format",
                    "&8[&3&lDISCORD&8] | [&b&lSTAFF&8]&r %role%%rolecolor%%name% &8» &f%message%"
            );

            Member m = event.getMember();
            String roleName = "";
            String roleColorCode = "";

            if (m != null && !m.getRoles().isEmpty()) {
                Role top = m.getRoles().get(0);
                roleName = top.getName() != null ? top.getName() : "";
                java.awt.Color c = top.getColor();
                if (c != null) roleColorCode = toLegacyHex(c);
            }

            String name = (m != null ? m.getEffectiveName() : event.getAuthor().getName());
            String msg  = event.getMessage().getContentDisplay();

            String bracketedRole = "";
            if (!roleName.isEmpty()) {
                bracketedRole = "&8[" + (roleColorCode == null ? "" : roleColorCode) + roleName + "&8] ";
            }

            final String MESSAGE_TOKEN = "%MESSAGE_TOKEN%";

            String outAmpWithToken = template
                    .replace("%role%", bracketedRole)
                    .replace("%rolecolor%", roleColorCode == null ? "" : roleColorCode)
                    .replace("%name%", name)
                    .replace("%message%", MESSAGE_TOKEN)
                    .replace("  ", " ");

            // Collect media: attachments + direct URLs (any link now)
            java.util.List<String> mediaUrls = new java.util.ArrayList<>();
            List<Message.Attachment> atts = event.getMessage().getAttachments();
            for (Message.Attachment att : atts) mediaUrls.add(att.getUrl());
            mediaUrls.addAll(extractAllUrls(msg));
            addEmbedMediaPlaceholders(event.getMessage(), mediaUrls);

            if (mediaUrls.isEmpty()) {
                String outAmpFull = template
                        .replace("%role%", bracketedRole)
                        .replace("%rolecolor%", roleColorCode == null ? "" : roleColorCode)
                        .replace("%name%", name)
                        .replace("%message%", msg)
                        .replace("  ", " ");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.hasPermission("sandbox.staff")) continue;
                        String perViewer = highlightDiscordMentionAmpersand(outAmpFull, p.getName(), "&f");
                        boolean ping = !perViewer.equals(outAmpFull);
                        p.sendMessage(HexColorUtil.translate(perViewer));
                        if (ping) {
                            try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1337F, 0.9F); } catch (Throwable ignored) {}
                        }
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.hasPermission("sandbox.staff")) continue;

                        String perViewer = highlightDiscordMentionAmpersand(outAmpWithToken, p.getName(), "&f");
                        int i = perViewer.indexOf(MESSAGE_TOKEN);
                        String before = i >= 0 ? perViewer.substring(0, i) : perViewer;
                        String after  = i >= 0 ? perViewer.substring(i + MESSAGE_TOKEN.length()) : "";

                        boolean ping = messageMentions(msg, p.getName());

                        if (mediaUrls.isEmpty()) {
                            String full = (i >= 0) ? (before + msg + after) : perViewer;
                            p.sendMessage(HexColorUtil.translate(full));
                        } else {
                            String cleanMsg = stripAllUrls(msg).trim();
                            BaseComponent[] messageComps = cleanMsg.isBlank()
                                    ? new BaseComponent[0]
                                    : legacy(cleanMsg + " ");
                            BaseComponent[] mediaComps = clickableMediaTagOrLegacy("&e[Media]", firstClickableMediaUrl(mediaUrls));
                            BaseComponent[] finalMsg = join(
                                    legacy(before),
                                    messageComps,
                                    mediaComps,
                                    legacy(after)
                            );
                            p.spigot().sendMessage(finalMsg);
                        }

                        if (ping) {
                            try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1337F, 0.9F); } catch (Throwable ignored) {}
                        }
                    }
                });
            }
            return;
        }

        // --- Public chat channel -> in-game public chat ---
        TextChannel chat = getChatChannel();
        if (chat != null && Objects.equals(channelId, chat.getId())) {
            if (!plugin.isDiscordChatBridgeEnabled()) return;
            String template = plugin.getConfig().getString(
                    "discord.chat.discord-to-mc-format",
                    "%role% %user% &8» &r%message%"
            );

            Member m = event.getMember();
            String roleName = "";
            String roleColorCode = "";

            if (m != null && !m.getRoles().isEmpty()) {
                Role top = m.getRoles().get(0);
                roleName = top.getName() != null ? top.getName() : "";
                java.awt.Color c = top.getColor();
                if (c != null) roleColorCode = toLegacyHex(c);
            }

            boolean noRoles = (m == null || m.getRoles().isEmpty());
            String userBase = (m != null ? m.getEffectiveName() : event.getAuthor().getName());
            String userColored = ((roleColorCode != null && !roleColorCode.isEmpty()) ? roleColorCode : "&a") + userBase;

            String roleFormatted = "";
            if (!roleName.isEmpty()) {
                roleFormatted = "&8[" + (roleColorCode == null ? "" : roleColorCode) + roleName + "&8]";
            }

            String msg = event.getMessage().getContentDisplay();
            final String MESSAGE_TOKEN = "%MESSAGE_TOKEN%";

            String outAmpWithToken = template
                    .replace("%role%", roleFormatted)
                    .replace("%roletag%", roleFormatted)
                    .replace("%rolecolor%", roleColorCode == null ? "" : roleColorCode)
                    .replace("%user%", userColored)
                    .replace("%message%", MESSAGE_TOKEN)
                    .replace("  ", " ");

            // Collect media: attachments + direct URLs (any link now)
            java.util.List<String> mediaUrls = new java.util.ArrayList<>();
            List<Message.Attachment> atts = event.getMessage().getAttachments();
            for (Message.Attachment att : atts) mediaUrls.add(att.getUrl());
            mediaUrls.addAll(extractAllUrls(msg));
            addEmbedMediaPlaceholders(event.getMessage(), mediaUrls);

            if (mediaUrls.isEmpty()) {
                String outAmpFull = template
                        .replace("%role%", roleFormatted)
                        .replace("%roletag%", roleFormatted)
                        .replace("%rolecolor%", roleColorCode == null ? "" : roleColorCode)
                        .replace("%user%", userColored)
                        .replace("%message%", msg)
                        .replace("  ", " ");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        String perViewer = highlightDiscordMentionAmpersand(outAmpFull, p.getName());
                        boolean ping = !perViewer.equals(outAmpFull);
                        p.sendMessage(HexColorUtil.translate(perViewer));
                        if (ping) {
                            try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1337F, 0.9F); } catch (Throwable ignored) {}
                        }
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        String perViewer = highlightDiscordMentionAmpersand(outAmpWithToken, p.getName());
                        int i = perViewer.indexOf(MESSAGE_TOKEN);
                        String before = i >= 0 ? perViewer.substring(0, i) : perViewer;
                        String after  = i >= 0 ? perViewer.substring(i + MESSAGE_TOKEN.length()) : "";

                        boolean ping = messageMentions(msg, p.getName());

                        String cleanMsg = stripAllUrls(msg).trim();
                        BaseComponent[] messageComps = cleanMsg.isBlank()
                                ? new BaseComponent[0]
                                : legacy(cleanMsg + " ");
                        BaseComponent[] mediaComps = clickableMediaTagOrLegacy("&e[Media]", firstClickableMediaUrl(mediaUrls));
                        BaseComponent[] finalMsg = join(
                                legacy(before),
                                messageComps,
                                mediaComps,
                                legacy(after)
                        );
                        p.spigot().sendMessage(finalMsg);

                        if (ping) {
                            try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1337F, 0.9F); } catch (Throwable ignored) {}
                        }
                    }
                });
            }
        }
    }

    /* -------------------- Slash commands: ONLY /list and /masterbuilder -------------------- */
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String cmd = event.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("update")) {
            handleUpdate(event);
            return;
        }

        if (cmd.equals("list")) {
            handleList(event);
            return;
        }

        if(cmd.equals("console")) {
            IssueInGameCommand(event);
            return;
        }

//        if (cmd.equals("ban")) {
//            return;
//        }

        if (cmd.equals("uploadschem")) {
            handleSchemUploads(event);
            return;
        }

        if (cmd.equals("masterbuilder")) {
            handleMasterbuilder(event);
            return;
        }

        if (cmd.equals("chatbridge")) {
            handleChatBridge(event);
            return;
        }

        if (cmd.equals("devtoggle")) {
            handleDevToggle(event);
        }
    }

    private void handleChatBridge(@NotNull SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need the Discord Administrator permission to use this command.").setEphemeral(true).queue();
            return;
        }

        OptionMapping stateOpt = event.getOption("state");
        String state = stateOpt == null ? "" : stateOpt.getAsString().toLowerCase(Locale.ROOT);
        if (state.equals("status")) {
            event.reply("The Minecraft/Discord chat bridge is currently " + (plugin.isDiscordChatBridgeEnabled() ? "enabled" : "disabled") + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        handleChatBridgeState(event, state);
    }

    private void handleDevToggle(@NotNull SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need the Discord Administrator permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String target = Optional.ofNullable(event.getOption("target"))
                .map(OptionMapping::getAsString)
                .orElse("")
                .toLowerCase(Locale.ROOT);
        String action = Optional.ofNullable(event.getOption("action"))
                .map(OptionMapping::getAsString)
                .orElse("")
                .toLowerCase(Locale.ROOT);

        if (!target.equals("discord")) {
            event.reply("Use `/devtoggle discord toggle` or `/devtoggle discord status`.").setEphemeral(true).queue();
            return;
        }

        if (action.equals("status")) {
            event.reply("The Minecraft/Discord chat bridge is currently " + (plugin.isDiscordChatBridgeEnabled() ? "enabled" : "disabled") + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (action.equals("toggle")) {
            boolean desired = !plugin.isDiscordChatBridgeEnabled();
            plugin.setDiscordChatBridgeEnabled(desired);
            event.reply("Minecraft/Discord chat bridge has been " + (desired ? "enabled" : "disabled") + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        handleChatBridgeState(event, action);
    }

    private void handleUpdate(@NotNull SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        Boolean hasroles = hasRole(member, ROLE_ADMIN) || hasRole(member, ROLE_SRADMIN);

        if (guild == null) {
            event.reply("Not In Guild").queue();
            return;
        }

        if (member == null || !hasroles) {
            event.reply("Not Authorized").queue();
            return;
        }

        IssueInGameCommand(event);
    }

    private void handleChatBridgeState(@NotNull SlashCommandInteractionEvent event, String state) {
        boolean desired;
        if (state.equals("enable") || state.equals("enabled") || state.equals("on")) {
            desired = true;
        } else if (state.equals("disable") || state.equals("disabled") || state.equals("off")) {
            desired = false;
        } else {
            event.reply("Use `enable`, `disable`, `toggle`, or `status`.").setEphemeral(true).queue();
            return;
        }

        boolean current = plugin.isDiscordChatBridgeEnabled();
        if (current == desired) {
            event.reply("The Minecraft/Discord chat bridge is already " + (desired ? "enabled" : "disabled") + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        plugin.setDiscordChatBridgeEnabled(desired);
        event.reply("Minecraft/Discord chat bridge has been " + (desired ? "enabled" : "disabled") + ".")
                .setEphemeral(true)
                .queue();
    }

    // ===== Used by older moderation commands (kept for now but unused) =====
        private record ParseResult(boolean ok, boolean permanent, String durationToken, String error) {

        static ParseResult okPerm() {
            return new ParseResult(true, true, null, null);
        }

        static ParseResult okWith(String tok) {
            return new ParseResult(true, false, tok, null);
        }

        static ParseResult fail(String msg) {
            return new ParseResult(false, false, null, msg);
        }
        }

    /** Accepts Ns/Nm/Nd (max 1d). Senior Admin may use "0" for permanent. */
    private ParseResult parseDuration(String raw, boolean isSrAdmin) {
        if (raw == null) return ParseResult.fail("Duration is required.");
        String s = raw.trim().toLowerCase(Locale.ROOT);

        if (s.equals("0")) {
            return isSrAdmin ? ParseResult.okPerm()
                    : ParseResult.fail("Only Senior Admin may use 0 for a permanent ban.");
        }

        Matcher m = Pattern.compile("^(\\d+)([smd])$").matcher(s);
        if (!m.matches()) {
            return ParseResult.fail("Duration must be like 30s, 10m, or 1d (or 0 for Senior Admin).");
        }

        long n = Long.parseLong(m.group(1));
        char u = m.group(2).charAt(0);

        if (n <= 0) return ParseResult.fail("Duration must be greater than 0.");

        long seconds = switch (u) {
            case 's' -> n;
            case 'm' -> n * 60L;
            case 'd' -> n * 86400L;
            default  -> Long.MAX_VALUE;
        };

        if (seconds > 86400L) return ParseResult.fail("Duration cannot exceed 1d.");
        if (u == 'd' && n > 1) return ParseResult.fail("Maximum is 1d.");

        return ParseResult.okWith(n + String.valueOf(u));
    }

    /* --- Thingy For Discord --- */

    private void IssueInGameCommand(@NotNull SlashCommandInteractionEvent event) {
        Guild g = event.getGuild();
        Member m = event.getMember();
        if (g == null) {
            event.reply("Please run this command in a server channel, not in DMs.")
                    .setEphemeral(true).queue();
            return;
        }
        boolean requesterPrivileged = hasRole(m, ROLE_SRADMIN) || hasRole(m, ROLE_ADMIN);

        if (!(requesterPrivileged)) {
            event.reply("You do not have permission to use this command.")
                    .queue();
            return;
        }

        OptionMapping CmdOPT = event.getOption("command");
        if (CmdOPT == null || CmdOPT.getAsString().trim().isEmpty()) {
            event.reply("You must provide a Minecraft command. Usage: `/command <command>`.")
                    .setEphemeral(true).queue();
            return;
        }

        String command = CmdOPT.getAsString();

        event.deferReply(true).queue(hook -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String cmd = command;
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                if (ok) {
                    hook.editOriginal("Ran Command: " + cmd)
                            .queue();
                    sendGenericEmbed("Ran Command: " + cmd, new Color(0x570000),event.getMember().getUser().getName(),event.getMember().getAvatarUrl(), "Logs");
                } else {
                    hook.editOriginal("Failed to execute: " + cmd)
                            .queue();
                    sendGenericEmbed("Failed To Run Command: " + cmd, new Color(0x570000),event.getMember().getUser().getName(),event.getMember().getAvatarUrl(), "Logs");
                }
            });
        });
    }


    /*               /upload-schem for discord                            */




    /* -------------------- /list implementation -------------------- */
    private void handleList(@NotNull SlashCommandInteractionEvent event) {
        Guild g = event.getGuild();
        Member m = event.getMember();
        if (g == null || m == null) {
            event.reply("Please run this command in a server channel, not in DMs.")
                    .setEphemeral(true).queue();
            return;
        }

        // The requester can see vanished players (and see vanish markers) if they are Mod/Admin/Senior Admin
        boolean requesterPrivileged = hasRole(m, ROLE_MOD) || hasRole(m, ROLE_ADMIN) || hasRole(m, ROLE_SRADMIN);

        java.util.List<String> staffNames = new java.util.ArrayList<>();
        java.util.List<String> playerNames = new java.util.ArrayList<>();
        int vanishedCount = 0; // total vanished online (for footer visible to privileged users)

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean vanished = false;
            try {
                vanished = de.myzelyam.api.vanish.VanishAPI.isInvisible(p);
            } catch (Throwable ignored) {
                // Vanish API missing or failed — treat as not vanished
            }
            if (vanished) vanishedCount++;

            // Non-staff does not see vanished players at all
            if (vanished && !requesterPrivileged) {
                continue;
            }

            // Determine the player's rank
            LoginService.Rank rank = LoginService.Rank.DEFAULT;
            boolean isStaffByRank = false;
            try {
                rank = plugin.getLoginService().getRank(p);
                isStaffByRank = isStaffRank(rank); // MB is not staff here
            } catch (Throwable ignored) {
                // Fallback to staff classification by permission only (no prefix if rank unknown)
                isStaffByRank = p.hasPermission("sandbox.staff");
            }

            // Build label to match the in-game prefix style as closely as Discord allows.
            String label = discordRankPrefix(rank) + p.getName();
            if (vanished && requesterPrivileged) {
                label += " *(VANISHED)*";
            }

            // MB should appear in Players even though it has a prefix
            if (isStaffByRank) {
                staffNames.add(label);
            } else {
                playerNames.add(label);
            }
        }

        java.util.Collections.sort(staffNames, String.CASE_INSENSITIVE_ORDER);
        java.util.Collections.sort(playerNames, String.CASE_INSENSITIVE_ORDER);

        int totalVisible = staffNames.size() + playerNames.size();

        String staffBody  = staffNames.isEmpty()  ? "_None_" : String.join(", ", staffNames);
        String playerBody = playerNames.isEmpty() ? "_None_" : String.join(", ", playerNames);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new java.awt.Color(50, 205, 50))
                .setTitle("Online Players (" + totalVisible + ")")
                .addField("Staff (" + staffNames.size() + ")", staffBody, false)
                .addField("Players (" + playerNames.size() + ")", playerBody, false)
                .setTimestamp(java.time.Instant.now());

        // Only privileged users get a vanished summary footer
        if (requesterPrivileged && vanishedCount > 0) {
            eb.setFooter(vanishedCount + " vanished");
        }

        // Only the requester should see it
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    /* -------------------- /masterbuilder implementation -------------------- */
    private void handleMasterbuilder(@NotNull SlashCommandInteractionEvent event) {
        Guild g = event.getGuild();
        Member m = event.getMember();
        if (g == null) {
            event.reply("Please run this command in a server channel, not in DMs.")
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping userOpt = event.getOption("username");
        if (userOpt == null || userOpt.getAsString().trim().isEmpty()) {
            event.reply("You must provide a Minecraft username. Usage: `/masterbuilder <username>`.")
                    .setEphemeral(true).queue();
            return;
        }

        String username = userOpt.getAsString().trim();
        boolean requesterPrivileged = hasRole(m, ROLE_ADMIN) || hasRole(m, ROLE_SRADMIN) || hasRole(m, ROLE_MOD) || hasRole(m, ROLE_DEV);

        if (!(requesterPrivileged)) {
            event.reply("You are not allowed to run this command.").queue();
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        Player p = Bukkit.getPlayer(offlinePlayer.getUniqueId());

        if (p == null) {
            event.reply("That player is not online Or Does Not Exist").queue();
            return;
        }

        boolean rankedabove = p.hasPermission("sandbox.staff") || p.hasPermission("sandbox.superuser");

        if (rankedabove) {
            event.reply("Player Already Possess A Higher Rank")
                    .queue();
            return;
        }


        event.deferReply(true).queue(hook -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String cmd = "rank set " + username + " masterbuilder";
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                if (ok) {
                    hook.editOriginal("Set rank for **" + username + "** to `masterbuilder` via console.")
                            .queue();
                } else {
                    hook.editOriginal("Failed to execute `rank set " + username + " masterbuilder` in the console.")
                            .queue();
                }
            });
        });
    }

    // SCHEM UPLOADS LOGIC

    private static final long MAX_SCHEM_SIZE = 20 * 1024 * 1024; // 20 MegaBytes
    private final Map<Long, Long> lastSchemUploadMs = new ConcurrentHashMap<>();

    private void handleSchemUploads(@NotNull SlashCommandInteractionEvent event) {
        Guild g = event.getGuild();
        Member m = event.getMember();
        if (g == null) {
            event.reply("Please run this command in a server channel, not in DMs.")
                    .setEphemeral(true).queue();
            return;
        }

        boolean requesterPrivileged = hasRole(m, ROLE_ADMIN) || hasRole(m, ROLE_SRADMIN)
                || hasRole(m, ROLE_MOD) || hasRole(m, ROLE_DEV) || hasRole(m, ROLE_MB);

        if (!requesterPrivileged) {
            event.reply("You are not allowed to run this command.")
                    .setEphemeral(true).queue();
            return;
        }

        long nowMs = System.currentTimeMillis();
        long lastMs = lastSchemUploadMs.getOrDefault(m.getIdLong(), 0L);

        if (checkSpam(lastMs, nowMs)) {
            event.reply("You Are Running This Command Too Quickly").setEphemeral(true).queue();
            return;
        }
        lastSchemUploadMs.put(m.getIdLong(), nowMs);


        OptionMapping fileOpt = event.getOption("schem");
        if (fileOpt == null) {
            event.reply("You must provide a Valid File.")
                    .setEphemeral(true).queue();
            return;
        }

        Message.Attachment attachment = fileOpt.getAsAttachment();
        String ext = attachment.getFileExtension();
        if (ext == null || !ext.equalsIgnoreCase("schem")) {
            plugin.getLogger().warning("Invalid file extension: " + ext + " Uploaded File Name Info : " + fileOpt.getName());
            event.reply("You must provide a Valid Minecraft .schem File.")
                    .setEphemeral(true).queue();
            return;
        }

        if (attachment.getSize() > MAX_SCHEM_SIZE) {
            event.reply("That file is too large (max " + (MAX_SCHEM_SIZE / 1024 / 1024) + " MB).")
                    .setEphemeral(true).queue();
            return;
        }

        /* Protects Against Path Traversal and Various Other Exploits
         You Could For Some Of These Just Use attachment.getExtension
         No Idea How To Do That, Will Figure it out later if i stumbe upon this again
         */
        String rawName = attachment.getFileName();

        String baseName = Paths.get(rawName).getFileName().toString();

        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }

        String safeName = baseName
                .replaceAll("[\0\\/\\\\:\\*\\?\"<>\\|%\\$&;]", "_")
                + "_Schematica.schem";

        sendGenericEmbed( safeName + " Uploaded To Server Files", Color.BLUE, m.getNickname(), m.getAvatarUrl(), "Logs");
        sandboxSeesAll(m.getUser().getName(), safeName, attachment.getSize());

        event.deferReply(true).queue();

        createSchematicFile(safeName, attachment, event);
    }

    public boolean checkSpam(long time, long now) {
        long deltaTime = now - time;
        return deltaTime < 20000; // 20+ Seconds != Spam
    }

    public void sandboxSeesAll(String playerName, String fileName, int FileSize) {
        File logFile = new File(plugin.getDataFolder(), "schematics.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(logFile);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedFileSize = (FileSize / 1024 / 1024) + " MB).";
        String entry = fileName + " (Uploaded: " + timestamp + " FileSize: " + formattedFileSize + ")";

        List<String> userSchematics = config.getStringList(playerName + ".Owned Schematics");
        userSchematics.add(entry);

        config.set(playerName + ".Uploaded Schems", userSchematics);

        try {
            config.save(logFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save schematics.yml: " + e.getMessage());
        }
    }

    public void createSchematicFile(String fileName, Message.Attachment attachment,
                                    SlashCommandInteractionEvent event) {
        Plugin fawe = Bukkit.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");

        if (fawe == null || !fawe.isEnabled()) {
            plugin.getLogger().severe("FastAsyncWorldEdit is not installed or not enabled!");
            event.getHook().editOriginal("Server error: schematic plugin unavailable.").queue();
            return;
        }

        File schematicsFolder = new File(fawe.getDataFolder(), "schematics");
        if (!schematicsFolder.exists() && !schematicsFolder.mkdirs()) {
            plugin.getLogger().severe("Could not create schematics folder: " + schematicsFolder.getPath());
            event.getHook().editOriginal("Server error: could not prepare storage.").queue();
            return;
        }

        File newSchematicFile = new File(schematicsFolder, fileName);

        try {
            if (!newSchematicFile.getCanonicalPath().startsWith(schematicsFolder.getCanonicalPath() + File.separator)) {
                plugin.getLogger().severe("Rejected suspicious file path: " + newSchematicFile.getPath());
                event.getHook().editOriginal("Invalid file name.").queue();
                return;
            }
        } catch (IOException e) {
            event.getHook().editOriginal("Server error validating file path.").queue();
            return;
        }

        attachment.getProxy().downloadToPath(newSchematicFile.toPath())
                .thenAccept(path -> {
                    plugin.getLogger().info("Successfully saved schematic file: " + path);
                    event.getHook().editOriginal("Schematic uploaded successfully as `" + fileName + "`.").queue();
                })
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Could not download schematic file: " + ex.getMessage());
                    event.getHook().editOriginal("Failed to save the schematic file.").queue();
                    return null;
                });
    }

    /* ===================== Reports (MC -> Discord) ===================== */
    public void sendReportEmbed(String reporterName, String targetName, String reason, Location loc)
    {
        if (!isReady()) return;
        TextChannel ch = getReportsChannel();
        if (ch == null) return;

        int x = (loc != null ? loc.getBlockX() : 0);
        int y = (loc != null ? loc.getBlockY() : 0);
        int z = (loc != null ? loc.getBlockZ() : 0);

        ReportRecord rec = new ReportRecord(reporterName, targetName, reason, x, y, z);
        String reportId = UUID.randomUUID().toString();
        byId.put(reportId, rec);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("In-Game Report")
                .setColor(Color.ORANGE)
                .addField("Reporter", safe(reporterName), true)
                .addField("Reported", safe(targetName), true)
                .addField("Reason", safe(reason), false)
                .addField("Coordinates", "X: " + x + " Y: " + y + " Z: " + z, false)
                .setTimestamp(Instant.now());

        ch.sendMessageEmbeds(eb.build()).queue((Message msg) -> {
            rec.messageId = msg.getId();
            pushIndex(reporterName, reportId);
            pushIndex(targetName, reportId);
            byMessageId.put(msg.getId(), reportId);
            msg.addReaction(Emoji.fromUnicode("✅")).queue();
        }, err -> {
            String more = (err instanceof ErrorResponseException ere)
                    ? " (Discord code " + ere.getErrorCode() + " / " + ere.getMeaning() + ")"
                    : "";
            plugin.getLogger().warning("[Discord] Failed to send report embed: " + err.getMessage() + more);
        });
    }

    private void pushIndex(String playerName, String reportId) {
        String k = playerName == null ? "" : playerName.toLowerCase(Locale.ENGLISH);
        byPlayer.computeIfAbsent(k, __ -> new ArrayDeque<>()).addLast(reportId);
    }

    /** Called by TheSandboxCore when staff run /tpo <name> in-game (from your click action). */
    public void tryArchiveRecentForPlayer(String clickedName, String handlerName) {
        if (!isReady()) return;
        TextChannel reports = getReportsChannel();
        TextChannel archive = getArchivedReportsChannel();
        if (reports == null || archive == null) return;

        String key = clickedName == null ? "" : clickedName.toLowerCase(Locale.ENGLISH);
        Deque<String> dq = byPlayer.get(key);
        if (dq == null || dq.isEmpty()) return;

        while (!dq.isEmpty()) {
            String id = dq.peekLast();
            ReportRecord rec = byId.get(id);
            if (rec == null || rec.messageId == null) { dq.pollLast(); continue; }

            String msgId = rec.messageId;
            reports.retrieveMessageById(msgId).queue((Message message) -> {
                EmbedBuilder archived = new EmbedBuilder()
                        .setTitle("In-Game Report (Archived)")
                        .setColor(Color.GREEN)
                        .addField("Reporter", safe(rec.reporter), true)
                        .addField("Reported", safe(rec.target), true)
                        .addField("Reason", safe(rec.reason), false)
                        .addField("Coordinates", "X: " + rec.x + " Y: " + rec.y + " Z: " + rec.z, false)
                        .setDescription("*Handled by: " + safe(handlerName) + "; MINECRAFT*")
                        .setTimestamp(Instant.now());

                archive.sendMessageEmbeds(archived.build()).queue(v -> {
                    message.delete().queue();
                    cleanup(id, msgId, rec);
                }, t -> plugin.getLogger().warning("[Discord] Failed to archive report: " + t.getMessage()));
            }, t -> cleanup(id, msgId, rec));

            dq.pollLast();
            break; // only one per click
        }
    }

    /* ===================== Reports (Discord ✅ -> archive) ===================== */
    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event)
    {
        if (event.getUser() == null || event.getUser().isBot()) return;

        TextChannel reports = getReportsChannel();
        TextChannel archive = getArchivedReportsChannel();
        if (reports == null || archive == null) return;

        if (!event.isFromGuild()) return;
        if (!Objects.equals(event.getChannel().getId(), reports.getId())) return;

        // ✅ U+2705
        String cp = null;
        try { cp = event.getEmoji().asUnicode().getAsCodepoints(); } catch (Exception ignored) {}
        if (!"U+2705".equalsIgnoreCase(cp)) return;

        final String messageId = event.getMessageId();
        final String reportId = byMessageId.get(messageId);
        if (reportId == null) return;
        ReportRecord rec = byId.get(reportId);
        if (rec == null) return;

        reports.retrieveMessageById(messageId).queue((Message message) -> {
            EmbedBuilder archived = new EmbedBuilder()
                    .setTitle("In-Game Report (Archived)")
                    .setColor(Color.GREEN)
                    .addField("Reporter", safe(rec.reporter), true)
                    .addField("Reported", safe(rec.target), true)
                    .addField("Reason", safe(rec.reason), false)
                    .addField("Coordinates", "X: " + rec.x + " Y: " + rec.y + " Z: " + rec.z, false)
                    // MENTION the Discord user who reacted:
                    .setDescription("*Handled by: " + event.getUser().getAsMention() + "; DISCORD*")
                    .setTimestamp(Instant.now());

            getArchivedReportsChannel().sendMessageEmbeds(archived.build()).queue(v -> {
                message.delete().queue();
                cleanup(reportId, messageId, rec);
            }, t -> plugin.getLogger().warning("[Discord] Failed to archive report: " + t.getMessage()));
        }, t -> cleanup(reportId, messageId, rec));
    }

    private void cleanup(String reportId, String messageId, ReportRecord rec) {
        byId.remove(reportId);
        if (messageId != null) byMessageId.remove(messageId);

        String r = rec.reporter == null ? "" : rec.reporter.toLowerCase(Locale.ENGLISH);
        String t = rec.target == null ? "" : rec.target.toLowerCase(Locale.ENGLISH);
        removeFromIndex(r, reportId);
        removeFromIndex(t, reportId);
    }

    private void removeFromIndex(String key, String reportId) {
        if (key == null || key.isEmpty()) return;
        Deque<String> dq = byPlayer.get(key);
        if (dq == null) return;
        dq.removeIf(id -> Objects.equals(id, reportId));
        if (dq.isEmpty()) byPlayer.remove(key);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /* ===================== Startup auto-archive ===================== */
    private void autoArchiveExistingReportsOnStartup() {
        TextChannel reports = getReportsChannel();
        TextChannel archive = getArchivedReportsChannel();
        if (reports == null || archive == null) return;

        plugin.getLogger().info("[Discord] Scanning #reports for leftover messages to auto-archive...");

        final int maxProcessed = 500;
        final int pageSize = 100;

        reports.getHistory().retrievePast(1).queue(firstBatch -> {
            if (firstBatch == null || firstBatch.isEmpty()) return;
            String beforeId = firstBatch.get(0).getId();
            fetchArchivePage(reports, archive, beforeId, 0, maxProcessed, pageSize);
        }, err -> plugin.getLogger().warning("[Discord] Startup scan (seed) failed: " + err.getMessage()));
    }

    private void fetchArchivePage(TextChannel reports, TextChannel archive,
                                  String beforeId, int processed, int maxProcessed, int pageSize) {
        if (processed >= maxProcessed) return;

        reports.getHistoryBefore(beforeId, pageSize).queue((MessageHistory history) -> {
            List<Message> messages = history.getRetrievedHistory();
            if (messages == null || messages.isEmpty()) return;

            String lastId = beforeId;

            for (Message message : messages) {
                lastId = message.getId();
                EmbedBuilder archived = buildArchivedFromMessage(message);
                if (archived == null) continue;

                archive.sendMessageEmbeds(archived.build()).queue(v -> {
                    message.delete().queue(
                            ok -> {},
                            err -> plugin.getLogger().warning("[Discord] Failed to delete old report: " + err.getMessage())
                    );
                }, t -> plugin.getLogger().warning("[Discord] Failed to move old report: " + t.getMessage()));
            }

            int newProcessed = processed + messages.size();
            if (newProcessed < maxProcessed) {
                fetchArchivePage(reports, archive, lastId, newProcessed, maxProcessed, pageSize);
            }
        }, err -> plugin.getLogger().warning("[Discord] Startup scan (page) failed: " + err.getMessage()));
    }

    private EmbedBuilder buildArchivedFromMessage(Message msg) {
        String reporter = null, reported = null, reason = null;
        Integer x = null, y = null, z = null;

        if (!msg.getEmbeds().isEmpty()) {
            MessageEmbed emb = msg.getEmbeds().get(0);
            if (emb.getTitle() != null && emb.getTitle().toLowerCase(Locale.ENGLISH).contains("in-game report")) {
                for (MessageEmbed.Field f : emb.getFields()) {
                    String name = f.getName() == null ? "" : f.getName().toLowerCase(Locale.ENGLISH);
                    String val = f.getValue() == null ? "" : f.getValue();
                    switch (name) {
                        case "reporter" -> reporter = val;
                        case "reported" -> reported = val;
                        case "reason" -> reason = val;
                        case "coordinates" -> {
                            int[] parsed = parseCoords(val);
                            if (parsed != null) { x = parsed[0]; y = parsed[1]; z = parsed[2]; }
                        }
                    }
                }
            }
        } else {
            String raw = msg.getContentRaw();
            try {
                String stripped = raw.replace("**", "");
                int idxReported = stripped.indexOf(" reported ");
                int idxFor = stripped.indexOf(" for: ");
                int idxCoords = stripped.indexOf("; X:");
                int bracket = stripped.indexOf("] ");

                if (bracket >= 0 && idxReported > bracket && idxFor > idxReported) {
                    reporter = stripped.substring(bracket + 2, idxReported).trim();
                    reported = stripped.substring(idxReported + " reported ".length(), idxFor).trim();
                }

                if (idxFor >= 0) {
                    int open = stripped.indexOf('(', idxFor);
                    int close = stripped.indexOf(')', open + 1);
                    if (open > 0 && close > open) {
                        reason = stripped.substring(open + 1, close).trim();
                    }
                }

                if (idxCoords >= 0) {
                    String coordPart = stripped.substring(idxCoords + 2).replace("*", "");
                    int[] parsed = parseCoords(coordPart);
                    if (parsed != null) { x = parsed[0]; y = parsed[1]; z = parsed[2]; }
                }
            } catch (Exception ignore) {}
        }

        if (reporter == null && reported == null && reason == null && x == null) {
            return null;
        }

        if (reporter == null) reporter = "Unknown";
        if (reported == null) reported = "Unknown";
        if (reason == null) reason = "Unknown";
        if (x == null) { x = 0; y = 0; z = 0; }

        return new EmbedBuilder()
                .setTitle("In-Game Report (Archived)")
                .setColor(Color.GREEN)
                .addField("Reporter", reporter, true)
                .addField("Reported", reported, true)
                .addField("Reason", reason, false)
                .addField("Coordinates", "X: " + x + " Y: " + y + " Z: " + z, false)
                .setDescription("*Automatically archived by CONSOLE*")
                .setTimestamp(Instant.now());
    }

    private int[] parseCoords(String s) {
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

    /* -------------------------- Server / Player embeds -------------------------- */
    /** True for ranks considered Staff in /list. MB is intentionally not staff. */
    private static boolean isStaffRank(LoginService.Rank r) {
        if (r == null) return false;
        return switch (r) {
            case OPERATOR, ADMIN, STAFF -> true;
            default -> false; // MB, VIP, and DEFAULT fall here
        };
    }

    /** DM a user by Discord ID. Returns true if the request was queued. */
    public boolean dm(long discordId, String content) {
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

    /**
     * Discord-safe rank prefix that mirrors the in-game style:
     * OP • Player, ADMIN • Player, STAFF • Player, MB • Player, VIP • Player.
     * Minecraft color codes do not render in Discord, so bold text is used instead.
     */
    private static String discordRankPrefix(LoginService.Rank r) {
        if (r == null) return "";
        return switch (r) {
            case OPERATOR -> "**OP** • ";
            case ADMIN    -> "**ADMIN** • ";
            case STAFF    -> "**STAFF** • ";
            case MB       -> "**MB** • ";
            case VIP      -> "**VIP** • ";
            default       -> "";
        };
    }

    private void sendServerStartEmbed() {
        TextChannel ch = getChatChannel();
        if (ch == null) return;

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(50, 205, 50))
                .setDescription("**:green_square: Server has started!**");
        ch.sendMessageEmbeds(eb.build()).queue();
    }



    private void sendUpdateEmbed(String update_message, Player p) {
        TextChannel ch = getStaffLogsChannel();
        if (ch == null) return;

        String name    = p.getName();
        String headUrl = playerHeadUrl(p);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(255, 162, 0, 255))
                .setAuthor("Requested By " + name, null, headUrl)
                .setDescription("**:yellow_square: " + update_message + " **");
        ch.sendMessageEmbeds(eb.build()).queue();
    }

    public void sendGenericEmbed(String msg, Color color, String sender, String avatarurl ,String channel) {
        TextChannel ch;
        switch (channel) {
            case "Logs":
                ch = getStaffLogsChannel();
                break;
            case "Staff":
                ch = getStaffChannel();
            case "Schems":
                ch = getSchemUploadsChannel();
                break;
            default:
                ch = getStaffLogsChannel();
        }
        if (ch == null) return;
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(color.getRGB()))
                .setAuthor("Requested By " + sender, null, avatarurl)
                .setDescription(msg);
        ch.sendMessageEmbeds(eb.build()).queue();
    }

    private void sendVanishEmbed(Player p, String message) {
        TextChannel ch = getStaffLogsChannel();
        if (ch == null) return;

        String name    = p.getName();
        String headurl = playerHeadUrl(p);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0, 0, 0, 255))
                .setAuthor(name + " " + message, null, headurl);
        ch.sendMessageEmbeds(eb.build()).queue();
    }

    private void sendServerStopEmbedBlocking() {
        TextChannel ch = getChatChannel();
        if (ch == null) return;

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(255, 0, 0))
                .setDescription("**:red_square: Server has stopped!**");
        try {
            ch.sendMessageEmbeds(eb.build()).complete();
        } catch (Throwable t) {
            plugin.getLogger().warning("[Discord] Stop embed failed: " + t.getMessage());
        }
    }

    /* Small circular head via Author icon (Discord renders it round). */
    private static String playerHeadUrl(Player p) {
        return "https://minotar.net/helm/" + p.getName() + "/64.png";
    }

    private void sendPlayerJoinEmbed(Player p) {
        if (!plugin.isDiscordChatBridgeEnabled()) return;
        TextChannel ch = getChatChannel();
        if (ch == null) return;

        String name    = p.getName();
        String headUrl = playerHeadUrl(p);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(50, 205, 50))
                .setAuthor(name + " joined the server", null, headUrl);

        ch.sendMessageEmbeds(eb.build()).queue();
    }

    private void sendPlayerQuitEmbed(Player p) {
        if (!plugin.isDiscordChatBridgeEnabled()) return;
        TextChannel ch = getChatChannel();
        if (ch == null) return;

        String name    = p.getName();
        String headUrl = playerHeadUrl(p);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(255, 0, 0))
                .setAuthor(name + " left the server", null, headUrl);

        ch.sendMessageEmbeds(eb.build()).queue();
    }

    private final class ServerActivityListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            Player p = e.getPlayer();
            Boolean vanish_status = playerDataListener.get(p.getUniqueId(), PlayerDataKeys.VANISHED, false);

            if (!vanish_status) {
                sendPlayerJoinEmbed(p);
            } else {
                sendVanishEmbed(p,"has joined the game while vanished");
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            Player p = e.getPlayer();
            Boolean vanish_status = playerDataListener.get(p.getUniqueId(), PlayerDataKeys.VANISHED, false);
            if (!vanish_status) {
                sendPlayerQuitEmbed(p);
            } else {
                sendVanishEmbed(p,"has left the game while vanished");
            }
        }

        // SuperVanish / PremiumVanish hooks
        @EventHandler
        public void onHide(PlayerHideEvent e) {
            sendPlayerQuitEmbed(e.getPlayer());
            sendVanishEmbed(e.getPlayer(), "has vanished");
        }

        @EventHandler
        public void onShow(PlayerShowEvent e) {
            sendPlayerJoinEmbed(e.getPlayer());
            sendVanishEmbed(e.getPlayer(), "has unvanished");
        }
    }

    /* ------------------------------ Helpers ------------------------------ */
    private static String stripAllColors(String s) {
        if (s == null) return "";
        // First remove MiniMessage tags like <red>, <bold>, <hover:...>, etc.
        String noMini = stripMiniMessageTags(s);
        // Then handle legacy & / § colors
        String translated = HexColorUtil.translate(noMini);
        return ChatColor.stripColor(translated);
    }

    private String resolveLuckPermsPrefix(Player p) {
        try {
            var lp = net.luckperms.api.LuckPermsProvider.get();
            var user = lp.getUserManager().getUser(p.getUniqueId());
            if (user == null) return null;
            return user.getCachedData().getMetaData().getPrefix();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String toLegacyHex(java.awt.Color c) {
        String hex = String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        return "&x&" + hex.charAt(0) + "&" + hex.charAt(1)
                + "&" + hex.charAt(2) + "&" + hex.charAt(3)
                + "&" + hex.charAt(4) + "&" + hex.charAt(5);
    }

    /** Prevents @everyone / @here from pinging by inserting a zero-width space. */
    private static String antiPingEveryoneHere(String s) {
        if (s == null) return null;
        return s.replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere");
    }

    // Strip MiniMessage-style tags like <red>, <bold>, <hover:...>, </click>, <gradient:#fff:#000>, etc.
    private static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^>]+>");

    private static String stripMiniMessageTags(String s) {
        if (s == null) return "";
        return MINIMESSAGE_TAG.matcher(s).replaceAll("");
    }

    // Every visible link sent through the Discord bridge is represented as [Media].
    // In Discord this is still clickable through markdown: [Media](https://example.com/file.png).
    private static final Pattern ANY_URL = Pattern.compile("(?i)https?://[^\\s<>()]+", Pattern.CASE_INSENSITIVE);

    private static List<String> extractAllUrls(String text) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (text != null && !text.isEmpty()) {
            Matcher m = ANY_URL.matcher(text);
            while (m.find()) out.add(trimTrailingUrlPunctuation(m.group()));
        }
        return out;
    }

    private static String stripAllUrls(String text) {
        if (text == null || text.isEmpty()) return "";
        return cleanupMediaSpacing(ANY_URL.matcher(text).replaceAll(""));
    }

    private static String replaceLinksWithMediaTag(String text) {
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

    private static String cleanupMediaSpacing(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
                .replaceAll("(?i)(^|\\s)&\\s+(?=\\[Media])", "$1")
                .replaceAll("(?i)(^|\\s)&(?=\\[Media])", "$1")
                .replaceAll("(?i)\\[MEDIA\\]", "[Media]")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String trimTrailingUrlPunctuation(String url) {
        if (url == null) return "";
        while (!url.isEmpty() && ".,!?;:".indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static boolean isMediaUrl(String url) {
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

    private static void addEmbedMediaPlaceholders(Message message, java.util.List<String> mediaUrls) {
        if (message == null) return;
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embed.getImage() != null && embed.getImage().getUrl() != null) {
                mediaUrls.add(embed.getImage().getUrl());
            } else if (embed.getThumbnail() != null && embed.getThumbnail().getUrl() != null) {
                mediaUrls.add(embed.getThumbnail().getUrl());
            } else if (embed.getVideoInfo() != null && embed.getVideoInfo().getUrl() != null) {
                mediaUrls.add(embed.getVideoInfo().getUrl());
            } else if (embed.getUrl() != null) {
                mediaUrls.add(embed.getUrl());
            }
        }
    }

    private static BaseComponent[] legacy(String ampersandColored) {
        return TextComponent.fromLegacyText(HexColorUtil.translate(ampersandColored));
    }

    private static BaseComponent[] join(BaseComponent[]... arrays) {
        java.util.ArrayList<BaseComponent> list = new java.util.ArrayList<>();
        for (BaseComponent[] a : arrays) {
            if (a != null) {
                Collections.addAll(list, a);
            }
        }
        return list.toArray(new BaseComponent[0]);
    }

    private static String firstClickableMediaUrl(java.util.List<String> mediaUrls) {
        if (mediaUrls == null) return null;
        for (String url : mediaUrls) {
            if (url != null && url.toLowerCase(Locale.ROOT).startsWith("http")) {
                return url;
            }
        }
        return null;
    }

    private static BaseComponent[] clickableMediaTagOrLegacy(String labelAmp, String url) {
        if (url == null || url.isBlank()) return legacy(labelAmp);
        return clickableMediaTag(labelAmp, url);
    }

    private static BaseComponent[] clickableMediaTag(String labelAmp, String url) {
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

    /** Did the plain message contain this MC name (name or @name) as a token? */
    private static boolean messageMentions(String content, String mcName) {
        if (content == null || mcName == null || mcName.isEmpty()) return false;
        String atPattern = "(?i)(?<!\\S)@(" + Pattern.quote(mcName) + ")(?!\\w)";
        String namePattern = "(?i)(?<!\\w)(" + Pattern.quote(mcName) + ")(?!\\w)";
        return Pattern.compile(atPattern).matcher(content).find()
                || Pattern.compile(namePattern).matcher(content).find();
    }

    /**
     * Public chat default: highlight then reset (&r).
     */
    private static String highlightDiscordMentionAmpersand(String amp, String mcName) {
        return highlightDiscordMentionAmpersand(amp, mcName, "&r");
    }

    /**
     * Highlights the given Minecraft player's name (and @name) in YELLOW for that player only.
     * 'postColor' is injected right after the mention so formatting resumes with that color.
     * Works on the ampersand-colored string (before ChatColor translation).
     */
    private static String highlightDiscordMentionAmpersand(String amp, String mcName, String postColor) {
        if (amp == null || mcName == null || mcName.isEmpty()) return amp;
        if (postColor == null) postColor = "&r";

        String atPattern = "(?i)(?<!\\S)(@)(" + Pattern.quote(mcName) + ")(?!\\w)";
        amp = amp.replaceAll(atPattern, "&e$1$2" + postColor);

        String namePattern = "(?i)(?<![\\w@])(" + Pattern.quote(mcName) + ")(?!\\w)";
        amp = amp.replaceAll(namePattern, "&e$1" + postColor);

        amp = amp.replace("  ", " ");
        return amp;
    }

    private boolean hasRole(Member m, String roleId) {
        if (m == null || roleId == null) return false;
        for (Role r : m.getRoles()) {
            if (roleId.equals(r.getId())) return true;
        }
        return false;
    }

    /* ---------------------- Rank role sync helpers ---------------------- */
    private String rankRoleIdFor(LoginService.Rank r) {
        if (r == null) return null;
        switch (r) {
            case ADMIN: return ROLE_SRADMIN; // existing Discord Senior Admin role now represents Admin
            case STAFF: return ROLE_MOD;     // existing Discord Moderator role now represents Staff
            case MB:    return ROLE_MB;
            // OPERATOR / VIP / DEFAULT -> no managed role id provided here
            default:    return null;
        }
    }

    private Set<String> managedRankRoleIds() {
        Set<String> s = new HashSet<>();
        s.add(ROLE_SRADMIN); // Admin
        s.add(ROLE_MOD);     // Staff
        s.add(ROLE_MB);
        return s;
    }

    private LoginService.Rank resolveRankForName(String mcName) {
        try {
            Player p = Bukkit.getPlayerExact(mcName);
            if (p != null) {
                return plugin.getLoginService().getRank(p);
            }
        } catch (Throwable ignored) {}

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
                case "operator":      return LoginService.Rank.OPERATOR;
                case "administrator": return LoginService.Rank.ADMIN;
                case "staff":         return LoginService.Rank.STAFF;
                case "masterbuilder": return LoginService.Rank.MB;
                case "vip":           return LoginService.Rank.VIP;
                default:              return LoginService.Rank.DEFAULT;
            }
        } catch (Throwable ignored) {
            return LoginService.Rank.DEFAULT;
        }
    }
}
