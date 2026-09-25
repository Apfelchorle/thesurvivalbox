package org.thesandbox.core.services;

import org.bukkit.plugin.java.JavaPlugin;

/** Simple in-memory flag for global chat mute. */
public class ManageChatService {
    private final JavaPlugin plugin;
    private volatile boolean muted = false;

    public ManageChatService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMuted() {
        return muted;
    }

    public void mute() {
        muted = true;
    }

    public void unmute() {
        muted = false;
    }

    public boolean toggle() {
        return !muted;
    }
}