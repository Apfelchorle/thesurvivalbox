package org.thesandbox.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.fun.Utils;

import java.io.File;
import java.io.IOException;

public class PluginConfigManager {
    private final TheSandboxCore plugin;

    public PluginConfigManager(TheSandboxCore plugin) {
        this.plugin = plugin;
    }

    public <T> T getOrCreate(String key, T defaultValue) {
        FileConfiguration config = plugin.getConfig();

        if (!config.isSet(key)) {
            config.set(key, defaultValue);
            plugin.saveConfig();
            return defaultValue;
        }

        Object raw = config.get(key);
        try {
            @SuppressWarnings("unchecked")
            T cast = (T) raw;
            return cast;
        } catch (ClassCastException e) {
            plugin.getLogger().warning("Config type mismatch for key '" + key + "', returning default.");
            return defaultValue;
        }
    }

    public <T> T getOrCreateCustom(String key, T defaultValue, File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isSet(key)) {
            config.set(key, defaultValue);
            saveDataCustom(key, defaultValue, file);
            return defaultValue;
        }

        Object raw = config.get(key);
        try {
            @SuppressWarnings("unchecked")
            T cast = (T) raw;
            return cast;
        } catch (ClassCastException e) {
            Utils.dump(e, "Config type mismatch for key '" + key + "', returning default.");
            return defaultValue;
        }
    }

    public void saveDataCustom(String key, Object value, File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set(key, value);

        try {
            config.save(file);
        } catch (IOException e) {
            Utils.dump(e, "Could not save config to file");
        }
    }

    public <T> void safeSet(String key, T value) {
        FileConfiguration config = plugin.getConfig();
        Object existing = config.get(key);

        if (existing != null && value != null && !existing.getClass().isInstance(value)) {
            plugin.getLogger().warning("Refusing to overwrite key '" + key + "' — existing type " + existing.getClass().getSimpleName() + " doesn't match new type " + value.getClass().getSimpleName());
            return;
        }

        config.set(key, value);
        plugin.saveConfig();
    }

    public <T> void forceset(String key, T value) {
        plugin.getConfig().set(key, value);
        plugin.saveConfig();
    }
}