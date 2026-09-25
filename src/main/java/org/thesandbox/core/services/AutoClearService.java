package org.thesandbox.core.services;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClearService
{
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public boolean add(UUID uuid) { return pending.add(uuid); }
    public boolean remove(UUID uuid) { return pending.remove(uuid); }
    public boolean contains(UUID uuid) { return pending.contains(uuid); }
}