package org.thesandbox.core.commands.Fun;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PrefixNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.HexColorUtil;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.guilds.Guild;
import org.thesandbox.core.guilds.GuildManager;
import org.thesandbox.core.tags.TagService;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class TagCommand implements ISubCommand {
    private static final int PREFIX_PRIORITY = 200;
    private static final int MAX_VISIBLE_LEN = 30; // visible chars only (no color codes)

    private final JavaPlugin plugin;
    private final TagService tagService;
    private final GuildManager guildManager;

    public TagCommand(JavaPlugin plugin, TagService tagService, GuildManager guildManager) {
        this.plugin = plugin;
        this.tagService = tagService;
        this.guildManager = guildManager;
    }

    public TagCommand(JavaPlugin plugin, TagService tagService) {
        this(plugin, tagService, null);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " [-save] <set <tag..> | gradient <hex> <hex> <tag..> | list | off | save | clear <player> | clearall>"));
            return true;
        }

        boolean saveFlag = false;
        int idx = 0;
        if (args[0].equalsIgnoreCase("-s") || args[0].equalsIgnoreCase("-save")) {
            saveFlag = true;
            idx = 1;
            if (args.length <= 1) {
                sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " -save <set|gradient|save ...>"));
                return true;
            }
        }

        String sub = args[idx].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> handleSet(sender, label, Arrays.copyOfRange(args, idx + 1, args.length), saveFlag);
            case "gradient" -> handleGradient(sender, label, Arrays.copyOfRange(args, idx + 1, args.length), saveFlag);
            case "list" -> handleList(sender);
            case "off" -> handleOff(sender);
            case "save" -> handleSaveCurrent(sender);
            case "clear" -> handleClear(sender, Arrays.copyOfRange(args, idx + 1, args.length));
            case "clearall" -> handleClearAll(sender);
            default -> sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " [-save] <set|gradient|list|off|save|clear|clearall>"));
        }
        return true;
    }

    // ---- /tag set <tag..> ----
    private void handleSet(CommandSender sender, String label, String[] args, boolean save) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can set their tag."));
            return;
        }
        if (args.length == 0) {
            p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " set <tag..>"));
            return;
        }
        String raw = joinWithSpaces(args);

        // Blacklist (skip if bypass)
        if (!sender.hasPermission("sandbox.mb")) {
            String blocked = firstBlacklistedTerm(raw);
            if (blocked != null) {
                p.sendMessage(CommandMessages.error(ChatColor.RED + "That tag contains a blocked term: " + ChatColor.YELLOW + blocked));
                return;
            }
        }

        // Visible-length enforcement
        int visibleLen = visibleLength(raw);
        if (visibleLen > MAX_VISIBLE_LEN) {
            p.sendMessage(CommandMessages.error(ChatColor.RED + "That tag is too long (" + visibleLen + "/" + MAX_VISIBLE_LEN + "). Shorten it."));
            return;
        }

        String legacy = HexColorUtil.translate(raw);
        // CHANGED: no trailing space is appended anymore; only a trailing reset
        String finalPrefix = ensureSingleSpaceAfterTag(legacy);
        applyPrefixAsync(p.getName(), p.getUniqueId(), finalPrefix, save, true);
    }

    // ---- /tag gradient <hex> <hex> <tag..> ----
    private void handleGradient(CommandSender sender, String label, String[] args, boolean save) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can set their tag."));
            return;
        }
        if (args.length < 3) {
            p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " gradient <hex> <hex> <tag..>"));
            return;
        }
        String c1 = normalizeHex(args[0]);
        String c2 = normalizeHex(args[1]);
        if (c1 == null || c2 == null) {
            p.sendMessage(CommandMessages.error(ChatColor.RED + "Colors must be hex like #ff00aa or ff00aa."));
            return;
        }
        String text = joinWithSpaces(Arrays.copyOfRange(args, 2, args.length));

        // Blacklist (skip if bypass)
        if (!sender.hasPermission("sandbox.mb")) {
            String blocked = firstBlacklistedTerm(text);
            if (blocked != null) {
                p.sendMessage(CommandMessages.error(ChatColor.RED + "That tag contains a blocked term: " + ChatColor.YELLOW + blocked));
                return;
            }
        }

        // Visible-length = plain text length for gradient
        int visibleLen = text.length();
        if (visibleLen > MAX_VISIBLE_LEN) {
            p.sendMessage(CommandMessages.error(ChatColor.RED + "That tag is too long (" + visibleLen + "/" + MAX_VISIBLE_LEN + "). Shorten it."));
            return;
        }

        String gradient = gradientify(c1, c2, text);
        // CHANGED: no trailing space is appended anymore; only a trailing reset
        String finalPrefix = ensureSingleSpaceAfterTag(gradient);
        applyPrefixAsync(p.getName(), p.getUniqueId(), finalPrefix, save, true);
    }

    // ---- /tag list ----
    private void handleList(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                List<String> lines = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    try {
                        User u = um.loadUser(online.getUniqueId()).get(5, TimeUnit.SECONDS);
                        if (u == null) continue;
                        String tag = currentPrefixAtPriority(u, PREFIX_PRIORITY);
                        if (tag != null && !tag.isEmpty()) {
                            lines.add(ChatColor.DARK_AQUA + " - " + online.getName() + ChatColor.GRAY + " -> " + tag);
                        }
                    } catch (Exception ignored) {}
                }
                if (lines.isEmpty()) {
                    sender.sendMessage(CommandMessages.command(ChatColor.YELLOW + "No one online currently has a tag."));
                } else {
                    sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Online players with tags:"));
                    for (String line : lines) sender.sendMessage(CommandMessages.command(line));
                }
            } catch (Exception e) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Error listing tags: " + e.getMessage()));
            }
        });
    }

    // ---- /tag save ----
    private void handleSaveCurrent(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can use /tag save."));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                User user = um.loadUser(p.getUniqueId()).get(5, TimeUnit.SECONDS);
                if (user == null) {
                    p.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms user not found."));
                    return;
                }
                String current = currentPrefixAtPriority(user, PREFIX_PRIORITY);
                if (current == null || current.isEmpty()) {
                    p.sendMessage(CommandMessages.error(ChatColor.YELLOW + "You don't have a tag to save yet."));
                    return;
                }
                tagService.saveTag(p.getName(), decolor(current));
                p.sendMessage(CommandMessages.command(ChatColor.GREEN + "Saved your current tag."));
            } catch (Exception e) {
                p.sendMessage(CommandMessages.error(ChatColor.RED + "Error: " + e.getMessage()));
            }
        });
    }

    // ---- /tag off ----
    private void handleOff(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can use /tag off."));
            return;
        }
        tagService.deleteTag(p.getName()); // also remove from MySQL per spec
        applyFallbackPrefixAsync(p.getName(), p.getUniqueId(), true);
    }

    // ---- /tag clear <player> ----
    private void handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sandbox.staff")) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "You need to be staff to do that."));
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + labelFrom(sender) + " clear <player>"));
            return;
        }
        String targetName = args[0];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                UUID uuid = um.lookupUniqueId(targetName).get(5, TimeUnit.SECONDS);
                if (uuid == null) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "Unknown player: " + targetName));
                    return;
                }
                User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                if (user == null) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "Could not load LuckPerms user for " + targetName));
                    return;
                }
                tagService.deleteTag(targetName);
                String fallback = applyFallbackPrefix(user, uuid);
                user.getCachedData().invalidate();
                um.saveUser(user).get(5, TimeUnit.SECONDS);
                if (fallback != null) {
                    sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Cleared saved tag for " + targetName + ". " + ChatColor.GRAY + "Now using " + fallback + "."));
                } else {
                    sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Cleared tag for " + targetName + ". " + ChatColor.GRAY + "Now using their normal LuckPerms rank prefix."));
                }
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - " + "Removing " + targetName + "'s tag"));
            } catch (Exception e) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Error clearing: " + e.getMessage()));
            }
        });
    }

    // ---- /tag clearall ----
    private void handleClearAll(CommandSender sender) {
        if (!sender.hasPermission("sandbox.staff")) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "You need to be staff to do that."));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                int clearedUsers = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    try {
                        User u = um.loadUser(online.getUniqueId()).get(5, TimeUnit.SECONDS);
                        if (u != null && removePrefixNodes(u)) {
                            u.getCachedData().invalidate();
                            um.saveUser(u).get(5, TimeUnit.SECONDS);
                            clearedUsers++;
                        }
                    } catch (Exception ignored) {}
                }
                sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Cleared tags for " + clearedUsers + " online player(s). "
                        + ChatColor.GRAY + "(Saved tags in MySQL were not modified.)"));
                Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - " + "Removing all online tags"));
            } catch (Exception e) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "clearall error: " + e.getMessage()));
            }
        });
    }

    // ---- helpers ----

    private String getEssentialsNickOrName(Player p) {
        try {
            org.bukkit.plugin.Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess != null && ess.isEnabled()) {
                java.lang.reflect.Method getUser = ess.getClass().getMethod("getUser", Player.class);
                Object user = getUser.invoke(ess, p);
                if (user != null) {
                    java.lang.reflect.Method getNickname = user.getClass().getMethod("getNickname");
                    Object nickObj = getNickname.invoke(user);
                    if (nickObj instanceof String nick && !nick.isEmpty()) {
                        String colored = ChatColor.translateAlternateColorCodes('&', nick);
                        return stripLeadingSpaces(colored); // avoid doubled gap in preview
                    }
                }
            }
        } catch (Throwable ignored) {}
        return p.getName();
    }

    private static String stripLeadingSpaces(String s) {
        if (s == null) return "";
        return s.replaceFirst("^\\s+", "");
    }

    private void applyPrefixAsync(String playerName, UUID uuid, String prefix, boolean save, boolean notify) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                if (user == null) {
                    if (notify) {
                        Player pl = Bukkit.getPlayer(uuid);
                        if (pl != null) pl.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms user not found."));
                    }
                    return;
                }
                removePrefixNodes(user);
                PrefixNode node = PrefixNode.builder(prefix, PREFIX_PRIORITY).build();
                user.data().add(node);
                user.getCachedData().invalidate();
                um.saveUser(user).get(5, TimeUnit.SECONDS);
                if (save) tagService.saveTag(playerName, decolor(prefix));
                if (notify) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendMessage(CommandMessages.command(ChatColor.GREEN + "Tag updated!"));
                        String nameToShow = getEssentialsNickOrName(p);
                        // Preview shows exactly what was set (no forced space)
                        p.sendMessage(CommandMessages.command(ChatColor.GRAY + "Preview: " + prefix + " " + ChatColor.RESET + nameToShow));
                    }
                }
            } catch (Exception e) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(CommandMessages.error(ChatColor.RED + "Error: " + e.getMessage()));
            }
        });
    }

    private static String joinWithSpaces(String[] parts) {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** UPDATED: remove trailing spaces & dangling codes, then append ONLY a reset (no space). */
    private static String ensureSingleSpaceAfterTag(String s) {
        if (s == null) s = "";
        // Remove trailing whitespace
        s = s.replaceAll("\\s+$", "");
        // Remove dangling legacy (&x) / section (§x) color/format codes at the end
        s = s.replaceAll("(?i)(?:&[0-9A-FK-OR]|§[0-9A-FK-OR])+$", "");
        // Remove dangling §x hex sequences at the end
        s = s.replaceAll("(?i)(?:§x(§[0-9A-F]){6})+$", "");
        // Remove dangling &#RRGGBB sequences at the end
        s = s.replaceAll("(?i)(?:&#[0-9A-F]{6})+$", "");
        // Append ONLY reset, no space
        return s + ChatColor.RESET;
    }

    private static String normalizeHex(String in) {
        if (in == null) return null;
        in = in.trim();
        if (in.startsWith("#")) in = in.substring(1);
        if (in.length() != 6) return null;
        if (!in.matches("[0-9a-fA-F]{6}")) return null;
        return in.toLowerCase(Locale.ROOT);
    }

    private static String gradientify(String hex1, String hex2, String text) {
        int r1 = Integer.parseInt(hex1.substring(0,2), 16);
        int g1 = Integer.parseInt(hex1.substring(2,4), 16);
        int b1 = Integer.parseInt(hex1.substring(4,6), 16);
        int r2 = Integer.parseInt(hex2.substring(0,2), 16);
        int g2 = Integer.parseInt(hex2.substring(2,4), 16);
        int b2 = Integer.parseInt(hex2.substring(4,6), 16);

        int n = Math.max(1, text.length());
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            double t = (n == 1) ? 0.0 : (double) i / (double) (n - 1);
            int r = (int) Math.round(r1 + (r2 - r1) * t);
            int g = (int) Math.round(g1 + (g2 - g1) * t);
            int b = (int) Math.round(b1 + (b2 - b1) * t);
            String h = String.format("%02x%02x%02x", r, g, b);
            out.append(HexColorUtil.toLegacyHex(h)).append(text.charAt(i));
        }
        return out.toString();
    }

    private static String decolor(String legacy) {
        if (legacy == null) return null;
        return legacy.replace('§', '&');
    }

    private String firstBlacklistedTerm(String rawInputWithPossibleCodes) {
        String plain = stripColorsAndFormatting(rawInputWithPossibleCodes).toLowerCase(Locale.ROOT);
        for (String term : getTagBlacklist()) {
            if (term == null) continue;
            String t = term.trim();
            if (t.isEmpty()) continue;
            if (t.regionMatches(true, 0, "regex:", 0, 6)) {
                String pattern = t.substring(6).trim();
                try {
                    if (plain.matches("(?s).*" + pattern + ".*")) {
                        return term;
                    }
                } catch (Exception ignored) {}
            } else {
                if (plain.contains(t.toLowerCase(Locale.ROOT))) {
                    return term;
                }
            }
        }
        return null;
    }

    private List<String> getTagBlacklist() {
        List<String> list = plugin.getConfig().getStringList("tags.blacklist");
        if (list == null || list.isEmpty()) {
            list = plugin.getConfig().getStringList("tag.blacklist");
        }
        return (list != null) ? list : Collections.emptyList();
    }

    private static String stripColorsAndFormatting(String s) {
        if (s == null) return "";
        return HexColorUtil.stripColors(s);
    }

    /** Count visible (non-color) characters for limit checks. */
    private static int visibleLength(String s) {
        return stripColorsAndFormatting(s == null ? "" : s).length();
    }

    private static String labelFrom(CommandSender sender) { return "tag"; }

    /** Returns the current prefix value at a given priority, or null if none. */
    private static String currentPrefixAtPriority(User user, int priority) {
        for (Node n : user.data().toCollection()) {
            if (n instanceof PrefixNode pn && pn.getPriority() == priority) {
                return pn.getMetaValue();
            }
        }
        return null;
    }

    /** Remove prefix nodes at the priority we manage. Returns true if anything was removed. */
    private static boolean removePrefixNodes(User user) {
        return removePrefixNodes(user.data()) || removePrefixNodes(user.transientData());
    }

    private static boolean removePrefixNodes(NodeMap map) {
        List<Node> toRemove = new ArrayList<>();
        for (Node n : map.toCollection()) {
            if (n instanceof PrefixNode pn && pn.getPriority() == PREFIX_PRIORITY) {
                toRemove.add(n);
            }
        }
        boolean changed = false;
        for (Node n : toRemove) {
            DataMutateResult res = map.remove(n);
            if (res != null && res.wasSuccessful()) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * After a saved /tag is cleared, keep the same fallback order used on join:
     * 1) guild rank prefix in the managed tag slot, if the player has one;
     * 2) otherwise remove the managed tag slot so LuckPerms' normal rank prefix is used.
     */
    private void applyFallbackPrefixAsync(String playerName, UUID uuid, boolean notify) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                if (user == null) {
                    if (notify) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) p.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms user not found."));
                    }
                    return;
                }

                String fallback = applyFallbackPrefix(user, uuid);
                user.getCachedData().invalidate();
                um.saveUser(user).get(5, TimeUnit.SECONDS);

                if (notify) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        if (fallback != null) {
                            p.sendMessage(CommandMessages.command(ChatColor.YELLOW + "Tag cleared. " + ChatColor.GRAY + "Using your " + fallback + " now."));
                        } else {
                            p.sendMessage(CommandMessages.command(ChatColor.YELLOW + "Tag cleared. " + ChatColor.GRAY + "Using your normal LuckPerms rank prefix now."));
                        }
                    }
                }
            } catch (Exception e) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage(CommandMessages.error(ChatColor.RED + "Error: " + e.getMessage()));
            }
        });
    }

    private String applyFallbackPrefix(User user, UUID uuid) {
        removePrefixNodes(user);

        String guildRankPrefix = getGuildRankPrefix(uuid);
        if (guildRankPrefix != null && !guildRankPrefix.isBlank()) {
            user.data().add(PrefixNode.builder(normalizeStoredTag(guildRankPrefix), PREFIX_PRIORITY).build());
            return "guild rank tag";
        }

        // No saved tag and no guild rank tag: leave priority 200 empty so the normal LP rank prefix wins.
        return null;
    }

    private String getGuildRankPrefix(UUID uuid) {
        try {
            if (guildManager == null) return null;
            Guild guild = guildManager.guildOf(uuid);
            if (guild == null) return null;
            String prefix = guild.visiblePrefixFor(uuid);
            return (prefix == null || prefix.isBlank()) ? null : prefix;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeStoredTag(String tagValue) {
        String out = HexColorUtil.translate(tagValue == null ? "" : tagValue);
        out = out.replaceAll("\\s+$", "");
        out = out.replaceAll("(?i)(?:&[0-9A-FK-OR]|§[0-9A-FK-OR])+$", "");
        out = out.replaceAll("(?i)(?:§x(§[0-9A-F]){6})+$", "");
        out = out.replaceAll("(?i)(?:&#[0-9A-F]{6})+$", "");
        return out + ChatColor.RESET;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        int idx = 0;
        if (args.length >= 1 && (args[0].equalsIgnoreCase("-s") || args[0].equalsIgnoreCase("-save"))) {
            idx = 1;
        }
        if (args.length == 1) {
            out.add("-s");
            out.add("set");
            out.add("gradient");
            out.add("list");
            out.add("off");
            out.add("save");
            if (sender.hasPermission("sandbox.staff")) {
                out.add("clear");
                out.add("clearall");
            }
            return filter(out, args[0]);
        }
        if (idx == 1 && args.length == 2) {
            out.add("set");
            out.add("gradient");
            out.add("save");
            return filter(out, args[1]);
        }
        if ((args[idx].equalsIgnoreCase("clear")) && args.length == idx + 2 && sender.hasPermission("sandbox.staff")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return filter(out, args[idx + 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        return out;
    }
}