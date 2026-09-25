package org.thesandbox.core.util;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;

public class EssentialsUtil {
    public static String getDisplayName(Player p) {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
            String displayname = LegacyComponentSerializer.legacyAmpersand().serialize(p.displayName());

            if (plugin == null) return displayname;
            Class<?> essentialsClass = plugin.getClass();
            Method getUser = essentialsClass.getMethod("getUser", Player.class);
            Object user = getUser.invoke(plugin, p);
            if (user == null) return displayname;
            Method getDisplayName = user.getClass().getMethod("getDisplayName");
            Object res = getDisplayName.invoke(user);
            if (res != null) return res.toString();
        } catch (Throwable ignored) {}
        return LegacyComponentSerializer.legacyAmpersand().serialize(p.displayName());
    }
}