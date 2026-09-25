package org.thesandbox.core.util;

import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jspecify.annotations.NonNull;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.commands.meta.CommandAliases;
import org.thesandbox.core.commands.meta.CommandName;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;

public final class CommandAutoRegistrar {
    private static List<Class<?>> findCommandClasses(JavaPlugin plugin) {
        List<Class<?>> list = new ArrayList<>();
        try {
            CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
            if (src == null) return list;
            URL jarUrl = src.getLocation();
            URI jarUri = jarUrl.toURI();
            String path = URLDecoder.decode(jarUrl.getPath(), StandardCharsets.UTF_8);
            try (InputStream is = jarUrl.openStream(); JarInputStream jis = new JarInputStream(is)) {
                JarEntry e;
                String pkgPath = COMMANDS_PKG.replace('.', '/') + "/";
                while ((e = jis.getNextJarEntry()) != null) {
                    String name = e.getName();
                    if (!name.startsWith(pkgPath) || !name.endsWith(".class")) continue;
                    if (name.contains("$")) continue;
                    if (!name.endsWith("Command.class")) continue;

                    String className = name.substring(0, name.length() - 6).replace('/', '.'); // strip ".class"
                    try {
                        Class<?> c = Class.forName(className, false, plugin.getClass().getClassLoader());
                        list.add(c);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[CommandAutoRegistrar] Could not load " + className + ": " + t.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("[CommandAutoRegistrar] JAR scan error: " + ex.getMessage());
        }
        return list;
    }


    private static final String COMMANDS_PKG = "org.thesandbox.core.commands";

    private CommandAutoRegistrar() {}

    public static void registerAll(JavaPlugin plugin, Object... services) {
        // ---- Build injector -------------------------------------------------
        Map<Class<?>, Object> injector = new HashMap<>();

        // Index the plugin under ITS CONCRETE CLASS and all supertypes/interfaces
        indexType(injector, plugin.getClass(), plugin);

        // Common Bukkit objects (often used in constructors)
        Server server = plugin.getServer();
        PluginManager pm = server.getPluginManager();
        Logger logger = plugin.getLogger();
        BukkitScheduler scheduler = server.getScheduler();

        indexType(injector, Server.class, server);
        indexType(injector, PluginManager.class, pm);
        indexType(injector, Logger.class, logger);
        indexType(injector, BukkitScheduler.class, scheduler);

        // LuckPerms (optional)
        try {
            Class<?> lpCls = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> provCls = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object lp = provCls.getMethod("get").invoke(null);
            if (lpCls.isInstance(lp)) {
                indexType(injector, lpCls, lp);
            }
        } catch (Throwable ignored) {
            // LuckPerms not installed or not yet ready; ignore.
        }

        // Index provided services by their class + supertypes
        for (Object svc : services) {
            if (svc == null) continue;
            indexType(injector, svc.getClass(), svc);
        }

        // ---- Read plugin.yml keys/aliases ----------------------------------
        Map<String, List<String>> ymlCommands = readCommandKeysAndAliases(plugin);
        if (ymlCommands.isEmpty()) {
            plugin.getLogger().warning("No commands found in plugin.yml.");
        }

        // ---- Scan for *Command classes -------------------------------------
        List<Class<?>> commandClasses = findCommandClasses(plugin);

        int wired = 0;
        for (Class<?> clazz : commandClasses) {
            if (!ISubCommand.class.isAssignableFrom(clazz)) continue;
            if (clazz.isInterface()) continue;
            if (Modifier.isAbstract(clazz.getModifiers())) continue;

            List<String> claimed = resolveCommandNames(clazz);
            if (claimed.isEmpty()) continue;

            // Match classes to plugin.yml command keys or their aliases
            Map<String, List<String>> targets = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : ymlCommands.entrySet()) {
                String key = e.getKey();
                List<String> aliases = e.getValue();
                boolean match = false;
                for (String c : claimed) {
                    if (key.equalsIgnoreCase(c)) { match = true; break; }
                    for (String a : aliases) {
                        if (a.equalsIgnoreCase(c)) { match = true; break; }
                    }
                    if (match) break;
                }
                if (match) targets.put(key, new ArrayList<>(aliases));
            }
            if (targets.isEmpty()) continue;

            ISubCommand instance = (ISubCommand) constructBest(plugin, clazz, injector);
            if (instance == null) {
                plugin.getLogger().warning("[CommandAutoRegistrar] Could not construct " + clazz.getName() + " (no suitable constructor).");
                continue;
            }

            // Always return true so default usage isn’t auto-spammed
            CommandExecutor exec = (sender, cmd, label, args) -> {
                try {
                    instance.execute(sender, cmd, label, args);
                } catch (Throwable t) {
                    plugin.getLogger().severe("[CommandAutoRegistrar] Error running /" + label + ": " + t.getMessage());
                    sender.sendMessage("§cAn internal error occurred while executing /" + label + ".");
                }
                return true;
            };

            for (Map.Entry<String, List<String>> t : targets.entrySet()) {
                String base = t.getKey();
                List<String> aliases = t.getValue();

                PluginCommand pc = plugin.getCommand(base);
                if (pc != null) {
                    pc.setExecutor(exec);
                    TabCompleter tc = (instance instanceof TabCompleter) ? (TabCompleter) instance : new DelegatingTabCompleter(instance);
                    pc.setTabCompleter(tc);
                } else {
                    plugin.getLogger().warning("[CommandAutoRegistrar] " + base + " is in plugin.yml but plugin.getCommand returned null.");
                }
                for (String alias : aliases) {
                    PluginCommand ac = plugin.getCommand(alias);
                    if (ac != null) {
                        ac.setExecutor(exec);
                        TabCompleter tc2 = (instance instanceof TabCompleter) ? (TabCompleter) instance : new DelegatingTabCompleter(instance);
                        ac.setTabCompleter(tc2);
                    }
                }
                wired++;
            }
        }
        plugin.getLogger().info("[CommandAutoRegistrar] Finished. Registered " + wired + " command executor mapping(s).");
    }

    // ---- helpers ------------------------------------------------------------

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

    private static Map<String, List<String>> readCommandKeysAndAliases(JavaPlugin plugin) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        PluginDescriptionFile pdf = plugin.getDescription();
        Map<String, Map<String, Object>> cmds = pdf.getCommands();
        if (cmds == null) return out;
        for (Map.Entry<String, Map<String, Object>> e : cmds.entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            List<String> aliases = new ArrayList<>();
            Map<String, Object> def = e.getValue();
            if (def != null) {
                Object al = def.get("aliases");
                if (al instanceof String) {
                    aliases.add(((String) al).toLowerCase(Locale.ROOT));
                } else if (al instanceof List) {
                    for (Object o : (List<?>) al) {
                        if (o != null) aliases.add(o.toString().toLowerCase(Locale.ROOT));
                    }
                }
            }
            out.put(key, aliases);
        }
        return out;
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
                            plugin.getLogger().warning("[CommandAutoRegistrar] " + clazz.getSimpleName()
                                    + " ctor needs " + pt.getName() + " but no matching service was found in the injector.");
                        }
                    }
                    continue;
                }
                c.setAccessible(true);
                return c.newInstance(args);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("[CommandAutoRegistrar] " + clazz.getSimpleName()
                        + " ctor threw during construction: " + (e.getCause() != null ? e.getCause() : e));
            }
        }
        try {
            Constructor<?> noArg = clazz.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (Exception ignored) { }
        return null;
    }

    private static List<String> resolveCommandNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        CommandName explicit = clazz.getAnnotation(CommandName.class);
        if (explicit != null && !explicit.value().isEmpty()) {
            names.add(explicit.value().toLowerCase(Locale.ROOT));
        } else {
            String simple = clazz.getSimpleName();
            if (simple.endsWith("Command")) {
                simple = simple.substring(0, simple.length() - "Command".length());
            }
            names.add(simple.toLowerCase(Locale.ROOT));
        }
        CommandAliases aliases = clazz.getAnnotation(CommandAliases.class);
        if (aliases != null) {
            for (String a : aliases.value()) {
                if (a != null && !a.isEmpty()) names.add(a.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    private static Object findAssignable(Map<Class<?>, Object> injector, Class<?> want) {
        // exact
        Object exact = injector.get(want);
        if (exact != null) return exact;
        // any assignable entry
        for (Map.Entry<Class<?>, Object> e : injector.entrySet()) {
            Class<?> haveType = e.getKey();
            Object val = e.getValue();
            if (want.isAssignableFrom(haveType)) return val;
            if (want.isInstance(val)) return val;
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

    // Delegates Bukkit tab completion to ISubCommand#tabComplete for classes that don't implement TabCompleter.
    private record DelegatingTabCompleter(ISubCommand sub) implements TabCompleter {
        @Override
        public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {
            try {
                List<String> out = sub.tabComplete(sender, command, alias, args);
                return (out != null) ? out : Collections.emptyList();
            } catch (Throwable t) {
                return Collections.emptyList();
            }
        }
    }
}