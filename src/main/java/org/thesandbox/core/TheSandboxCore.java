package org.thesandbox.core;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.Admin.UpdateLocalCommand;
import org.thesandbox.core.commands.CommandManager;
import org.thesandbox.core.fun.LoginMessages;
import org.thesandbox.core.fun.Utils;
import org.thesandbox.core.fun.items.FloatBoatItem;
import org.thesandbox.core.fun.items.itemUTILS.Item;
import org.thesandbox.core.fun.items.itemUTILS.ItemKeys;
import org.thesandbox.core.guilds.GuildManager;
import org.thesandbox.core.listeners.*;
import org.thesandbox.core.login.LoginService;
import org.thesandbox.core.managers.*;
import org.thesandbox.core.services.*;
import org.thesandbox.core.tags.TagService;
import org.thesandbox.core.util.*;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"}) // to stop gay errors
public class TheSandboxCore extends JavaPlugin implements Listener {


    // Core
    private DataManager dataManager;
    private PlayerDataListener dataListener;
    private LoginMessages loginMessages;

    // sql
    private LegacySqlStore legacySqlStore;

    // Services
    private CommandBlockManager commandBlockManager;
    private RankScoreboardManager rankScoreboardManager;
    private StaffChatManager staffChatManager;
    private CommandSpyManager commandSpyManager;

    private DiscordBridge discord;
    private CommandManager commandManager;
    private LoginService loginService;
    private TagService tagService;
    private GuildManager guildManager;
    private LiteBansWarningListener liteBansWarningListener;
    private UpdateTarget updateTarget;
    private UpdateLocalCommand updateLocalCommand;

    private GenericListener genericListener;

    private ChatFilterEngine chatFilterEngine;
    private PotionSpyService potionSpyService;
    private ShushService shushService;
    private List<Item> registeredItems = new ArrayList<>();

    // getters

    public DiscordBridge getDiscord() {
        return this.discord;
    }

    public boolean isDiscordChatBridgeEnabled() {
        return getConfig().getBoolean("discord.chat-bridge.enabled", true);
    }

    public PotionSpyService getPotionSpyService() {
        return potionSpyService;
    }
    public List<Item> getRegisteredItems() {
        return this.registeredItems;
    }
    public File getPluginFile() {
        return getFile();
    }
    public ShushService getShushService() {
        return shushService;
    }
    public GuildManager getGuildManager() {
        return guildManager;
    }
    public LoginService getLoginService() {
        return loginService;
    }
    public CommandBlockManager getCommandBlockManager() {
        return commandBlockManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public LoginMessages getLoginMessages() {
        return loginMessages;
    }

    public TagService getTagService() {
        return tagService;
    }

    public UpdateLocalCommand getUpdateLocalCommand() {
        return updateLocalCommand;
    }

    public GenericListener getGenericListener() {
        return genericListener;
    }

    public ChatFilterEngine getChatFilterEngine() {
        return chatFilterEngine;
    }
    public RankScoreboardManager getRankScoreboardManager() {
        return rankScoreboardManager;
    }

    public StaffChatManager getStaffChatManager() {
        return staffChatManager;
    }

    public PlayerDataListener getDataListener() {
        return dataListener;
    }
    public CommandSpyManager getCommandSpyManager() {
        return commandSpyManager;
    }

    @Override
    public void onLoad() {
        load();
        getLogger().info("The SandboxCore has been loaded!");
    }

    @Override
    public void onEnable() {
        startup();
        getLogger().info("TheSandboxCore enabled!");
    }

    @Override
    public void onDisable() {
        cleanup();
        getLogger().info("TheSandboxCore disabled!");
    }

    private void load() {
        Utils.sendASCII("Loading TheSandboxCore ...");
        getLogger().info("loading.. :)");
        // packet events
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
            PacketEvents.getAPI().load();
        } catch (Throwable t) {
            Utils.dump(t, " ): PacketEvents Failed to load!");
        }
        getLogger().info("loaded! ;)");
    }

