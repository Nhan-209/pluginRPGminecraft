package com.rpgplugin.manager;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import com.rpgplugin.RPGPlugin;
import org.bukkit.Bukkit;
import java.text.DecimalFormat;

public class StackManager implements Listener {

    private final DecimalFormat format = new DecimalFormat("#.##");

    public StackManager() {
        // Removed heavy runTaskTimer scanning all entities
    }

    @EventHandler
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        updateItemName(event.getEntity());
    }

    private void updateItemName(Item itemEntity) {
        int amount = itemEntity.getItemStack().getAmount();
        if (amount > 1) {
            String name = itemEntity.getItemStack().hasItemMeta() && itemEntity.getItemStack().getItemMeta().hasDisplayName() ?
                          itemEntity.getItemStack().getItemMeta().getDisplayName() :
                          itemEntity.getItemStack().getType().name();
            itemEntity.setCustomName(name + " §ex" + formatNumber(amount));
            itemEntity.setCustomNameVisible(true);
        } else {
            itemEntity.setCustomNameVisible(false);
            itemEntity.setCustomName(null);
        }
    }

    @EventHandler
    public void onItemMerge(ItemMergeEvent event) {
        Item target = event.getTarget();
        Item entity = event.getEntity();
        
        long targetAmount = target.getItemStack().getAmount();
        long entityAmount = entity.getItemStack().getAmount();
        long newAmount = targetAmount + entityAmount;
        
        if (newAmount <= 99) {
            ItemStack newStack = target.getItemStack().clone();
            newStack.setAmount((int) newAmount);
            target.setItemStack(newStack);
            updateItemName(target);
            entity.remove();
            event.setCancelled(true);
        } else {
            // Fill target to 99, leave rest in entity
            ItemStack newStack = target.getItemStack().clone();
            newStack.setAmount(99);
            target.setItemStack(newStack);
            updateItemName(target);
            
            ItemStack entityStack = entity.getItemStack().clone();
            entityStack.setAmount((int) (newAmount - 99));
            entity.setItemStack(entityStack);
            updateItemName(entity);
            event.setCancelled(true);
        }
    }

    public ItemStack addStack(ItemStack item, long amountToAdd) {
        if (item == null) return null;

        long current = item.getAmount();
        long newAmount = current + amountToAdd;

        if (newAmount <= 0) return null;
        if (newAmount > 99) newAmount = 99;

        item.setAmount((int) newAmount);
        return item;
    }

    public long getStackAmount(ItemStack item) {
        if (item == null) return 0;
        return item.getAmount();
    }

    public String formatNumber(long number) {
        return String.valueOf(number);
    }
}
