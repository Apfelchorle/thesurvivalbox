package org.thesandbox.core.commands.Fun;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
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
import org.thesandbox.core.commands.CommandMessages;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.tags.TagService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * /tagnyan [text...]
 * - If text is provided: nyan-ify that text and set as your tag (session-only).
 * - If no text: nyan-ify your existing tag (if any).
 * - Use /tag save to persist the current tag afterward.
 */
public class TagNyanCommand implements ISubCommand
{
    private static final int PREFIX_PRIORITY = 200; // keep in sync with TagCommand
    private static final int MAX_VISIBLE_LEN = 30;  // visible chars only (no color codes)

    private final JavaPlugin plugin;
    @SuppressWarnings("unused")
    private final TagService tagService; // saving is handled via /tag save, not here

    public TagNyanCommand(JavaPlugin plugin, TagService tagService)
    {
        this.plugin = plugin;
        this.tagService = tagService;
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player p))
        {
            sender.sendMessage(CommandMessages.error(ChatColor.RED + "Only players can use /" + label + "."));
            return true;
        }

        final boolean hasInput = args.length > 0;
        final String input = hasInput ? joinWithSpaces(args) : null;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try
            {
                LuckPerms lp = LuckPermsProvider.get();
                UserManager um = lp.getUserManager();
                User user = um.loadUser(p.getUniqueId()).get(5, TimeUnit.SECONDS);
                if (user == null)
                {
                    p.sendMessage(CommandMessages.error(ChatColor.RED + "LuckPerms user not found."));
                    return;
                }

                String baseVisible;
                if (hasInput)
                {
                    // Blacklist (skip if bypass)
                    if (!p.hasPermission("sandbox.mb"))
                    {
                        String blocked = firstBlacklistedTerm(input);
                        if (blocked != null)
                        {
                            p.sendMessage(CommandMessages.error(ChatColor.RED + "That tag contains a blocked term: " + ChatColor.YELLOW + blocked));
                            return;
                        }
                    }
                    // Allow &-codes in input, then strip to visible text
                    String legacy = ChatColor.translateAlternateColorCodes('&', input);
                    baseVisible = stripAllColors(legacy).trim();
                }
                else
                {
                    // No args -> use current tag at managed priority
                    String current = currentPrefixAtPriority(user, PREFIX_PRIORITY);
                    if (current == null || current.isEmpty())
                    {
                        p.sendMessage(CommandMessages.usage(ChatColor.RED + "Usage: /tagnyan <tag>"));
                        return;
                    }
                    baseVisible = stripAllColors(current).trim();
                }

                if (baseVisible.isEmpty())
                {
                    p.sendMessage(CommandMessages.error(ChatColor.RED + "Your tag text has no visible characters."));
                    return;
                }

                // Visible-length enforcement
                int visibleLen = baseVisible.length();
                if (visibleLen > MAX_VISIBLE_LEN)
                {
                    p.sendMessage(CommandMessages.error(ChatColor.RED + "That tag is too long (" + visibleLen + "/" + MAX_VISIBLE_LEN + "). Shorten it."));
                    return;
                }

                String nyan = nyanify(baseVisible);
                String finalPrefix = ensureSingleSpaceAfterTag(nyan); // trims whitespace + dangling codes, adds one space + reset

                // Apply session-only: remove existing nodes at our priority, then add new one
                removePrefixNodes(user);
                user.data().add(PrefixNode.builder(finalPrefix, PREFIX_PRIORITY).build());
                um.saveUser(user).get(5, TimeUnit.SECONDS);
                user.getCachedData().invalidate();

                p.sendMessage(CommandMessages.command(ChatColor.GREEN + "Nyan tag applied (session-only)."));
                String nameToShow = getEssentialsNickOrName(p);
                p.sendMessage(CommandMessages.command(ChatColor.GRAY + "Preview: " + finalPrefix + ChatColor.RESET + nameToShow));
                p.sendMessage(CommandMessages.command(ChatColor.LIGHT_PURPLE + "Want to save your tag? Run " + ChatColor.RED + "/tag save" + ChatColor.LIGHT_PURPLE + " to persist your current tag."));
            }
            catch (Exception e)
            {
                p.sendMessage(CommandMessages.error(ChatColor.RED + "Error: " + e.getMessage()));
            }
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        return List.of();
    }

    // ===== Helpers =====

    /** Returns the current prefix value at a given priority, or null if none. */
    private static String currentPrefixAtPriority(User user, int priority)
    {
        for (Node n : user.data().toCollection())
        {
            if (n instanceof PrefixNode pn && pn.getPriority() == priority)
            {
                return pn.getMetaValue();
            }
        }
        return null;
    }

    /** Remove prefix nodes at the priority we manage. */
    private static void removePrefixNodes(User user)
    {
        List<Node> toRemove = new ArrayList<>();
        for (Node n : user.data().toCollection())
        {
            if (n instanceof PrefixNode pn && pn.getPriority() == PREFIX_PRIORITY)
            {
                toRemove.add(n);
            }
        }
        for (Node n : toRemove)
        {
            user.data().remove(n);
        }
    }

    /** Applies a rainbow (nyan) sequence to each visible character. */
    private static String nyanify(String text)
    {
        ChatColor[] colors = new ChatColor[]{
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE
        };
        StringBuilder out = new StringBuilder();
        int idx = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (Character.isWhitespace(c))
            {
                out.append(c);
                continue;
            }
            ChatColor cc = colors[idx % colors.length];
            idx++;
            out.append(cc).append(c);
        }
        return out.toString();
    }

    /** EXACTLY one space after the tag, then reset. Removes trailing whitespace and dangling color codes. */
    private static String ensureSingleSpaceAfterTag(String s)
    {
        if (s == null) s = "";
        // Remove trailing whitespace
        s = s.replaceAll("\\s+$", "");
        // Remove dangling legacy/section color/format codes (&x / §x)
        s = s.replaceAll("(?i)(?:&[0-9A-FK-OR]|§[0-9A-FK-OR])+$", "");
        // Remove dangling §x hex sequences
        s = s.replaceAll("(?i)(?:§x(§[0-9A-F]){6})+$", "");
        // Remove dangling &#RRGGBB sequences
        s = s.replaceAll("(?i)(?:&#[0-9A-F]{6})+$", "");
        return s + " " + ChatColor.RESET;
    }

    /** Strip legacy (&/§) and hex sequences to get visible characters only. */
    private static String stripAllColors(String s)
    {
        if (s == null) return "";
        String out = s;
        // Legacy (&a, &l, etc.)
        out = out.replaceAll("(?i)&[0-9A-FK-OR]", "");
        // Section-sign (§a, §l, etc.)
        out = out.replaceAll("(?i)§[0-9A-FK-OR]", "");
        // §x hex sequences (§x§R§R§G§G§B§B)
        out = out.replaceAll("(?i)§x(§[0-9A-F]){6}", "");
        // Common alt hex forms like &#RRGGBB
        out = out.replaceAll("(?i)&#[0-9A-F]{6}", "");
        return out;
    }

    private static String joinWithSpaces(String[] parts)
    {
        if (parts == null || parts.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    // ===== Preview helpers (Essentials nickname support) =====

    private String getEssentialsNickOrName(Player p)
    {
        try
        {
            org.bukkit.plugin.Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess != null && ess.isEnabled())
            {
                java.lang.reflect.Method getUser = ess.getClass().getMethod("getUser", Player.class);
                Object user = getUser.invoke(ess, p);
                if (user != null)
                {
                    java.lang.reflect.Method getNickname = user.getClass().getMethod("getNickname");
                    Object nickObj = getNickname.invoke(user);
                    if (nickObj instanceof String nick && !nick.isEmpty())
                    {
                        String colored = ChatColor.translateAlternateColorCodes('&', nick);
                        return stripLeadingSpaces(colored); // avoid doubled gap in preview
                    }
                }
            }
        }
        catch (Throwable ignored) {}
        return p.getName();
    }

    private static String stripLeadingSpaces(String s)
    {
        if (s == null) return "";
        return s.replaceFirst("^\\s+", "");
    }

    // ===== Blacklist support (reuse TagCommand's behavior) =====

    /** Returns the first matched blacklist term in the (visible) text, or null if allowed. */
    private String firstBlacklistedTerm(String rawInputWithPossibleCodes)
    {
        String plain = stripColorsAndFormatting(rawInputWithPossibleCodes).toLowerCase(Locale.ROOT);
        for (String term : getTagBlacklist())
        {
            if (term == null) continue;
            String t = term.trim();
            if (t.isEmpty()) continue;

            // regex: prefix enables regex terms
            if (t.regionMatches(true, 0, "regex:", 0, 6))
            {
                String pattern = t.substring(6).trim();
                try
                {
                    if (plain.matches("(?s).*" + pattern + ".*"))
                    {
                        return term;
                    }
                }
                catch (Exception ignored) { }
            }
            else
            {
                if (plain.contains(t.toLowerCase(Locale.ROOT)))
                {
                    return term;
                }
            }
        }
        return null;
    }

    private List<String> getTagBlacklist()
    {
        List<String> list = plugin.getConfig().getStringList("tags.blacklist");
        if (list == null || list.isEmpty())
        {
            list = plugin.getConfig().getStringList("tag.blacklist");
        }
        return (list != null) ? list : Collections.emptyList();
    }

    /** Remove legacy (&) and section (§) color codes and common hex color notations. */
    private static String stripColorsAndFormatting(String s)
    {
        if (s == null) return "";
        String out = s;
        // Remove Minecraft legacy codes: &a, &l, &r, etc.
        out = out.replaceAll("(?i)&[0-9A-FK-OR]", "");
        // Remove actual section-sign applied codes: §a, §l, §r, etc.
        out = out.replaceAll("(?i)§[0-9A-FK-OR]", "");
        // Remove §x hex sequences (§x§R§R§G§G§B§B)
        out = out.replaceAll("(?i)§x(§[0-9A-F]){6}", "");
        // Remove common alt hex forms like &#RRGGBB
        out = out.replaceAll("(?i)&#[0-9A-F]{6}", "");
        return out;
    }
}