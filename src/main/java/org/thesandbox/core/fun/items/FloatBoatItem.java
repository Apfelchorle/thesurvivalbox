package org.thesandbox.core.fun.items;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.fun.items.itemUTILS.Item;
import org.thesandbox.core.fun.items.itemUTILS.ItemKeys;
import org.thesandbox.core.util.PlayerDataKeys;
import org.thesandbox.core.util.PluginConfigManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FloatBoatItem extends PacketListenerAbstract implements Item, Listener {
    private static final String NAME = PlayerDataKeys.FLOAT_BOAT;
    public static final Set<UUID> ALLOWED_DISMOUNTS = ConcurrentHashMap.newKeySet();
    private static final double VERTICAL_SPEED = 0.8;

    private final TheSandboxCore plugin;
    private final ItemKeys keys;
    private final PluginConfigManager configManager;
    private final Map<UUID, BoatInputState> playerInputs = new ConcurrentHashMap<>();


//    public void onPacketReceive(PacketReceiveEvent event) {
//        plugin.getLogger().info("PACKET RECIEVED :" + event.getPacketType().getName());
//    }

    public FloatBoatItem(TheSandboxCore plugin, PluginConfigManager configManager, ItemKeys keys) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.configManager = configManager;
        this.keys = keys;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(this);

        //startFlightTask();
        simpleFlightTask();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_INPUT) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientPlayerInput input = new WrapperPlayClientPlayerInput(event);

        BoatInputState state = playerInputs.computeIfAbsent(player.getUniqueId(), k -> new BoatInputState());

        state.forward = input.isForward() ? 1f : (input.isBackward() ? -1f : 0f);
        state.sideways = input.isLeft() ? 1f : (input.isRight() ? -1f : 0f);
        state.jump = input.isJump();
        state.shift = input.isShift();
        state.yaw = player.getLocation().getYaw();
        state.pitch = player.getLocation().getPitch();
    }


    // fuck performance and optimization
    // this is all you're getting you greedy jews
    public void simpleFlightTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!(player.getVehicle() instanceof Boat boat) || !isFloatBoat(boat)) {
                    continue;
                }

                if (boat.getPassengers().getFirst() != player) continue;

                BoatInputState input = playerInputs.get(player.getUniqueId());
                if (input == null) continue;

                Vector velocity = boat.getVelocity();
                boolean modified = false;

                if (input.jump) {
                    velocity.setY(velocity.getY() + VERTICAL_SPEED);
                    modified = true;
                }
                if (input.shift) {
                    velocity.setY(velocity.getY() - VERTICAL_SPEED);
                    modified = true;
                }

                if (modified) {
                    boat.setVelocity(velocity);
                }

            }
        }, 0, 1L);
    }


    public void startFlightTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!(player.getVehicle() instanceof Boat boat) || !isFloatBoat(boat)) {
                    continue;
                }

                if (boat.getPassengers().getFirst() != player) continue;

                BoatInputState input = playerInputs.get(player.getUniqueId());
                if (input == null) continue;

                double yawRad = Math.toRadians(input.yaw);
                double pitchRad = Math.toRadians(input.pitch);

                Vector lookDir = new Vector(
                        -Math.sin(yawRad) * Math.cos(pitchRad),
                        -Math.sin(pitchRad),
                        Math.cos(yawRad) * Math.cos(pitchRad)
                ).normalize();

                Vector currentVel = boat.getVelocity();
                Vector targetVel = new Vector(0, 0, 0);
                double speed = 1.5;

                targetVel.setX(lookDir.getX() * input.forward * speed);
                targetVel.setZ(lookDir.getZ() * input.forward * speed);

// up and down
                double targetY;
                if (input.jump || input.pitch < -30.0f) {
                    targetY = (VERTICAL_SPEED);
                } else if (input.shift || input.pitch > 30.0f) {
                    targetY = (-VERTICAL_SPEED);
                } else {
                    targetY = (currentVel.getY() * 0.85);
                }

                Vector instantVel = new Vector(targetVel.getX(), targetY, targetVel.getZ());

                Vector newVel = currentVel.clone().multiply(0.3).add(targetVel.multiply(0.7));
                boolean check = input.jump || input.pitch < -30.0f || input.shift || input.pitch > 30.0f;

                if (check) {
                    newVel.setY((currentVel.getY() * 0.3) + (targetY * 0.7));
                } else {
                    newVel.setY(targetY);
                }

                boolean lerp = getVelocityType();

                if (lerp) {
                    boat.setVelocity(newVel);
                } else {
                    boat.setVelocity(instantVel);
                }
                boat.setFallDistance(0);
            }
        }, 1L, 1L);
    }


    @Override
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.OAK_BOAT);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(NAME, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("A boat that defies gravity.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keys.Float_Boat, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean matches(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.Float_Boat, PersistentDataType.BYTE);
    }

    @Override
    public void onInteract(PlayerInteractEvent e) {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPrice() {
        return configManager.getOrCreate("items." + NAME + ".price", 30);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoatPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Boat boat)) return;
        Player player = event.getPlayer();
        if (player == null) return;

        ItemStack used = player.getInventory().getItem(event.getHand());
        if (!matches(used)) return;

        applyNoGravity(boat);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Boat boat && isFloatBoat(boat)) {
                boat.setGravity(false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof Boat boat)) return;

        if (isFloatBoat(boat)) {
            if (ALLOWED_DISMOUNTS.remove(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ALLOWED_DISMOUNTS.remove(event.getPlayer().getUniqueId());
    }
    public void cleanup() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        ALLOWED_DISMOUNTS.clear();
    }

    private void applyNoGravity(Boat boat) {
        boat.setGravity(false);
        boat.getPersistentDataContainer().set(keys.Float_Boat, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isFloatBoat(Boat boat) {
        return boat != null && boat.getPersistentDataContainer().has(keys.Float_Boat, PersistentDataType.BYTE);
    }

    private boolean getVelocityType() {
        return configManager.getOrCreate("items." + NAME + ".lerp", false);
    }

    public boolean isFloatBoatPublic(Boat boat) {
        return isFloatBoat(boat);
    }
}

