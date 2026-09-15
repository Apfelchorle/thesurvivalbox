package org.thesandbox.core.commands.utility;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;

import java.util.*;
import java.util.stream.Collectors;

public class CageCommand implements ISubCommand, Listener
{
    private final TheSandboxCore plugin;

    // Active cages: caged player's UUID -> cage data
    private final Map<UUID, CageData> cages = new HashMap<>();

    public CageCommand(TheSandboxCore plugin)
    {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ============================== Command ============================== */

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args)
    {
        if (!sender.hasPermission("sandbox.staff"))
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cNo permission."));
            return true;
        }
        if (args.length < 1)
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lUsage &8» &c/" + label + " <player> [skull <username> | block <MATERIAL>] | /" + label + " purge"));
            return true;
        }

        // /cage purge
        if ("purge".equalsIgnoreCase(args[0]))
        {
            if (cages.isEmpty())
            {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cNo players are currently caged."));
                return true;
            }
            // Uncage everyone safely
            for (UUID id : new HashSet<>(cages.keySet()))
            {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline())
                {
                    uncage(p);
                }
                else
                {
                    CageData d = cages.remove(id);
                    if (d != null) restoreAndRemove(d);
                }
            }
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lServer &8» &c" + sender.getName() + " has uncaged all players."));
            return true;
        }

        // Normal /cage <player> [skull <u> | block <mat>]
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null)
        {
            // partial match fallback
            List<Player> matches = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            if (matches.size() == 1) target = matches.get(0);
        }
        if (target == null)
        {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cPlayer not found."));
            return true;
        }

        UUID tid = target.getUniqueId();

        // Toggle OFF if already caged
        if (cages.containsKey(tid))
        {
            uncage(target);
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lServer &8» &c" + sender.getName() + " has uncaged " + target.getName() + "."));
            return true;
        }

        // Parse mode
        CageMode mode = CageMode.GLASS_DEFAULT;
        Material blockMat = Material.GLASS;
        String skullUser = null;

        if (args.length >= 2)
        {
            if (args[1].equalsIgnoreCase("skull"))
            {
                if (args.length < 3)
                {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c&lUsage &8» &c/" + label + " <target> skull <username>"));
                    return true;
                }
                mode = CageMode.SKULL;
                skullUser = args[2]; // keep EXACT user-typed casing for broadcast
            }
            else if (args[1].equalsIgnoreCase("block"))
            {
                if (args.length < 3)
                {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c&lUsage &8» &c/" + label + " <target> block <material>"));
                    return true;
                }
                Material m = resolveBlockMaterial(args[2]);
                if (m == null)
                {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&c&lError &8» &cUnknown block: " + args[2]));
                    return true;
                }
                mode = CageMode.BLOCK;
                blockMat = m; // stays ALL CAPS via Material.name()
            }
        }

        CageData data = buildCage(target, mode, blockMat, skullUser);

        if (data == null) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
            "&c&lError &8» &cFailed to place cage (world or block issue)."));
        return true;
        }

        cages.put(tid, data);

        // Broadcast shows skull username exactly as typed; blocks stay ALL CAPS (Material.name()).
        String msg = "&c&lServer &8» &c" + sender.getName() + " has caged " + target.getName() + broadcastSuffix(mode, blockMat, skullUser) + ".";
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        if (args.length == 1)
        {
            String prefix = args[0].toLowerCase();
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
            if ("purge".startsWith(prefix))
            {
                names.add("purge");
            }
            return names;
        }
        if (args.length == 2)
        {
            return Arrays.asList("skull", "block").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("block"))
        {
            String p = args[2].toUpperCase(Locale.ROOT);
            return Arrays.stream(Material.values())
                    .filter(Material::isBlock)
                    .map(Enum::name)
                    .filter(n -> n.startsWith(p))
                    .limit(20)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /* ============================== Events ============================== */

    // Keep the player inside the cage without recursion
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event)
    {
        CageData d = cages.get(event.getPlayer().getUniqueId());
        if (d == null) return;

        // Ignore rotations to reduce churn
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && Objects.equals(event.getFrom().getWorld(), event.getTo().getWorld()))
        {
            return;
        }

        if (!isInside(event.getTo(), d))
        {
            event.setTo(d.center.clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event)
    {
        CageData d = cages.get(event.getPlayer().getUniqueId());
        if (d == null) return;

        if (!isInside(event.getTo(), d))
        {
            event.setTo(d.center.clone()); // prevents recursion
        }
    }

    // Make ALL cage blocks unbreakable while caged (for everyone).
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        BlockPos pos = BlockPos.of(block);

        for (CageData data : cages.values())
        {
            if (!data.worldId.equals(block.getWorld().getUID())) continue;
            if (!data.blockSet.contains(pos)) continue;

            event.setCancelled(true);
            String message = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cThat block is part of an active cage.");

            event.getPlayer().sendMessage(message);
            return;
        }
    }

    // Prevent the caged player from placing blocks anywhere inside their cage
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        UUID placerId = event.getPlayer().getUniqueId();
        CageData d = cages.get(placerId);
        if (d == null) return;

        // Only block if the placement is within the cage bounds (and same world)
        if (event.getBlockPlaced().getWorld().getUID().equals(d.worldId)
                && isInside(event.getBlockPlaced().getLocation(), d))
        {
            event.setCancelled(true);
            String message = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                "&c&lError &8» &cYou cannot place blocks inside your cage.");

            event.getPlayer().sendMessage(message);
        }
    }

    /* ============================== Core ============================== */

    private enum CageMode { GLASS_DEFAULT, BLOCK, SKULL }

    // Broadcast suffix:
    //  - SKULL: use the EXACT casing the initiator typed (e.g., NoTcH -> " in NoTcH")
    //  - BLOCK: keep ALL CAPS via Material.name()
    private String broadcastSuffix(CageMode mode, Material mat, String skullUser)
    {
        switch (mode)
        {
            case SKULL: return " in " + (skullUser == null ? "UNKNOWN" : skullUser);
            case BLOCK: return " in " + mat.name();
            default: return "";
        }
    }

    // Accept loose tokens and coerce to plausible blocks (e.g., "diamond" -> DIAMOND_BLOCK)
    private Material resolveBlockMaterial(String token)
    {
        String up = token.toUpperCase(Locale.ROOT);

        Material m = Material.matchMaterial(up, true);
        if (m != null && m.isBlock()) return m;

        List<String> suffixes = Arrays.asList("_BLOCK", "_WOOL", "_CONCRETE", "_GLASS", "_PLANKS", "_TERRACOTTA");
        for (String suf : suffixes)
        {
            m = Material.matchMaterial(up.endsWith(suf) ? up : up + suf, true);
            if (m != null && m.isBlock()) return m;
        }

        m = Material.matchMaterial(up);
        if (m != null && m.isBlock()) return m;

        return Material.GLASS;
    }

    private boolean isInside(Location loc, CageData d)
    {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getUID().equals(d.worldId)) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= d.minX + 0.1 && x <= d.maxX + 0.9
            && y >= d.minY       && y <= d.maxY + 0.9
            && z >= d.minZ + 0.1 && z <= d.maxZ + 0.9;
    }

    private CageData buildCage(Player target, CageMode mode, Material blockMat, String skullUser)
    {
        World w = target.getWorld();

        // Force Survival while caged
        target.setGameMode(GameMode.SURVIVAL);

        // Floor one block below player feet
        Location base = target.getLocation().getBlock().getLocation();
        int minX = base.getBlockX() - 2;
        int minY = base.getBlockY() - 1; // important
        int minZ = base.getBlockZ() - 2;

        int maxX = minX + 4;
        int maxY = minY + 4;
        int maxZ = minZ + 4;

        List<OriginalBlock> originals = new ArrayList<>();
        Set<BlockPos> cageBlocks = new HashSet<>();

        // Build hollow 5x5x5 and place blocks
        for (int x = minX; x <= maxX; x++)
        {
            for (int y = minY; y <= maxY; y++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    boolean isWall = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    if (!isWall) continue;
                    if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;

                    Block b = w.getBlockAt(x, y, z);
                    originals.add(new OriginalBlock(b.getLocation(), b.getBlockData()));
                    cageBlocks.add(BlockPos.of(b));

                    if (mode == CageMode.SKULL)
                    {
                        b.setType(Material.PLAYER_HEAD, false);
                    }
                    else
                    {
                        b.setType(blockMat, false);
                    }
                }
            }
        }

        // Center player inside the cage
        Location center = new Location(w, (minX + maxX) / 2.0 + 0.5, minY + 1.0, (minZ + maxZ) / 2.0 + 0.5);
        target.teleport(center);

        CageData data = new CageData(
                originals, cageBlocks, w.getUID(),
                minX, minY, minZ, maxX, maxY, maxZ,
                center, mode, blockMat, skullUser
        );
        cages.put(target.getUniqueId(), data);

        // If skull mode, fetch textures via SkinsRestorer (offline/cracked friendly)
        if (mode == CageMode.SKULL && skullUser != null && !skullUser.isBlank())
        {
            fetchAndApplySkullProfileAsync(target.getUniqueId(), skullUser);
        }

        return data;
    }

    /* ============================== Skull profile via SkinsRestorer ============================== */

    private void fetchAndApplySkullProfileAsync(UUID cagedPlayerId, String username)
    {
        if (!Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer"))
        {
            plugin.getLogger().warning("[/cage skull] SkinsRestorer is not enabled; cannot texture skull cage.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try
            {
                SkinsRestorer sr = SkinsRestorerProvider.get();

                // Get the skin identifier SkinsRestorer would apply for this player/name
                Optional<SkinIdentifier> idOpt =
                        sr.getPlayerStorage().getSkinIdForPlayer(cagedPlayerId, username, false);
                if (idOpt.isEmpty()) return;

                Optional<SkinProperty> propOpt = sr.getSkinStorage().getSkinDataByIdentifier(idOpt.get());
                if (propOpt.isEmpty()) return;

                SkinProperty prop = propOpt.get();
                String value = prop.getValue();
                String signature = prop.getSignature();

                // Apply on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CageData d = cages.get(cagedPlayerId);
                    if (d == null) return;
                    World w = Bukkit.getWorld(d.worldId);
                    if (w == null) return;

                    PlayerProfile prof = Bukkit.createProfile(UUID.randomUUID(), username);
                    if (signature != null && !signature.isBlank())
                    {
                        prof.setProperty(new ProfileProperty("textures", value, signature));
                    }
                    else
                    {
                        prof.setProperty(new ProfileProperty("textures", value));
                    }
                    d.profile = prof;

                    for (int x = d.minX; x <= d.maxX; x++)
                    {
                        for (int y = d.minY; y <= d.maxY; y++)
                        {
                            for (int z = d.minZ; z <= d.maxZ; z++)
                            {
                                boolean isWall = (x == d.minX || x == d.maxX || y == d.minY || y == d.maxY || z == d.minZ || z == d.maxZ);
                                if (!isWall) continue;
                                Block b = w.getBlockAt(x, y, z);
                                if (b.getType() == Material.PLAYER_HEAD)
                                {
                                    BlockState state = b.getState();
                                    if (state instanceof Skull skull)
                                    {
                                        skull.setPlayerProfile(prof);
                                        skull.update(true, false);
                                    }
                                }
                            }
                        }
                    }
                });
            }
            catch (Throwable t)
            {
                plugin.getLogger().warning("[/cage skull] SR lookup failed for '" + username + "': " + t.getMessage());
            }
        });
    }

    /* ============================== Apply / Unapply ============================== */

    private void uncage(Player target)
    {
        CageData d = cages.remove(target.getUniqueId());
        if (d == null) return;
        restoreAndRemove(d);
    }

    private void restoreAndRemove(CageData d)
    {
        World w = Bukkit.getWorld(d.worldId);
        if (w == null) return;

        // Restore original terrain
        List<OriginalBlock> list = d.originals;
        Collections.reverse(list);
        for (OriginalBlock ob : list)
        {
            Block b = w.getBlockAt(ob.location);
            b.setBlockData(ob.blockData, false);
        }
    }

    /* ============================== Data & utils ============================== */

    private record OriginalBlock(Location location, BlockData blockData) {
            private OriginalBlock(Location location, BlockData blockData) {
                this.location = location.clone();
                this.blockData = blockData.clone();
            }
        }

    // Compact immutable block position key (world + xyz)
        private record BlockPos(UUID world, int x, int y, int z) {
        static BlockPos of(Block b) {
                return new BlockPos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
            }

        @Override
        public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof BlockPos(UUID world1, int x1, int y1, int z1))) return false;
                return x == x1 && y == y1 && z == z1 && Objects.equals(world, world1);
            }
    }

    private static class CageData
    {
        final List<OriginalBlock> originals;
        final Set<BlockPos> blockSet;
        final UUID worldId;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final Location center;

        // For rebuilds
        final CageMode mode;
        final Material blockMat;
        final String skullUser;
        PlayerProfile profile; // may be null until textures fetched

        CageData(List<OriginalBlock> originals, Set<BlockPos> blockSet, UUID worldId,
                 int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                 Location center, CageMode mode, Material blockMat, String skullUser)
        {
            this.originals = originals;
            this.blockSet = blockSet;
            this.worldId = worldId;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.center = center.clone();
            this.mode = mode;
            this.blockMat = blockMat;
            this.skullUser = skullUser;
        }
    }
}