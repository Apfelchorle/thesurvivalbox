package org.thesandbox.core.managers;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandBlockManager {

    private final ConcurrentHashMap<UUID, Long> cmdBlockedUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cmdBlockedByName = new ConcurrentHashMap<>();
    private volatile boolean cmdBlockAll = false;

    public boolean isCmdBlockAll() {
        return cmdBlockAll;
    }

    public void enableCmdBlockAll() {
        cmdBlockAll = true;
    }

    public void disableCmdBlockAll() {
        cmdBlockAll = false;
    }

    public void purgeAllCmdBlocks() {
        disableCmdBlockAll();
        cmdBlockedUntil.clear();
        cmdBlockedByName.clear();
    }

    public void blockPlayerCommands(UUID uuid, long untilMs) {
        if (uuid != null) {
            cmdBlockedUntil.put(uuid, untilMs);
        }
    }

    public void blockPlayerCommandsByName(String name, long untilMs) {
        if (name != null) {
            cmdBlockedByName.put(name.toLowerCase(Locale.ENGLISH), untilMs);
        }
    }

    public void unblockPlayerCommands(UUID uuid) {
        if (uuid != null) {
            cmdBlockedUntil.remove(uuid);
        }
    }

    public void unblockPlayerCommandsByName(String name) {
        if (name != null) {
            cmdBlockedByName.remove(name.toLowerCase(Locale.ENGLISH));
        }
    }

    public boolean isPlayerCmdBlockedByName(String name) {
        if (name == null) return false;
        Long until = cmdBlockedByName.get(name.toLowerCase(Locale.ENGLISH));
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            cmdBlockedByName.remove(name.toLowerCase(Locale.ENGLISH));
            return false;
        }
        return true;
    }

    /**
     * True if this player's commands should be blocked right now.
     */
    public boolean isCommandsBlocked(Player p) {
        // Moderators+ are NEVER blocked (per requirement)
        if (p.hasPermission("sandbox.staff")) return false;

        long now = System.currentTimeMillis();

        Long until = cmdBlockedUntil.get(p.getUniqueId());
        if (until != null) {
            if (now > until) cmdBlockedUntil.remove(p.getUniqueId());
            else return true;
        }

        Long byName = cmdBlockedByName.get(p.getName().toLowerCase(Locale.ENGLISH));
        if (byName != null) {
            if (now > byName) cmdBlockedByName.remove(p.getName().toLowerCase(Locale.ENGLISH));
            else return true;
        }

        return cmdBlockAll;
    }
}