    /* ================== startup / cleanup =================== */
    private void startup() {
        Utils.sendASCII("Starting TheSandboxCore ...");
        getLogger().info("starting up :)");
        try {
            PacketEvents.getAPI().init();
            getLogger().info("PacketEvents initialized successfully");
        } catch (Throwable t) {
            Utils.dump(t, "PacketEvents failed to initialize");
        }

        saveDefaultConfig();

        legacySqlStore = new LegacySqlStore(this);
        legacySqlStore.setup();

        PluginConfigManager configManager = new PluginConfigManager(this);

        this.dataManager = new DataManager(this);
        this.dataListener = new PlayerDataListener(dataManager);
        getServer().getPluginManager().registerEvents(dataListener, this);

        this.loginMessages = new LoginMessages(dataListener, this);
        getServer().getPluginManager().registerEvents(this.loginMessages, this);

        ItemKeys itemKeys = new ItemKeys(this);
        this.registeredItems = ItemAutoRegistrar.registerAll(this, itemKeys, dataListener, loginMessages, configManager);

        loginService = new LoginService(this);
        loginService.init();

        rankScoreboardManager = new RankScoreboardManager(loginService);
        commandBlockManager = new CommandBlockManager();
        commandSpyManager = new CommandSpyManager(this, () -> legacySqlStore.getDataSource());
        staffChatManager = new StaffChatManager(this, () -> legacySqlStore.getDataSource(), () -> shushService, () -> discord);

        ManageChatService manageChatService = new ManageChatService(this);

        this.guildManager = new GuildManager(this);

        this.chatFilterEngine = new ChatFilterEngine(configManager, this);

        getServer().getPluginManager().registerEvents(new ChatMentionFormatListener(this, chatFilterEngine), this);
        getServer().getPluginManager().registerEvents(new ManageChatListener(manageChatService), this);

        AutoTpService autoTpService = new AutoTpService(this);
        getServer().getPluginManager().registerEvents(new AutoTpListener(this, autoTpService), this);

        this.tagService = new TagService(this);
        this.tagService.init();
        new org.thesandbox.core.tags.TagCleanupListener(this, tagService, guildManager).register();

        Bukkit.getPluginManager().registerEvents(this, this);
        discord = new DiscordBridge(this, this.chatFilterEngine, this.dataListener);
        discord.start();

        this.updateLocalCommand = new UpdateLocalCommand(this, discord);

        AutoClearService autoClearService = new AutoClearService();
        getServer().getPluginManager().registerEvents(new AutoClearListener(autoClearService), this);

        this.potionSpyService = new PotionSpyService(this, legacySqlStore.getDataSource());
        getServer().getPluginManager().registerEvents(this.potionSpyService, this);

        this.shushService = new ShushService(this, legacySqlStore.getDataSource());
        getServer().getPluginManager().registerEvents(new ShushListener(this), this);

        this.liteBansWarningListener = new LiteBansWarningListener(this);
        this.liteBansWarningListener.register();

        // chat filter
        getServer().getPluginManager().registerEvents(new ChatFilterListener(chatFilterEngine), this);

        this.genericListener = new GenericListener(configManager);
        getServer().getPluginManager().registerEvents(this.genericListener, this);

        // Join/leave + vanish behavior (extracted)
        getServer().getPluginManager().registerEvents(
                new JoinLeaveListener(this, dataListener, loginMessages, rankScoreboardManager, shushService), this);

        // Command-block / commandspy / tpo-archive on every command (extracted)
        getServer().getPluginManager().registerEvents(
                new CommandPreListener(this, commandBlockManager, commandSpyManager, () -> shushService, () -> discord), this);

        List<Object> commandServices = new ArrayList<>(List.of(
                tagService,
                loginService,
                autoClearService,
                autoTpService,
                manageChatService,
                guildManager,
                potionSpyService,
                this.shushService,
                this.discord,
                dataListener,
                itemKeys,
                loginMessages,
                configManager,
                updateLocalCommand,
                genericListener,
                commandBlockManager,
                rankScoreboardManager,
                staffChatManager,
                commandSpyManager
        ));

        commandServices.addAll(this.registeredItems);
        CommandAutoRegistrar.registerAll(this, commandServices.toArray());

        rankScoreboardManager.setupRankScoreboardTeams();
        rankScoreboardManager.refreshAllRankScoreboardTeams();

        getLogger().info("Discord Chat Bridge : " + DiscordChatBridgeStatus());
        
        getLogger().info("startup complete! ;)");
    }

