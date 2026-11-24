package com.rpgplugin.listener;

import com.rpgplugin.manager.ItemManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ListIterator;

public class ItemListener implements Listener {

    private final ItemManager itemManager;

    public ItemListener(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result != null) {
            ItemStack newResult = itemManager.rollItemQuality(result);
            event.setCurrentItem(newResult);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        ListIterator<ItemStack> iterator = event.getDrops().listIterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            iterator.set(itemManager.rollItemQuality(drop));
        }
    }
}
