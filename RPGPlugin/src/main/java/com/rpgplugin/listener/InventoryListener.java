package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.gui.StatsGUI;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.manager.StackManager;
import com.rpgplugin.util.Compat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class InventoryListener implements Listener {

    private final ItemManager itemManager;
    private final StackManager stackManager;

    public InventoryListener(ItemManager itemManager, StackManager stackManager) {
        this.itemManager = itemManager;
        this.stackManager = stackManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        fixOversizedStacks(player.getInventory());
        autoGroupInventory(player);

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null) {
                ItemStack rolled = itemManager.rollItemQuality(item);
                if (!rolled.equals(item)) {
                    player.getInventory().setItem(i, rolled);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack item = event.getItem().getItemStack();
            if (Compat.setMaxStackSizeSafe(item, 99)) {
                event.getItem().setItemStack(item);
            }

            RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
                if (!player.isOnline()) return;
                fixOversizedStacks(player.getInventory());
                autoGroupInventory(player);
            }, 1L);
        }
    }

    private void stripInfinite(ItemStack item) {
        if (item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(itemManager.INFINITE_KEY, PersistentDataType.BYTE)) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(itemManager.INFINITE_KEY);
            if (meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore != null) {
                    lore.remove("§b§l[VÔ HẠN]");
                    meta.setLore(lore);
                }
            }
            item.setItemMeta(meta);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped.getType().getMaxDurability() == 0 && dropped.getAmount() < 99 && itemManager.hasInfinite(dropped)) {
            stripInfinite(dropped);
            event.getItemDrop().setItemStack(dropped);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StatsGUI.StatsMenuHolder) return;
        RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
            int countSlots = event.getView().countSlots();
            for (int slot : event.getRawSlots()) {
                if (slot < 0 || slot >= countSlots) continue;
                ItemStack slotItem = event.getView().getItem(slot);
                if (slotItem != null && slotItem.getType().getMaxDurability() == 0 && slotItem.getAmount() < 99) {
                    stripInfinite(slotItem);
                }
            }
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof StatsGUI.StatsMenuHolder) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack currentItem = event.getCurrentItem();
        if (currentItem != null) {
            ItemStack rolled = itemManager.rollItemQuality(currentItem);
            if (!rolled.equals(currentItem)) {
                event.setCurrentItem(rolled);
            }
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.PICKUP_HALF) {
            RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor.getType().getMaxDurability() == 0 && cursor.getAmount() < 99) {
                    stripInfinite(cursor);
                }
            }, 1L);
        } else if (action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME) {
            RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
                ItemStack slotItem = event.getClickedInventory() != null ? event.getClickedInventory().getItem(event.getSlot()) : null;
                if (slotItem != null && slotItem.getType().getMaxDurability() == 0 && slotItem.getAmount() < 99) {
                    stripInfinite(slotItem);
                }
            }, 1L);
        } else if (action == InventoryAction.CLONE_STACK) {
            RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor.getType().getMaxDurability() == 0 && cursor.getAmount() < 99) {
                    stripInfinite(cursor);
                }
            }, 1L);
        }

        RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
            if (player.isOnline()) {
                fixOversizedStacks(player.getInventory());
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInfiniteBlockPlace(BlockPlaceEvent event) {
        ItemStack handItem = event.getItemInHand();
        if (handItem.getType().getMaxDurability() != 0 || !itemManager.hasInfinite(handItem)) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
            if (!player.isOnline()) return;
            ItemStack current = (hand == EquipmentSlot.OFF_HAND)
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            if (itemManager.hasInfinite(current) && current.getType().getMaxDurability() == 0 && current.getAmount() < 99) {
                Compat.setMaxStackSizeSafe(current, 99);
                current.setAmount(99);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInfiniteConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType().getMaxDurability() != 0 || !itemManager.hasInfinite(item)) return;

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
            if (!player.isOnline()) return;
            ItemStack current = (hand == EquipmentSlot.OFF_HAND)
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            if (itemManager.hasInfinite(current) && current.getType().getMaxDurability() == 0 && current.getAmount() < 99) {
                Compat.setMaxStackSizeSafe(current, 99);
                current.setAmount(99);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInfiniteProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
            if (!player.isOnline()) return;
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && invItem.getType().getMaxDurability() == 0
                        && itemManager.hasInfinite(invItem) && invItem.getAmount() < 99) {
                    Compat.setMaxStackSizeSafe(invItem, 99);
                    invItem.setAmount(99);
                }
            }
        }, 1L);
    }

    private void fixOversizedStacks(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Compat.setMaxStackSizeSafe(item, 99);
            int maxStack = item.getType().getMaxDurability() == 0 ? 99 : item.getMaxStackSize();
            if (item.getAmount() > maxStack) {
                int overflow = item.getAmount() - maxStack;
                item.setAmount(maxStack);

                ItemStack extra = item.clone();
                while (overflow > 0) {
                    int toGive = Math.min(overflow, maxStack);
                    extra.setAmount(toGive);
                    inv.addItem(extra.clone());
                    overflow -= toGive;
                }
            }
        }
    }

    private void autoGroupInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            Compat.setMaxStackSizeSafe(item, 99);

            if (item.getType().getMaxDurability() == 0 && itemManager.hasInfinite(item) && item.getAmount() < 99) {
                item.setAmount(99);
            }

            int maxStack = item.getMaxStackSize();
            if (maxStack > 1 && item.getAmount() < maxStack) {
                for (int j = i + 1; j < inv.getSize(); j++) {
                    ItemStack other = inv.getItem(j);
                    if (other != null && other.isSimilar(item)) {
                        int space = maxStack - item.getAmount();
                        if (space <= 0) break;

                        if (other.getAmount() <= space) {
                            item.setAmount(item.getAmount() + other.getAmount());
                            inv.setItem(j, null);
                        } else {
                            item.setAmount(maxStack);
                            other.setAmount(other.getAmount() - space);
                        }
                    }
                }
            }
        }
    }
}
