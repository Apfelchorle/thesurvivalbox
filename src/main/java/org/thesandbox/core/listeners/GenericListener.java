package org.thesandbox.core.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.thesandbox.core.util.GenericDataKeys;
import org.thesandbox.core.util.PluginConfigManager;

public class GenericListener implements Listener {

    private final PluginConfigManager pluginConfigManager;

    public GenericListener(PluginConfigManager pluginConfigManager) {
        this.pluginConfigManager = pluginConfigManager;
    }

    @EventHandler
    public void BlockFromTo(BlockFromToEvent event) {
        Material material = event.getBlock().getType();
        boolean config = pluginConfigManager.getOrCreate(GenericDataKeys.WATER_FLOW, GenericDataKeys.WATER_FLOW_DEFAULT);
        boolean target = material == Material.WATER || material == Material.LAVA;


        if (target && config) {
            event.setCancelled(true);
        }
    }
}
