package org.thesandbox.core.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class PlayerDataListener implements Listener {

    private final DataManager dataManager;
    private final Map<UUID, Map<String, Object>> playerCache = new HashMap<>();
    public PlayerDataListener(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        // Coins Balance
        DEFAULTS.put(PlayerDataKeys.COINS, 0);

        // JumpPad State
        DEFAULTS.put(PlayerDataKeys.JUMPPADS_MODE, "disabled");

        // item ownership
        DEFAULTS.put(PlayerDataKeys.LIGHTNING_ROD, "not_owned");
        DEFAULTS.put(PlayerDataKeys.LOGIN_MESSAGE, "");
        DEFAULTS.put(PlayerDataKeys.LOGIN_MESSAGES_STATE, "not_owned");
        DEFAULTS.put(PlayerDataKeys.CLOWN_FISH, "not_owned");
        DEFAULTS.put(PlayerDataKeys.RIDEABLE_ENDER_PEARL, "not_owned");
        DEFAULTS.put(PlayerDataKeys.GRAPPLING_HOOK, "not_owned");
        DEFAULTS.put(PlayerDataKeys.Stacking_Potato, "not_owned");
        DEFAULTS.put(PlayerDataKeys.WIND_ROD, "not_owned");
        DEFAULTS.put(PlayerDataKeys.FLOAT_BOAT, "not_owned");

        // marriage
        DEFAULTS.put(PlayerDataKeys.MARRIAGE_SPOUSE, "");
        DEFAULTS.put(PlayerDataKeys.MARRIAGE_STATUS, "Single");
        DEFAULTS.put(PlayerDataKeys.GENDER, "male");

        // staff
        DEFAULTS.put(PlayerDataKeys.VANISHED, false);
        DEFAULTS.put(PlayerDataKeys.CHATFILTER, true);
    }

    private Map<String, Object> loadFromDisk(UUID uuid) {
        Map<String, Object> data = new HashMap<>();
        DEFAULTS.forEach((key, defaultValue) -> data.put(key, dataManager.loadData(uuid, key, defaultValue)));
        return data;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerCache.put(uuid, loadFromDisk(uuid));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Map<String, Object> data = playerCache.remove(uuid);
        if (data == null) return;

        data.forEach((key, value) -> dataManager.saveData(uuid, key, value));
    }

    public void loadAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!playerCache.containsKey(uuid)) {
                playerCache.put(uuid, loadFromDisk(uuid));
            }
        }
    }

    public void saveAll() {
        for (UUID uuid : new HashSet<>(playerCache.keySet())) {
            Map<String, Object> data = playerCache.get(uuid);
            if (data != null) {
                data.forEach((key, value) -> dataManager.saveData(uuid, key, value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(UUID uuid, String key, T defaultValue) {
        Map<String, Object> data = playerCache.get(uuid);
        if (data == null || !data.containsKey(key)) return defaultValue;
        try {
            return (T) data.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public void set(UUID uuid, String key, Object value) {
        playerCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(key, value);
    }

    // shortcuts for repetitive tasks:

    public String getMarriageStatus(UUID uuid) {
        return get(uuid, PlayerDataKeys.MARRIAGE_STATUS, "Single");
    }

    public String getMarriageSpouse(UUID uuid) {
        return get(uuid, PlayerDataKeys.MARRIAGE_SPOUSE, "");
    }

    public void Marry(Player Issuer, Player reciever) {
        set(Issuer.getUniqueId(), PlayerDataKeys.MARRIAGE_SPOUSE, reciever.getName());
        set(reciever.getUniqueId(), PlayerDataKeys.MARRIAGE_SPOUSE, Issuer.getName());
        set(reciever.getUniqueId(), PlayerDataKeys.MARRIAGE_STATUS, "Married");
        set(Issuer.getUniqueId(), PlayerDataKeys.MARRIAGE_STATUS, "Married");
    }

    public void Divorce(Player Issuer, Player reciever) {
        set(Issuer.getUniqueId(), PlayerDataKeys.MARRIAGE_SPOUSE, "");
        set(reciever.getUniqueId(), PlayerDataKeys.MARRIAGE_SPOUSE, "");
        set(reciever.getUniqueId(), PlayerDataKeys.MARRIAGE_STATUS, "Divorced");
        set(Issuer.getUniqueId(), PlayerDataKeys.MARRIAGE_STATUS, "Divorced");
    }

    public void OfflineDivorce(Player Issuer, OfflinePlayer reciever) {
        set(Issuer.getUniqueId(), PlayerDataKeys.MARRIAGE_SPOUSE, "");
        set(reciever.getUniqueId(), PlayerDataKeys.MARRIAGE_SPOUSE, "");
        set(reciever.getUniqueId(), PlayerDataKeys.MARRIAGE_STATUS, "Divorced");
        set(Issuer.getUniqueId(), PlayerDataKeys.MARRIAGE_STATUS, "Divorced");
    }

    public String getGender(UUID uuid) {
        return get(uuid, PlayerDataKeys.GENDER, "male");
    }

    public void setGender(UUID uuid, String gender) {
        set(uuid, PlayerDataKeys.GENDER, gender);
    }

    public int getCoins(UUID uuid) {
        return get(uuid, PlayerDataKeys.COINS, 0);
    }

    public void addCoins(UUID uuid, int amount) {
        set(uuid, PlayerDataKeys.COINS, getCoins(uuid) + amount);
    }

    public void removeCoins(UUID uuid, int amount) {
        set(uuid, PlayerDataKeys.COINS, getCoins(uuid) - amount);
    }

    public void setCoins(UUID uuid, int amount) {
        set(uuid, PlayerDataKeys.COINS, amount);
    }


}