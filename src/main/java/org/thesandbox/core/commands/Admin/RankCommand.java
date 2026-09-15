package org.thesandbox.core.commands.Admin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.login.LoginService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RankCommand implements ISubCommand {
    private final TheSandboxCore plugin;

    // All LP rank groups you use (lowercase)
    private static final Set<String> RANK_GROUPS = new HashSet<>(Arrays.asList(
            "operator", "administrator", "staff", "masterbuilder", "vip", "default"
    ));

    private static final Map<String, LoginService.Rank> NAME_TO_RANK = new HashMap<>();
    private static final List<String> RANK_KEYS_ORDERED = new ArrayList<>();

    static {
        // Build tolerant mapping using enum name and displayName
        for (LoginService.Rank r : LoginService.Rank.values()) {
            String enumKey = r.name().toLowerCase(Locale.ENGLISH);
            NAME_TO_RANK.put(enumKey, r);
            NAME_TO_RANK.put(r.displayName.toLowerCase(Locale.ENGLISH), r);
            NAME_TO_RANK.put(r.displayName.replace(" ", "").toLowerCase(Locale.ENGLISH), r);
        }

        // Current public rank names / aliases
        NAME_TO_RANK.put("operator", LoginService.Rank.OPERATOR);
        NAME_TO_RANK.put("op", LoginService.Rank.OPERATOR);
        NAME_TO_RANK.put("administrator", LoginService.Rank.ADMIN);
        NAME_TO_RANK.put("admin", LoginService.Rank.ADMIN);
        NAME_TO_RANK.put("staff", LoginService.Rank.STAFF);
        NAME_TO_RANK.put("masterbuilder", LoginService.Rank.MB);
        NAME_TO_RANK.put("master builder", LoginService.Rank.MB);
        NAME_TO_RANK.put("mb", LoginService.Rank.MB);
        NAME_TO_RANK.put("vip", LoginService.Rank.VIP);
        NAME_TO_RANK.put("default", LoginService.Rank.DEFAULT);
        NAME_TO_RANK.put("player", LoginService.Rank.DEFAULT);

        // Order for tab completion (filtered per permissions)
        RANK_KEYS_ORDERED.add("operator");
        RANK_KEYS_ORDERED.add("administrator");
        RANK_KEYS_ORDERED.add("staff");
        RANK_KEYS_ORDERED.add("masterbuilder");
        RANK_KEYS_ORDERED.add("vip");
        RANK_KEYS_ORDERED.add("default");
    }

    public RankCommand(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    /* =================== Command =================== */
    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        // /rank  (anyone)
        if (args.length == 0) {
            if (sender instanceof Player self) {
                showRankOnlineOrOffline(sender, self.getName());
            } else {
                sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <player>|set <player> <rank>"));
            }
            return true;
        }

        // /rank <player>  (anyone may view; supports offline via LP)
        if (args.length == 1) {
            showRankOnlineOrOffline(sender, args[0]);
            return true;
        }

        // /rank set <player> <rank>  (supports offline via LP; errors if LP doesn't know them)
        if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            if (!canUseSet(sender)) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "You don't have permission to set ranks."));
                return true;
            }

            String playerName = args[1];
            String rankArg = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            LoginService.Rank newRank = parseRank(rankArg);

            if (newRank == null) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "Unknown rank: " + rankArg));
                sender.sendMessage(CommandMessages.command(ChatColor.GRAY + "Valid: " + String.join(", ", visibleRankKeysFor(sender))));
                return true;
            }
            if (!isRankAllowedForSetter(sender, newRank)) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "You are not allowed to set rank: " + LoginService.displayRankTitleCase(newRank)));
                return true;
            }

            // Run LP work asynchronously to avoid blocking the main thread
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                LuckPerms lp;
                try {
                    lp = LuckPermsProvider.get();
                } catch (Exception e) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms is required for rank changes."));
                    return;
                }

                UserManager um = lp.getUserManager();

                // 1) Resolve UUID from LP storage; error if not found
                UUID uuid;
                try {
                    CompletableFuture<UUID> f = um.lookupUniqueId(playerName);
                    uuid = f.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms lookup error: " + e.getMessage()));
                    return;
                }
                if (uuid == null) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms does not recognize the player '" + playerName + "'. They must have joined before."));
                    return;
                }

                // 2) Load user, replace rank group parents, set primary, save
                try {
                    User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                    if (user == null) {
                        sender.sendMessage(CommandMessages.error(ChatColor.RED + "Could not load LuckPerms data for '" + playerName + "'."));
                        return;
                    }

                    String targetGroup = groupIdFor(newRank); // exact LP group id

                    // --- FIX: NodeMap doesn't have removeIf. Collect then remove. ---
                    Collection<Node> current = user.data().toCollection();
                    List<Node> toRemove = new ArrayList<>();
                    for (Node n : current) {
                        if (n instanceof InheritanceNode) {
                            String g = ((InheritanceNode) n).getGroupName().toLowerCase(Locale.ENGLISH);
                            if (RANK_GROUPS.contains(g)) {
                                toRemove.add(n);
                            }
                        }
                    }
                    for (Node n : toRemove) {
                        user.data().remove(n);
                    }
                    // ---------------------------------------------------------------

                    // Add the desired rank group
                    InheritanceNode add = InheritanceNode.builder(targetGroup).value(true).build();
                    user.data().add(add);

                    // Set primary group to match
                    user.setPrimaryGroup(targetGroup);

                    // Save
                    um.saveUser(user).get(5, TimeUnit.SECONDS);
                    // --------------------------------------------------------------

                } catch (Exception e) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms save error: " + e.getMessage()));
                    return;
                }

                // 3) If online, notify them now
                Player online = Bukkit.getPlayerExact(playerName);
                if (online != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String disp = LoginService.displayRankTitleCase(newRank);
                        String colored = ChatColor.translateAlternateColorCodes('&', newRank.color) + disp + ChatColor.RESET;
                        Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - " + "Setting " + playerName + "'s rank to " + disp));
                        plugin.applyRankScoreboardTeam(online, newRank);
                        if (!sender.equals(online)) {
                            online.sendMessage(CommandMessages.command(ChatColor.AQUA + "Your rank has been set to " + colored + ChatColor.AQUA + "."));
                        }
                        sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Updated " + playerName + " to " + colored + ChatColor.GREEN + "."));
                    });
                } else {
                    // offline confirmation to sender
                    String disp = LoginService.displayRankTitleCase(newRank);
                    String colored = ChatColor.translateAlternateColorCodes('&', newRank.color) + disp + ChatColor.RESET;
                    Bukkit.broadcastMessage(CommandMessages.server(ChatColor.RED + sender.getName() + " - " + "Setting " + playerName + "'s rank to " + disp + " (offline)"));
                    sender.sendMessage(CommandMessages.command(ChatColor.GREEN + "Updated " + playerName + " to " + colored + ChatColor.GREEN + " (offline)."));
                }
            });
            return true;
        }

        // Fallback usage
        sender.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /" + label + " <player>|set <player> <rank>"));
        return true;
    }

    /* =================== Tab Complete (required by ISubCommand) =================== */
    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ENGLISH);
            if (canUseSet(sender) && "set".startsWith(p)) out.add("set");
            // also allow direct /rank <player> (online names for tab)
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ENGLISH).startsWith(p))
                    .collect(Collectors.toList()));
            return out;
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            String p = args[1].toLowerCase(Locale.ENGLISH);
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ENGLISH).startsWith(p))
                    .collect(Collectors.toList()));
            return out;
        }
        if (args.length >= 3 && "set".equalsIgnoreCase(args[0])) {
            String typed = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).toLowerCase(Locale.ENGLISH);
            Set<LoginService.Rank> allowed = allowedRanksFor(sender);
            for (String key : RANK_KEYS_ORDERED) {
                LoginService.Rank r = NAME_TO_RANK.get(key);
                if (r == null) r = NAME_TO_RANK.get(key.replace(" ", ""));
                if (r != null && allowed.contains(r) && key.startsWith(typed)) {
                    out.add(key);
                }
            }
            return out;
        }
        return out;
    }

    /* =================== Permissions Rules =================== */
    private boolean canUseSet(CommandSender s) {
        // sandbox.admin can manage normal ranks only. Operator requires sandbox.superuser.
        return s.hasPermission("sandbox.admin") || s.hasPermission("sandbox.superuser");
    }

    /** Which ranks this sender is allowed to set. */
    private EnumSet<LoginService.Rank> allowedRanksFor(CommandSender s) {
        boolean isAdmin = s.hasPermission("sandbox.admin");
        boolean isSuper = s.hasPermission("sandbox.superuser");
        EnumSet<LoginService.Rank> allowed = EnumSet.noneOf(LoginService.Rank.class);

        if (isAdmin) {
            // Admins cannot set Operator. They can only set: ADMINISTRATOR, STAFF, MASTER BUILDER, VIP, DEFAULT.
            allowed.add(LoginService.Rank.ADMIN);
            allowed.add(LoginService.Rank.STAFF);
            allowed.add(LoginService.Rank.MB);
            allowed.add(LoginService.Rank.VIP);
            allowed.add(LoginService.Rank.DEFAULT);
        }
        if (isSuper) {
            // Superuser can set Operator.
            allowed.add(LoginService.Rank.OPERATOR);
        }
        return allowed;
    }

    private boolean isRankAllowedForSetter(CommandSender s, LoginService.Rank desired) {
        return allowedRanksFor(s).contains(desired);
    }

    private List<String> visibleRankKeysFor(CommandSender s) {
        EnumSet<LoginService.Rank> allowed = allowedRanksFor(s);
        List<String> out = new ArrayList<>();
        for (String key : RANK_KEYS_ORDERED) {
            LoginService.Rank r = NAME_TO_RANK.get(key);
            if (r == null) r = NAME_TO_RANK.get(key.replace(" ", ""));
            if (r != null && allowed.contains(r)) out.add(key);
        }
        return out;
    }

    /* =================== View Helpers (online/offline) =================== */
    private void showRankOnlineOrOffline(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            showRank(sender, online);
            return;
        }

        // Offline: ask LuckPerms for their primary group
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (Exception e) {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms is required to view offline player ranks."));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UserManager um = lp.getUserManager();
                UUID uuid = um.lookupUniqueId(name).get(5, TimeUnit.SECONDS);
                if (uuid == null) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms does not recognize the player '" + name + "'. They must have joined before."));
                    return;
                }
                User user = um.loadUser(uuid).get(5, TimeUnit.SECONDS);
                if (user == null) {
                    sender.sendMessage(CommandMessages.error(ChatColor.RED + "Could not load LuckPerms data for '" + name + "'."));
                    return;
                }
                String group = user.getPrimaryGroup();
                LoginService.Rank r = rankFromGroup(group);
                String display = LoginService.displayRankTitleCase(r);
                String colorized = ChatColor.translateAlternateColorCodes('&', r.color) + display + ChatColor.RESET;
                sender.sendMessage(CommandMessages.command(ChatColor.AQUA + name + " is " + colorized + ChatColor.AQUA + "."));
            } catch (Exception ex) {
                sender.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms lookup error: " + ex.getMessage()));
            }
        });
    }

    private void showRank(CommandSender sender, Player target) {
        LoginService.Rank r = plugin.getLoginService().getRank(target);
        String display = LoginService.displayRankTitleCase(r);
        String colorized = ChatColor.translateAlternateColorCodes('&', r.color) + display + ChatColor.RESET;
        if (sender.equals(target)) {
            sender.sendMessage(CommandMessages.command(ChatColor.AQUA + "You are " + colorized + ChatColor.AQUA + "."));
        } else {
            sender.sendMessage(CommandMessages.command(ChatColor.AQUA + target.getName() + " is " + colorized + ChatColor.AQUA + "."));
        }
    }

    /* =================== Parsing & Mapping =================== */
    private LoginService.Rank parseRank(String input) {
        if (input == null) return null;
        String key = input.trim().toLowerCase(Locale.ENGLISH).replace("_", " ").replaceAll("\\s+", " ");
        LoginService.Rank r = NAME_TO_RANK.get(key);
        if (r != null) return r;
        r = NAME_TO_RANK.get(key.replace(" ", "")); // no-space alias
        if (r != null) return r;
        r = NAME_TO_RANK.get(key.replace(" ", "").toUpperCase(Locale.ENGLISH)); // enum style
        return r;
    }

    private LoginService.Rank rankFromGroup(String groupId) {
        if (groupId == null) return LoginService.Rank.DEFAULT;
        switch (groupId.toLowerCase(Locale.ENGLISH)) {
            case "operator":      return LoginService.Rank.OPERATOR;
            case "administrator": return LoginService.Rank.ADMIN;
            case "staff":         return LoginService.Rank.STAFF;
            case "masterbuilder": return LoginService.Rank.MB;
            case "vip":           return LoginService.Rank.VIP;
            case "default":
            default:              return LoginService.Rank.DEFAULT;
        }
    }

    private String groupIdFor(LoginService.Rank r) {
        switch (r) {
            case OPERATOR: return "operator";
            case ADMIN:    return "administrator";
            case STAFF:    return "staff";
            case MB:       return "masterbuilder";
            case VIP:      return "vip";
            case DEFAULT:
            default:       return "default";
        }
    }
}
