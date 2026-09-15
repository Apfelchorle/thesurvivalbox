package org.thesandbox.core.commands.GUILD;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.guilds.Guild;
import org.thesandbox.core.guilds.GuildManager;
import org.thesandbox.core.guilds.Text;

import java.util.*;
import java.util.stream.Collectors;

public final class GuildCommand implements ISubCommand {

    private final JavaPlugin plugin;
    private final GuildManager guilds;

    public GuildCommand(JavaPlugin plugin, GuildManager guilds) {
        this.plugin = plugin;
        this.guilds = guilds;
    }

    private boolean isMod(CommandSender s) {
        return s.hasPermission("sandbox.staff");
    }

    private boolean isSuperuser(CommandSender s) {
        return s.hasPermission("sandbox.superuser");
    }

    @Override
    public boolean execute(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("admin")) {
            return executeAdmin(sender, label, args);
        }

        if (sub.equals("spy")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Text.c(CommandMessages.error("&cOnly players can toggle guild chat spy.")));
                return true;
            }
            if (!isMod(p)) {
                p.sendMessage(Text.c(CommandMessages.error("&cNo permission.")));
                return true;
            }
            guilds.toggleGuildChatSpy(p.getUniqueId());
            p.sendMessage(Text.c(CommandMessages.command("&aGuild chat spy: " + (guilds.isGuildChatSpy(p.getUniqueId()) ? "&2ON" : "&cOFF"))));
            return true;
        }

        if (sub.equals("create")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Text.c(CommandMessages.error("&cOnly players can create guilds.")));
                return true;
            }
            if (args.length < 3) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " create <name> <tag>")));
                return true;
            }
            String name = args[1];
            String tag = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

            var res = guilds.createGuild(name, tag, p.getUniqueId());
            switch (res) {
                case OK -> {
                    p.sendMessage(Text.c(CommandMessages.command("&aCreated guild &f" + name + " &awith tag &r" + tag)));
                    broadcast(CommandMessages.server("&e" + p.getName() + " &ahas created the guild &f" + name + "&a."));
                }
                case ALREADY_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are already in a guild.")));
                case NAME_TAKEN -> p.sendMessage(Text.c(CommandMessages.error("&cThat guild name already exists.")));
            }
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Text.c(CommandMessages.error("&cOnly players can use that guild command.")));
            return true;
        }

        if (sub.equals("invite")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " invite <player>")));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                p.sendMessage(Text.c(CommandMessages.error("&cPlayer not found (must be online).")));
                return true;
            }
            if (target.getUniqueId().equals(p.getUniqueId())) {
                p.sendMessage(Text.c(CommandMessages.error("&cYou cannot invite yourself.")));
                return true;
            }

            Guild g = guilds.guildOf(p.getUniqueId());
            var res = guilds.invite(p.getUniqueId(), target.getUniqueId());
            switch (res) {
                case OK -> {
                    String guildName = g == null ? "your guild" : g.name();
                    p.sendMessage(Text.c(CommandMessages.command("&aSuccessfully invited &f" + target.getName() + " &ato your guild.")));
                    target.sendMessage(Text.c(CommandMessages.command("&e" + p.getName() + " &ahas invited you to &f" + guildName + "&a. Would you like to accept?")));
                    target.sendMessage(Text.c(CommandMessages.command("")).append(inviteButtons(label)));
                }
                case NOT_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                case NO_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cYou do not have permission to invite players.")));
                case TARGET_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cThat player is already in a guild.")));
            }
            return true;
        }

        if (sub.equals("revoke") || sub.equals("uninvite") || sub.equals("cancelinvite")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " " + sub + " <player>")));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                p.sendMessage(Text.c(CommandMessages.error("&cPlayer not found (must be online).")));
                return true;
            }
            var res = guilds.revokeInvite(p.getUniqueId(), target.getUniqueId());
            switch (res) {
                case OK -> {
                    p.sendMessage(Text.c(CommandMessages.command("&aRevoked &f" + target.getName() + "&a's guild invite.")));
                    target.sendMessage(Text.c(CommandMessages.command("&eYour guild invite from &f" + p.getName() + " &ewas revoked.")));
                }
                case NOT_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                case NO_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cYou do not have permission to revoke invites.")));
                case NO_INVITE -> p.sendMessage(Text.c(CommandMessages.error("&cThat player does not have a pending invite.")));
                case WRONG_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cThat pending invite is not for your guild.")));
            }
            return true;
        }

        if (sub.equals("join") || sub.equals("accept")) {
            String guildName = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            var res = guilds.join(p.getUniqueId(), guildName);
            switch (res) {
                case OK -> p.sendMessage(Text.c(CommandMessages.command("&aJoined the guild!")));
                case NO_INVITE -> p.sendMessage(Text.c(CommandMessages.error("&cYou don't have an invite.")));
                case INVITE_EXPIRED -> p.sendMessage(Text.c(CommandMessages.error("&cYour invite expired. Ask for a new one.")));
                case ALREADY_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are already in a guild.")));
                case GUILD_MISSING -> p.sendMessage(Text.c(CommandMessages.error("&cThat guild no longer exists.")));
                case NOT_OPEN -> p.sendMessage(Text.c(CommandMessages.error("&cThat guild is not open to the public.")));
            }
            return true;
        }

        if (sub.equals("deny") || sub.equals("decline")) {
            boolean denied = guilds.denyInvite(p.getUniqueId());
            p.sendMessage(Text.c(denied
                    ? CommandMessages.command("&cGuild invite denied.")
                    : CommandMessages.error("&cYou don't have an invite.")));
            return true;
        }

        if (sub.equals("kick")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " kick <player>")));
                return true;
            }
            GuildManager.KickResult res = guilds.kickMember(p.getUniqueId(), args[1]);
            switch (res) {
                case OK -> {
                    p.sendMessage(Text.c(CommandMessages.command("&aKicked &f" + args[1] + " &afrom your guild.")));
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target != null) target.sendMessage(Text.c(CommandMessages.command("&cYou were kicked from your guild.")));
                }
                case NO_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                case NO_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cYou do not have permission to kick members.")));
                case TARGET_NOT_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cThat player is not in your guild.")));
                case CANNOT_KICK_OWNER -> p.sendMessage(Text.c(CommandMessages.error("&cYou cannot kick the guild owner.")));
            }
            return true;
        }

        if (sub.equals("transfer")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " transfer <player>")));
                return true;
            }

            Guild before = guilds.guildOf(p.getUniqueId());
            GuildManager.TransferResult res = guilds.transferOwnership(p.getUniqueId(), args[1]);
            switch (res) {
                case OK -> {
                    Guild after = guilds.guildOf(p.getUniqueId());
                    String guildName = after == null && before != null ? before.name() : after == null ? "the guild" : after.name();
                    String newOwnerName = after == null ? args[1] : guilds.nameOf(after.owner());
                    p.sendMessage(Text.c(CommandMessages.command("&aTransferred ownership of &f" + guildName + " &ato &f" + newOwnerName + "&a.")));
                    if (after != null) {
                        Player target = Bukkit.getPlayer(after.owner());
                        if (target != null) {
                            target.sendMessage(Text.c(CommandMessages.command("&e" + p.getName() + " &ahas transferred ownership of &f" + guildName + " &ato you.")));
                        }
                    }
                }
                case NO_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                case NOT_OWNER -> p.sendMessage(Text.c(CommandMessages.error("&cOnly the guild owner can transfer ownership.")));
                case TARGET_NOT_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cThat player must already be a member of your guild.")));
                case ALREADY_OWNER -> p.sendMessage(Text.c(CommandMessages.error("&cYou already own this guild.")));
                case STORAGE_ERROR -> p.sendMessage(Text.c(CommandMessages.error("&cCould not transfer guild ownership because the database update failed.")));
            }
            return true;
        }

        if (sub.equals("leave")) {
            if (!guilds.isInGuild(p.getUniqueId())) {
                p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                return true;
            }
            Guild g = guilds.guildOf(p.getUniqueId());
            if (g != null && g.isOwner(p.getUniqueId())) {
                p.sendMessage(Text.c(CommandMessages.error("&cOwner can't leave. Use &e/" + label + " disband&c.")));
                return true;
            }
            boolean ok = guilds.leaveGuild(p.getUniqueId());
            p.sendMessage(Text.c(ok ? CommandMessages.command("&aLeft the guild.") : CommandMessages.error("&cFailed to leave guild.")));
            return true;
        }

        if (sub.equals("disband")) {
            if (args.length >= 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin <guild> disband")));
                return true;
            }

            Guild g = guilds.guildOf(p.getUniqueId());
            if (g == null) {
                p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                return true;
            }
            if (!g.isOwner(p.getUniqueId())) {
                p.sendMessage(Text.c(CommandMessages.error("&cOnly the owner can disband.")));
                return true;
            }
            String guildName = g.name();
            guilds.disbandGuild(g);
            p.sendMessage(Text.c(CommandMessages.command("&aGuild disbanded.")));
            broadcast(CommandMessages.server("&c" + p.getName() + " &chas disbanded the guild &f" + guildName + "&c."));
            return true;
        }

        if (sub.equals("tag")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " tag <newTag>")));
                return true;
            }
            String tag = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            boolean ok = guilds.setGuildTag(p.getUniqueId(), tag);
            p.sendMessage(Text.c(ok ? CommandMessages.command("&aGuild tag updated to &r" + tag) : CommandMessages.error("&cYou do not have permission to change the guild tag.")));
            return true;
        }

        if (sub.equals("rank")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " rank <create|delete|set|rank> ...")));
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("create")) {
                if (args.length < 4) {
                    p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " rank create <name> <prefix>")));
                    return true;
                }
                String rankName = args[2];
                String prefix = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                boolean ok = guilds.createRank(p.getUniqueId(), rankName, prefix);
                p.sendMessage(Text.c(ok ? CommandMessages.command("&aCreated guild rank &f" + rankName) : CommandMessages.error("&cCould not create rank. You need permission, and OWNER/MEMBER are reserved.")));
                return true;
            }
            if (action.equals("delete")) {
                if (args.length < 3) {
                    p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " rank delete <name>")));
                    return true;
                }
                boolean ok = guilds.deleteRank(p.getUniqueId(), args[2]);
                p.sendMessage(Text.c(ok ? CommandMessages.command("&aDeleted guild rank &f" + args[2]) : CommandMessages.error("&cCould not delete rank.")));
                return true;
            }
            if (action.equals("set")) {
                if (args.length < 4) {
                    p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " rank set <player> <rank>")));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    p.sendMessage(Text.c(CommandMessages.error("&cPlayer not found (must be online).")));
                    return true;
                }
                var res = guilds.setRank(p.getUniqueId(), target.getUniqueId(), args[3]);
                switch (res) {
                    case OK -> p.sendMessage(Text.c(CommandMessages.command("&aSet &f" + target.getName() + "&a to rank &f" + args[3])));
                    case NO_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                    case NO_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cYou do not have permission to set ranks.")));
                    case TARGET_NOT_IN_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cThat player is not in your guild.")));
                    case RANK_NOT_FOUND -> p.sendMessage(Text.c(CommandMessages.error("&cRank not found.")));
                }
                return true;
            }

            if (args.length >= 4 && (args[2].equalsIgnoreCase("allow") || args[2].equalsIgnoreCase("deny"))) {
                boolean allow = args[2].equalsIgnoreCase("allow");
                var res = guilds.setRankPermission(p.getUniqueId(), args[1], args[3], allow);
                switch (res) {
                    case OK -> p.sendMessage(Text.c(CommandMessages.command("&aUpdated permission &f" + GuildManager.normalizePermission(args[3]) + " &afor rank &f" + args[1] + "&a.")));
                    case NO_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                    case NO_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cYou do not have permission to edit rank permissions.")));
                    case RANK_NOT_FOUND -> p.sendMessage(Text.c(CommandMessages.error("&cRank not found.")));
                    case INVALID_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cInvalid permission. Try one of: &7" + String.join(", ", GuildManager.RANK_PERMISSIONS))));
                }
                return true;
            }

            p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " rank <create|delete|set> ...")));
            p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " rank <rank> allow/deny <permission>")));
            return true;
        }

        if (sub.equals("toggle")) {
            Guild g = guilds.guildOf(p.getUniqueId());
            if (g == null) {
                p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                return true;
            }
            if (args.length < 2) {
                sendToggles(p, label, g);
                return true;
            }
            var res = guilds.toggle(p.getUniqueId(), args[1]);
            switch (res) {
                case OK -> sendToggles(p, label, guilds.guildOf(p.getUniqueId()));
                case NO_GUILD -> p.sendMessage(Text.c(CommandMessages.error("&cYou are not in a guild.")));
                case NO_PERMISSION -> p.sendMessage(Text.c(CommandMessages.error("&cYou do not have permission to change guild toggles.")));
                case INVALID_TOGGLE -> p.sendMessage(Text.c(CommandMessages.error("&cInvalid toggle. Try &eopen &cor &epublic&c.")));
            }
            return true;
        }

        if (sub.equals("sethome")) {
            boolean ok = guilds.setHome(p.getUniqueId(), p.getLocation());
            p.sendMessage(Text.c(ok ? CommandMessages.command("&aGuild home set to your current location.") : CommandMessages.error("&cYou do not have permission to set the guild home.")));
            return true;
        }

        if (sub.equals("home")) {
            Location loc;
            if (args.length >= 2) {
                String guildName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                loc = guilds.publicHome(guildName);
                if (loc == null) {
                    p.sendMessage(Text.c(CommandMessages.error("&cThat guild does not have a public home.")));
                    return true;
                }
            } else {
                loc = guilds.homeFor(p.getUniqueId());
                if (loc == null) {
                    p.sendMessage(Text.c(CommandMessages.error("&cYour guild does not have a home set.")));
                    return true;
                }
            }
            p.teleport(loc);
            p.sendMessage(Text.c(CommandMessages.command("&aTeleported to guild home.")));
            return true;
        }

        if (sub.equals("setwarp")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " setwarp <warp>")));
                return true;
            }
            boolean ok = guilds.setWarp(p.getUniqueId(), args[1], p.getLocation());
            p.sendMessage(Text.c(ok ? CommandMessages.command("&aSet guild warp &f" + args[1] + "&a.") : CommandMessages.error("&cYou must be in a guild to set a guild warp.")));
            return true;
        }

        if (sub.equals("delwarp")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " delwarp <warp>")));
                return true;
            }
            boolean ok = guilds.deleteWarp(p.getUniqueId(), args[1]);
            p.sendMessage(Text.c(ok ? CommandMessages.command("&aDeleted guild warp &f" + args[1] + "&a.") : CommandMessages.error("&cCould not delete that warp. You may not have permission, or it does not exist.")));
            return true;
        }

        if (sub.equals("warp")) {
            if (args.length < 2) {
                p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " warp <warp>")));
                return true;
            }
            Location loc = guilds.warp(p.getUniqueId(), args[1]);
            if (loc == null) {
                p.sendMessage(Text.c(CommandMessages.error("&cGuild warp not found.")));
                return true;
            }
            p.teleport(loc);
            p.sendMessage(Text.c(CommandMessages.command("&aTeleported to guild warp &f" + args[1] + "&a.")));
            return true;
        }

        if (sub.equals("listwarps")) {
            Set<String> warps = guilds.warpNames(p.getUniqueId());
            p.sendMessage(Text.c(CommandMessages.command("&eGuild warps: " + (warps.isEmpty() ? "&7(none)" : "&f" + String.join("&7, &f", warps)))));
            return true;
        }

        if (sub.equals("info")) {
            Guild g;
            if (args.length >= 2) {
                g = guilds.getGuildByName(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                if (g == null) {
                    p.sendMessage(Text.c(CommandMessages.error("&cGuild not found.")));
                    return true;
                }
            } else {
                g = guilds.guildOf(p.getUniqueId());
                if (g == null) {
                    p.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " info <guild>")));
                    p.sendMessage(Text.c(CommandMessages.error("&7You are not in a guild, so choose a guild name to view.")));
                    return true;
                }
            }
            sendGuildInfo(p, g);
            return true;
        }

        p.sendMessage(Text.c(CommandMessages.error("&cUnknown subcommand. Try /" + label)));
        sendHelp(p, label);
        return true;
    }

    private boolean executeAdmin(CommandSender sender, String label, String[] args) {
        if (!isSuperuser(sender)) {
            sender.sendMessage(Text.c(CommandMessages.error("&cNo permission.")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin <guild> <command>")));
            sender.sendMessage(Text.c(CommandMessages.command("&7Example: /" + label + " admin GuildName kick <player>")));
            return true;
        }

        Guild g = guilds.getGuildByName(args[1]);
        if (g == null) {
            sender.sendMessage(Text.c(CommandMessages.error("&cGuild not found.")));
            return true;
        }

        String adminSub = args[2].toLowerCase(Locale.ROOT);

        if (adminSub.equals("disband")) {
            String guildName = g.name();
            guilds.disbandGuild(g);
            sender.sendMessage(Text.c(CommandMessages.command("&aDisbanded guild &f" + guildName + "&a.")));
            broadcast(CommandMessages.server("&cThe guild &f" + guildName + " &chas been disbanded by an admin."));
            return true;
        }

        if (adminSub.equals("kick")) {
            if (args.length < 4) {
                sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " kick <player>")));
                return true;
            }
            GuildManager.KickResult res = guilds.kickMemberFromGuild(g, args[3]);
            switch (res) {
                case OK -> {
                    sender.sendMessage(Text.c(CommandMessages.command("&aKicked &f" + args[3] + " &afrom &f" + g.name() + "&a.")));
                    Player target = Bukkit.getPlayerExact(args[3]);
                    if (target != null) target.sendMessage(Text.c(CommandMessages.command("&cYou were kicked from your guild.")));
                }
                case NO_GUILD -> sender.sendMessage(Text.c(CommandMessages.error("&cGuild not found.")));
                case NO_PERMISSION -> sender.sendMessage(Text.c(CommandMessages.error("&cNo permission.")));
                case TARGET_NOT_IN_GUILD -> sender.sendMessage(Text.c(CommandMessages.error("&cThat player is not in that guild.")));
                case CANNOT_KICK_OWNER -> sender.sendMessage(Text.c(CommandMessages.error("&cYou cannot kick the guild owner.")));
            }
            return true;
        }

        if (adminSub.equals("tag")) {
            if (args.length < 4) {
                sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " tag <tag>")));
                return true;
            }
            String tag = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            g.setTag(tag);
            guilds.save();
            sender.sendMessage(Text.c(CommandMessages.command("&aUpdated &f" + g.name() + "&a's guild tag to &r" + tag)));
            return true;
        }

        if (adminSub.equals("sethome")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Text.c(CommandMessages.error("&cOnly players can set a guild home from their location.")));
                return true;
            }
            guilds.adminSetHome(g, p.getLocation());
            sender.sendMessage(Text.c(CommandMessages.command("&aSet &f" + g.name() + "&a's guild home to your current location.")));
            return true;
        }

        if (adminSub.equals("delwarp")) {
            if (args.length < 4) {
                sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " delwarp <warp>")));
                return true;
            }
            boolean ok = guilds.adminDeleteWarp(g, args[3]);
            sender.sendMessage(Text.c(ok ? CommandMessages.command("&aDeleted guild warp &f" + args[3] + "&a.") : CommandMessages.error("&cGuild warp not found.")));
            return true;
        }

        if (adminSub.equals("toggle")) {
            if (args.length < 4) {
                sendToggles(sender, label, g);
                return true;
            }
            String toggle = args[3].toLowerCase(Locale.ROOT);
            if (toggle.equals("open")) g.setOpen(!g.isOpen());
            else if (toggle.equals("public")) g.setPublicHome(!g.isPublicHome());
            else {
                sender.sendMessage(Text.c(CommandMessages.error("&cInvalid toggle. Try &eopen &cor &epublic&c.")));
                return true;
            }
            guilds.save();
            sendToggles(sender, label, g);
            return true;
        }

        if (adminSub.equals("rank")) {
            if (args.length < 5) {
                sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " rank <create|delete|set|rank> ...")));
                return true;
            }
            String action = args[3].toLowerCase(Locale.ROOT);
            if (action.equals("create")) {
                if (args.length < 6) {
                    sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " rank create <name> <prefix>")));
                    return true;
                }
                String rankName = args[4].toUpperCase(Locale.ROOT);
                if (rankName.equals("OWNER") || rankName.equals("MEMBER")) {
                    sender.sendMessage(Text.c(CommandMessages.error("&cOWNER and MEMBER are reserved.")));
                    return true;
                }
                String prefix = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
                g.createRank(rankName, prefix);
                guilds.save();
                sender.sendMessage(Text.c(CommandMessages.command("&aCreated rank &f" + rankName + " &ain &f" + g.name() + "&a.")));
                return true;
            }
            if (action.equals("delete")) {
                String rankName = args[4].toUpperCase(Locale.ROOT);
                if (rankName.equals("OWNER") || rankName.equals("MEMBER") || !g.rankExists(rankName)) {
                    sender.sendMessage(Text.c(CommandMessages.error("&cRank not found or protected.")));
                    return true;
                }
                Map<UUID, String> oldPrefixes = guilds.oldPrefixesForRank(g, rankName);
                g.deleteRank(rankName);
                guilds.save();
                guilds.refreshGuildRankPrefixes(oldPrefixes);
                sender.sendMessage(Text.c(CommandMessages.command("&aDeleted rank &f" + rankName + " &afrom &f" + g.name() + "&a.")));
                return true;
            }
            if (action.equals("set")) {
                if (args.length < 6) {
                    sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " rank set <player> <rank>")));
                    return true;
                }
                UUID target = guilds.memberByName(g, args[4]);
                if (target == null) {
                    sender.sendMessage(Text.c(CommandMessages.error("&cThat player is not in that guild.")));
                    return true;
                }
                String rankName = args[5].toUpperCase(Locale.ROOT);
                if (!g.rankExists(rankName)) {
                    sender.sendMessage(Text.c(CommandMessages.error("&cRank not found.")));
                    return true;
                }
                String oldPrefix = g.visiblePrefixFor(target);
                g.setMemberRank(target, rankName);
                guilds.save();
                guilds.refreshGuildRankPrefix(target, oldPrefix);
                sender.sendMessage(Text.c(CommandMessages.command("&aSet &f" + guilds.nameOf(target) + "&a to rank &f" + rankName + "&a.")));
                return true;
            }
            if (args.length >= 6 && (args[4].equalsIgnoreCase("allow") || args[4].equalsIgnoreCase("deny"))) {
                String rankName = args[3].toUpperCase(Locale.ROOT);
                String permission = GuildManager.normalizePermission(args[5]);
                if (!GuildManager.RANK_PERMISSIONS.contains(permission)) {
                    sender.sendMessage(Text.c(CommandMessages.error("&cInvalid permission. Try one of: &7" + String.join(", ", GuildManager.RANK_PERMISSIONS))));
                    return true;
                }
                if (!g.rankExists(rankName)) {
                    sender.sendMessage(Text.c(CommandMessages.error("&cRank not found.")));
                    return true;
                }
                if (args[4].equalsIgnoreCase("allow")) g.allowPermission(rankName, permission);
                else g.denyPermission(rankName, permission);
                guilds.save();
                sender.sendMessage(Text.c(CommandMessages.command("&aUpdated permission &f" + permission + " &afor rank &f" + rankName + "&a.")));
                return true;
            }
            sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " rank <create|delete|set> ...")));
            sender.sendMessage(Text.c(CommandMessages.usage("&cUsage: /" + label + " admin " + g.name() + " rank <rank> allow/deny <permission>")));
            return true;
        }

        sender.sendMessage(Text.c(CommandMessages.error("&cUnknown admin guild command. Try /" + label + " admin <guild> <kick|tag|rank|toggle|sethome|delwarp|disband>")));
        return true;
    }

    private void broadcast(String message) {
        Component component = Text.c(message);
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(component);
    }

    private Component inviteButtons(String label) {
        Component yes = Text.c("&a[YES]")
                .clickEvent(ClickEvent.runCommand("/" + label + " join"))
                .hoverEvent(HoverEvent.showText(Text.c("&aClick to accept this guild invite.")));
        Component no = Text.c("&c[NO]")
                .clickEvent(ClickEvent.runCommand("/" + label + " deny"))
                .hoverEvent(HoverEvent.showText(Text.c("&cClick to deny this guild invite.")));
        return yes.append(Component.space()).append(no);
    }

    private void sendGuildInfo(CommandSender sender, Guild g) {
        sender.sendMessage(Text.c(CommandMessages.command("&8&m----------------&r &6Guild Info &8&m----------------")));
        sender.sendMessage(Text.c(CommandMessages.command("&eGuild: &f" + g.name())));
        sender.sendMessage(Text.c(CommandMessages.command("&eTag: &r" + g.tag())));
        sender.sendMessage(Text.c(CommandMessages.command("&eOwner: &f" + guilds.nameOf(g.owner()))));
        sender.sendMessage(Text.c(CommandMessages.command("&eMembers: &f" + g.members().size())));
        sender.sendMessage(Text.c(CommandMessages.command("&eOpen Joining: " + (g.isOpen() ? "&aEnabled" : "&cDisabled"))));
        sender.sendMessage(Text.c(CommandMessages.command("&ePublic Home: " + (g.isPublicHome() ? "&aEnabled" : "&cDisabled"))));
        sender.sendMessage(Text.c(CommandMessages.command("&eHome Set: " + (g.home() == null ? "&cNo" : "&aYes"))));
        sender.sendMessage(Text.c(CommandMessages.command("&eWarps: &f" + g.warps().size())));

        String memberList = g.members().entrySet().stream()
                .sorted(Comparator.comparing(e -> guilds.nameOf(e.getKey()).toLowerCase(Locale.ROOT)))
                .map(e -> "&f" + guilds.nameOf(e.getKey()) + " &7(" + e.getValue() + ")")
                .collect(Collectors.joining("&7, "));
        sender.sendMessage(Text.c(CommandMessages.command("&eMember list: " + (memberList.isEmpty() ? "&7(none)" : memberList))));

        String ranks = g.ranks().entrySet().stream()
                .filter(e -> !e.getKey().equalsIgnoreCase("OWNER") && !e.getKey().equalsIgnoreCase("MEMBER"))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&7, &r"));
        if (ranks.isEmpty()) ranks = "&7(none)";
        sender.sendMessage(Text.c(CommandMessages.command("&eCustom ranks: &r" + ranks)));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.c(CommandMessages.command("&8&m----------------&r &6Guild Commands &8&m----------------")));
        sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " help")));
        sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " create <name> <tag>")));
        sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " info [guild]")));
        sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " join [guild] &8- &7Accept invite or join an open guild")));
        sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " deny &8- &7Deny your pending invite")));

        if (sender instanceof Player p) {
            Guild g = guilds.guildOf(p.getUniqueId());
            if (g != null) {
                sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " leave")));
                sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " home [guild]")));
                sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " setwarp <warp>")));
                sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " warp <warp>")));
                sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " listwarps")));

                if (canUse(p, "invite")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " invite <player>")));
                if (g.isOwner(p.getUniqueId())) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " revoke <player>")));
                if (canUse(p, "tag")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " tag <tag>")));
                if (canUse(p, "kick")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " kick <player>")));
                if (canUse(p, "delwarp")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " delwarp <warp>")));
                if (canUse(p, "sethome")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " sethome")));
                if (canUse(p, "toggle")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " toggle [open|public]")));
                if (canUse(p, "rank.create")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " rank create <name> <prefix>")));
                if (canUse(p, "rank.delete")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " rank delete <name>")));
                if (canUse(p, "rank.set")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " rank set <player> <rank>")));
                if (canUse(p, "rank.allow")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " rank <rank> allow <permission>")));
                if (canUse(p, "rank.deny")) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " rank <rank> deny <permission>")));
                if (g.isOwner(p.getUniqueId())) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " transfer <player>")));
                if (g.isOwner(p.getUniqueId())) sender.sendMessage(Text.c(CommandMessages.command("&7/" + label + " disband")));

                Set<String> perms = guilds.permissionsFor(p.getUniqueId());
                if (!perms.isEmpty() && !perms.contains("OWNER")) {
                    sender.sendMessage(Text.c(CommandMessages.command("&eYour rank permissions: &f" + String.join("&7, &f", new TreeSet<>(perms)))));
                }
            }
        }

        if (sender.hasPermission("sandbox.staff")) sender.sendMessage(Text.c(CommandMessages.error("&c(MOD) &7/" + label + " spy")));
        if (sender.hasPermission("sandbox.superuser")) sender.sendMessage(Text.c(CommandMessages.error("&c(SUPERUSER) &7/" + label + " admin <guild> <command>")));
    }

    private void sendToggles(CommandSender p, String label, Guild g) {
        p.sendMessage(Text.c(CommandMessages.command("&8&m----------------&r &6Guild Toggles &8&m----------------")));
        p.sendMessage(Text.c(CommandMessages.command("&eopen: " + (g.isOpen() ? "&aEnabled" : "&cDisabled") + " &8- &7/" + label + " join " + g.name())));
        p.sendMessage(Text.c(CommandMessages.command("&epublic: " + (g.isPublicHome() ? "&aEnabled" : "&cDisabled") + " &8- &7/" + label + " home " + g.name())));
    }

    private boolean canUse(Player p, String permission) {
        return guilds.hasGuildPermission(p.getUniqueId(), permission);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return prefix(args[0], visibleSubcommands(sender));

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && (sub.equals("invite") || sub.equals("revoke") || sub.equals("uninvite") || sub.equals("cancelinvite"))) return onlinePlayerNames(args[1]);

        if (args.length == 2 && sub.equals("kick") && sender instanceof Player p) {
            Guild g = guilds.guildOf(p.getUniqueId());
            if (g == null) return List.of();
            return prefix(args[1], g.members().keySet().stream().filter(u -> !g.isOwner(u)).map(guilds::nameOf).toList());
        }

        if (args.length == 2 && sub.equals("transfer") && sender instanceof Player p) {
            Guild g = guilds.guildOf(p.getUniqueId());
            if (g == null || !g.isOwner(p.getUniqueId())) return List.of();
            return prefix(args[1], g.members().keySet().stream().filter(u -> !g.isOwner(u)).map(guilds::nameOf).toList());
        }

        if (args.length == 2 && (sub.equals("info") || sub.equals("join") || sub.equals("home"))) {
            if (sub.equals("join")) return prefix(args[1], guilds.allGuilds().stream().filter(Guild::isOpen).map(Guild::name).toList());
            if (sub.equals("home")) return prefix(args[1], guilds.allGuilds().stream().filter(Guild::isPublicHome).map(Guild::name).toList());
            return prefix(args[1], guilds.allGuilds().stream().map(Guild::name).toList());
        }

        if (args.length == 2 && sub.equals("toggle")) return prefix(args[1], GuildManager.TOGGLES);

        if (args.length == 2 && (sub.equals("warp") || sub.equals("delwarp")) && sender instanceof Player p) return prefix(args[1], new ArrayList<>(guilds.warpNames(p.getUniqueId())));

        if (args.length >= 2 && sub.equals("admin") && isSuperuser(sender)) return adminTab(sender, args);

        if (args.length >= 2 && sub.equals("rank")) {
            Guild g = sender instanceof Player p ? guilds.guildOf(p.getUniqueId()) : null;
            if (args.length == 2) {
                List<String> options = new ArrayList<>(List.of("create", "delete", "set"));
                if (g != null) options.addAll(g.ranks().keySet());
                return prefix(args[1], options);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("set")) return onlinePlayerNames(args[2]);
            if (args.length == 3 && args[1].equalsIgnoreCase("delete") && g != null) {
                return prefix(args[2], g.ranks().keySet().stream().filter(r -> !r.equalsIgnoreCase("OWNER") && !r.equalsIgnoreCase("MEMBER")).toList());
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("set") && g != null) return prefix(args[3], new ArrayList<>(g.ranks().keySet()));
            if (args.length == 3 && g != null && g.rankExists(args[1])) return prefix(args[2], List.of("allow", "deny"));
            if (args.length == 4 && g != null && g.rankExists(args[1]) && (args[2].equalsIgnoreCase("allow") || args[2].equalsIgnoreCase("deny"))) return prefix(args[3], GuildManager.RANK_PERMISSIONS);
        }
        return List.of();
    }

    private List<String> visibleSubcommands(CommandSender sender) {
        List<String> options = new ArrayList<>(List.of("help", "create", "info", "join", "accept", "deny", "decline"));
        if (sender.hasPermission("sandbox.staff")) options.add("spy");
        if (sender.hasPermission("sandbox.superuser")) options.add("admin");
        if (!(sender instanceof Player p)) return options;
        Guild g = guilds.guildOf(p.getUniqueId());
        if (g == null) return options;
        options.addAll(List.of("leave", "home", "setwarp", "warp", "listwarps"));
        if (canUse(p, "invite")) options.add("invite");
        if (g.isOwner(p.getUniqueId())) options.add("revoke");
        if (canUse(p, "tag")) options.add("tag");
        if (canUse(p, "kick")) options.add("kick");
        if (canUse(p, "delwarp")) options.add("delwarp");
        if (canUse(p, "sethome")) options.add("sethome");
        if (canUse(p, "toggle")) options.add("toggle");
        if (canUse(p, "rank.create") || canUse(p, "rank.delete") || canUse(p, "rank.set") || canUse(p, "rank.allow") || canUse(p, "rank.deny")) options.add("rank");
        if (g.isOwner(p.getUniqueId())) options.add("transfer");
        if (g.isOwner(p.getUniqueId())) options.add("disband");
        return options;
    }

    private List<String> adminTab(CommandSender sender, String[] args) {
        if (args.length == 2) return prefix(args[1], guilds.allGuilds().stream().map(Guild::name).toList());

        Guild g = guilds.getGuildByName(args[1]);
        if (g == null) return List.of();

        if (args.length == 3) {
            return prefix(args[2], List.of("kick", "tag", "rank", "toggle", "sethome", "delwarp", "disband"));
        }

        String sub = args[2].toLowerCase(Locale.ROOT);
        if (args.length == 4 && sub.equals("kick")) {
            return prefix(args[3], g.members().keySet().stream().filter(u -> !g.isOwner(u)).map(guilds::nameOf).toList());
        }
        if (args.length == 4 && sub.equals("delwarp")) {
            return prefix(args[3], new ArrayList<>(g.warps().keySet()));
        }
        if (args.length == 4 && sub.equals("toggle")) {
            return prefix(args[3], GuildManager.TOGGLES);
        }
        if (sub.equals("rank")) {
            if (args.length == 4) {
                List<String> options = new ArrayList<>(List.of("create", "delete", "set"));
                options.addAll(g.ranks().keySet());
                return prefix(args[3], options);
            }
            if (args.length == 5 && args[3].equalsIgnoreCase("set")) {
                return prefix(args[4], g.members().keySet().stream().map(guilds::nameOf).toList());
            }
            if (args.length == 5 && args[3].equalsIgnoreCase("delete")) {
                return prefix(args[4], g.ranks().keySet().stream().filter(r -> !r.equalsIgnoreCase("OWNER") && !r.equalsIgnoreCase("MEMBER")).toList());
            }
            if (args.length == 6 && args[3].equalsIgnoreCase("set")) {
                return prefix(args[5], new ArrayList<>(g.ranks().keySet()));
            }
            if (args.length == 5 && g.rankExists(args[3])) {
                return prefix(args[4], List.of("allow", "deny"));
            }
            if (args.length == 6 && g.rankExists(args[3]) && (args[4].equalsIgnoreCase("allow") || args[4].equalsIgnoreCase("deny"))) {
                return prefix(args[5], GuildManager.RANK_PERMISSIONS);
            }
        }
        return List.of();
    }

    private List<String> onlinePlayerNames(String start) {
        return prefix(start, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    private List<String> prefix(String start, List<String> options) {
        String s = start == null ? "" : start.toLowerCase(Locale.ROOT);
        return options.stream().filter(Objects::nonNull).filter(o -> o.toLowerCase(Locale.ROOT).startsWith(s)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