    private void cleanup() {
        getLogger().info("[cleaning up :)]");

        if (dataListener != null) dataListener.saveAll();
        if (liteBansWarningListener != null) liteBansWarningListener.unregister();
        if (discord != null) discord.stop();
        if (guildManager != null) {
            guildManager.save();
            guildManager.shutdown();
        }
        if (loginService != null) loginService.shutdown();

        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable t) {
            getLogger().warning("Error terminating PacketEvents: " + t.getMessage());
        }

        if (legacySqlStore != null) legacySqlStore.close();

        registeredItems.stream()
                .filter(item -> item instanceof FloatBoatItem)
                .map(item -> (FloatBoatItem) item)
                .findFirst()
                .ifPresent(FloatBoatItem::cleanup);

        getLogger().info("clean up complete! have a nice day! ;)");
    }

    /* =================== Reload ==================== */

    public void reloadAndReconnect() {
        reloadConfig();
        legacySqlStore.setup();
        if (discord != null) {
            discord.stop();
            discord.start();
        }
    }

    /* =================== Load/Save (legacy commandspy/staffchat) ==================== */

    private void loadPlayer(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            javax.sql.DataSource dataSource = legacySqlStore.getDataSource();
            if (dataSource == null) return;

            try (Connection conn = dataSource.getConnection()) {
                // CommandSpy
                try (PreparedStatement ps = conn.prepareStatement("SELECT enabled FROM commandspy WHERE uuid=?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        commandSpyManager.applyLoadedState(player.getUniqueId(), rs.getBoolean("enabled"));
                    } else {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO commandspy (uuid, enabled) VALUES (?, ?)")) {
                            ins.setString(1, player.getUniqueId().toString());
                            ins.setBoolean(2, true);
                            ins.executeUpdate();
                        }
                    }
                }

                // StaffChat (speak + hidden)
                try (PreparedStatement ps = conn.prepareStatement("SELECT enabled, hidden FROM staffchat WHERE uuid=?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        boolean enabled = rs.getBoolean("enabled");
                        boolean hidden = false;
                        try { hidden = rs.getBoolean("hidden"); } catch (SQLException ignore) {}
                        staffChatManager.applyLoadedState(player.getUniqueId(), enabled, hidden);
                    } else {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO staffchat (uuid, enabled, hidden) VALUES (?, ?, ?)")) {
                            ins.setString(1, player.getUniqueId().toString());
                            ins.setBoolean(2, false);
                            ins.setBoolean(3, false);
                            ins.executeUpdate();
                        } catch (SQLException fallback) {
                            try (PreparedStatement ins2 = conn.prepareStatement(
                                    "INSERT INTO staffchat (uuid, enabled) VALUES (?, ?)")) {
                                ins2.setString(1, player.getUniqueId().toString());
                                ins2.setBoolean(2, false);
                                ins2.executeUpdate();
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                Utils.dump(e, "SQLException");
            }
        });
    }

    /* =================== Remaining direct events ==================== */

    public boolean DiscordChatBridgeStatus() {
        return setDiscordChatBridgeEnabled(true);
    }

    public boolean setDiscordChatBridgeEnabled(boolean enabled) {
        boolean current = isDiscordChatBridgeEnabled();
        if (current == enabled) return false;
        getConfig().set("discord.chat-bridge.enabled", enabled);
        saveConfig();
        if (discord != null) {
            discord.applyChatBridgePermissionState();
        }
        return true;
    }

    @org.bukkit.event.EventHandler
    public void onJoinLoadLegacyData(org.bukkit.event.player.PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (staffChatManager.isStaffChatEnabled(p.getUniqueId())) {
            event.setCancelled(true);

            if (staffChatManager.isStaffChatHidden(p.getUniqueId())) {
                p.sendMessage(Component.text("You cannot talk in staff chat while it is hidden. Use /staffchat hide off.", NamedTextColor.DARK_RED));
                return;
            }

            String name = p.getName();
            String msg = Utils.plainText(event.message());

            Bukkit.getScheduler().runTask(this, () ->
                    staffChatManager.broadcastStaffChat(name, msg, "", "", StaffChatManager.Source.MINECRAFT));
        }
    }
}