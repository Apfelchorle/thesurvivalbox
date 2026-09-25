package org.thesandbox.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.thesandbox.core.TheSandboxCore;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataManager {

    private final TheSandboxCore plugin;
    private final File playerFolder;

    public DataManager(TheSandboxCore plugin) {
        this.plugin = plugin;
        this.playerFolder = new File(plugin.getDataFolder(), "player_data");
        if (!playerFolder.exists()) {
            playerFolder.mkdirs();
        }
    }

    private File fileFor(UUID uuid) {
        return new File(playerFolder, uuid.toString() + ".yml");
    }

    public <T> T loadData(UUID uuid, String key, T defaultValue) {
        File file = fileFor(uuid);
        if (!file.exists()) return defaultValue;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Object raw = config.get(key, defaultValue);

        try {
            @SuppressWarnings("unchecked")
            T cast = (T) raw;
            return cast;
        } catch (ClassCastException e) {
            plugin.getLogger().warning("Data type mismatch for " + uuid + " key '" + key + "', returning default.");
            return defaultValue;
        }
    }

    public void saveData(UUID uuid, String key, Object value) {
        File file = fileFor(uuid);
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set(key, value);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data for " + uuid);
        }
    }
}