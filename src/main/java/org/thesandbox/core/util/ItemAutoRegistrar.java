package org.thesandbox.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.thesandbox.core.fun.items.itemUTILS.Item;
import org.thesandbox.core.fun.items.itemUTILS.ItemListener;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;

// some functions were just copied from CommandAutoRegistrar.java

public final class ItemAutoRegistrar {

    private static final String ITEMS_PKG = "org.thesandbox.core.fun.items";

    private ItemAutoRegistrar() {}

    public static List<Item> registerAll(JavaPlugin plugin, Object... services) {
        Map<Class<?>, Object> injector = new HashMap<>();

        indexType(injector, plugin.getClass(), plugin);

        Server server = plugin.getServer();
        indexType(injector, Server.class, server);
        indexType(injector, PluginManager.class, server.getPluginManager());
        indexType(injector, Logger.class, plugin.getLogger());
        indexType(injector, BukkitScheduler.class, server.getScheduler());

        for (Object svc : services) {
            if (svc == null) continue;
            indexType(injector, svc.getClass(), svc);
        }

        List<Class<?>> itemClasses = findItemClasses(plugin);
        List<Item> constructed = new ArrayList<>();

        for (Class<?> clazz : itemClasses) {
            if (!Item.class.isAssignableFrom(clazz)) continue;
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) continue;

            Object instance = constructBest(plugin, clazz, injector);
            if (instance == null) {
                plugin.getLogger().warning("[ItemAutoRegistrar] Could not construct " + clazz.getName() + " (no suitable constructor).");
                continue;
            }


            constructed.add((Item) instance);
            indexType(injector, instance.getClass(), instance);
        }

        Bukkit.getPluginManager().registerEvents(new ItemListener(constructed), plugin);
        plugin.getLogger().info("[ItemAutoRegistrar] Registered " + constructed.size() + " item(s).");

        return constructed;
    }

    private static void indexType(Map<Class<?>, Object> map, Class<?> type, Object instance) {
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> stack = new ArrayDeque<>();
        stack.push(type);
        while (!stack.isEmpty()) {
            Class<?> t = stack.pop();
            if (t == null || !seen.add(t)) continue;
            map.putIfAbsent(t, instance);
            for (Class<?> i : t.getInterfaces()) stack.push(i);
            Class<?> sup = t.getSuperclass();
            if (sup != null) stack.push(sup);
        }
    }

    private static List<Class<?>> findItemClasses(JavaPlugin plugin) {
        List<Class<?>> list = new ArrayList<>();
        try {
            CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
            if (src == null) return list;
            String path = URLDecoder.decode(src.getLocation().getPath(), StandardCharsets.UTF_8);

            try (JarInputStream jis = new JarInputStream(new URL("file", null, path).openStream())) {
                JarEntry e;
                String pkgPath = ITEMS_PKG.replace('.', '/') + "/";
                while ((e = jis.getNextJarEntry()) != null) {
                    String name = e.getName();
                    if (!name.startsWith(pkgPath) || !name.endsWith(".class")) continue;
                    if (name.contains("$")) continue;
                    if (!name.endsWith("Item.class")) continue;

                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    try {
                        list.add(Class.forName(className, false, plugin.getClass().getClassLoader()));
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[ItemAutoRegistrar] Could not load " + className + ": " + t.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("[ItemAutoRegistrar] JAR scan error: " + ex.getMessage());
        }
        return list;
    }

    private static Object constructBest(JavaPlugin plugin, Class<?> clazz, Map<Class<?>, Object> injector) {
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        Arrays.sort(ctors, Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount()).reversed());
        for (Constructor<?> c : ctors) {
            Class<?>[] paramTypes = c.getParameterTypes();
            try {
                Object[] args = buildArgsFor(c.getParameterTypes(), injector);
                if (args == null) {
                    for (Class<?> pt : paramTypes) {
                        if (findAssignable(injector, pt) == null) {
                            plugin.getLogger().warning("[ItemAutoRegistrar] " + clazz.getSimpleName()
                                    + " ctor needs " + pt.getName() + " but no matching service was found in the injector.");
                        }
                    }
                    continue;
                }
                c.setAccessible(true);
                return c.newInstance(args);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("[ItemAutoRegistrar] " + clazz.getSimpleName()
                        + " ctor threw during construction: " + (e.getCause() != null ? e.getCause() : e));
            }
        }
        return null;
    }

    private static Object[] buildArgsFor(Class<?>[] types, Map<Class<?>, Object> injector) {
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Object match = findAssignable(injector, types[i]);
            if (match == null) return null;
            out[i] = match;
        }
        return out;
    }

    private static Object findAssignable(Map<Class<?>, Object> injector, Class<?> want) {
        Object exact = injector.get(want);
        if (exact != null) return exact;
        for (Map.Entry<Class<?>, Object> e : injector.entrySet()) {
            if (want.isAssignableFrom(e.getKey())) return e.getValue();
        }
        return null;
    }
}