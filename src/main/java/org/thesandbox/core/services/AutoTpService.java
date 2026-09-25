package org.thesandbox.core.services;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks players who should be randomly teleported on join.
 * Persists in config under "autotp".
 *
 * Supports one-shot consumption: consume(uuid) removes and returns true if present.
 */
public class AutoTpService {
    private final JavaPlugin plugin;
    private final Set<UUID> enabled = new HashSet<>();

    public AutoTpService(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public boolean isEnabled(UUID uuid) {
        return enabled.contains(uuid);
    }

    /** Toggle: returns true if now enabled, false if now disabled. */
    public boolean toggle(UUID uuid) {
        if (enabled.remove(uuid)) {
            save();
            return false;
        } else {
            enabled.add(uuid);
            save();
            return true;
        }
    }

    /** Enable; returns true if it wasn’t enabled before. */
    public boolean add(UUID uuid) {
        boolean changed = enabled.add(uuid);
        if (changed) save();
        return changed;
    }

    /** Disable; returns true if it was enabled before. */
    public boolean remove(UUID uuid) {
        boolean changed = enabled.remove(uuid);
        if (changed) save();
        return changed;
    }

    /** One-shot remove on join; returns true if it was present. */
    public boolean consume(UUID uuid) {
        boolean present = enabled.remove(uuid);
        if (present) save();
        return present;
    }

    private void load() {
        enabled.clear();
        FileConfiguration cfg = plugin.getConfig();
        for (String s : cfg.getStringList("autotp")) {
            try { enabled.add(UUID.fromString(s)); } catch (IllegalArgumentException ignore) {}
        }
    }

    private void save() {
        FileConfiguration cfg = plugin.getConfig();
        java.util.List<String> list = enabled.stream().map(UUID::toString).toList();
        cfg.set("autotp", list);
        plugin.saveConfig();
    }
}