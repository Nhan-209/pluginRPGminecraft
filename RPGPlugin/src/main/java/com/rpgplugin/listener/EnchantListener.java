package com.rpgplugin.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;

public class EnchantListener implements Listener {

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);

        if (first == null || second == null) return;
        if (first.getType() != second.getType() && second.getType() != Material.ENCHANTED_BOOK) return;

        ItemStack result = first.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        Map<Enchantment, Integer> firstEnchants = getEnchants(first);
        Map<Enchantment, Integer> secondEnchants = getEnchants(second);

        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int level2 = entry.getValue();
            int level1 = firstEnchants.getOrDefault(ench, 0);

            int finalLevel = level1;
            if (level1 == level2) {
                finalLevel = level1 + 1; // Uncapped combining
            } else if (level2 > level1) {
                finalLevel = level2;
            }

            // Cap % based enchants for players to avoid broken invincibility
            if (ench.equals(Enchantment.PROTECTION) || ench.equals(Enchantment.FIRE_PROTECTION)
                    || ench.equals(Enchantment.BLAST_PROTECTION) || ench.equals(Enchantment.PROJECTILE_PROTECTION)) {
                if (finalLevel > 20) finalLevel = 20;
            } else if (ench.equals(Enchantment.THORNS)) {
                if (finalLevel > 10) finalLevel = 10;
            } else if (ench.equals(Enchantment.FEATHER_FALLING)) {
                if (finalLevel > 10) finalLevel = 10;
            } else if (ench.equals(Enchantment.KNOCKBACK) || ench.equals(Enchantment.PUNCH)) {
                if (finalLevel > 10) finalLevel = 10;
            } else if (ench.equals(Enchantment.LOOTING) || ench.equals(Enchantment.FORTUNE)) {
                if (finalLevel > 20) finalLevel = 20;
            }

            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(ench, finalLevel, true);
            } else {
                meta.addEnchant(ench, finalLevel, true);
            }
        }

        AnvilView view = event.getView();
        String rename = view.getRenameText();
        if (rename != null && !rename.isBlank()) {
            meta.setDisplayName(rename);
        }

        result.setItemMeta(meta);
        event.setResult(result);

        view.setMaximumRepairCost(Integer.MAX_VALUE);
        view.setRepairCost(5);
    }

    private Map<Enchantment, Integer> getEnchants(ItemStack item) {
        if (item.getItemMeta() instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        }
        return item.getEnchantments();
    }
}
