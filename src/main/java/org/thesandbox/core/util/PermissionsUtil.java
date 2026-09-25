package org.thesandbox.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Lightweight utility to fetch a player's primary group from LuckPerms or Vault (no hard deps).
 */
public class PermissionsUtil {

    /** Try LuckPerms first (preferred), then Vault Permission. Returns null if not found. */
    public static String getPrimaryGroup(Player p) {
        // LuckPerms
        try {
            Class<?> luckPermsCls = Class.forName("org.luckperms.api.LuckPerms");
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(luckPermsCls);
            if (reg != null) {
                Object lp = reg.getProvider();
                Object userManager = luckPermsCls.getMethod("getUserManager").invoke(lp);
                Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, p.getUniqueId());
                if (user != null) {
                    String pg = (String) user.getClass().getMethod("getPrimaryGroup").invoke(user);
                    if (pg != null && !pg.isEmpty()) return pg;
                }
            }
        } catch (Throwable ignored) {}

        // Vault Permission
        try {
            Class<?> permCls = Class.forName("net.milkbowl.vault.permission.Permission");
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(permCls);
            if (reg != null) {
                Object perm = reg.getProvider();
                String pg = (String) permCls.getMethod("getPrimaryGroup", Player.class).invoke(perm, p);
                if (pg != null && !pg.isEmpty()) return pg;
            }
        } catch (Throwable ignored) {}

        return null;
    }
}