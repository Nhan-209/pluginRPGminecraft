package com.rpgplugin.listener;

import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.util.Compat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class VillagerListener implements Listener {

    private final ItemManager itemManager;

    public VillagerListener(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        AbstractVillager abstractVillager = event.getEntity();
        MerchantRecipe recipe = event.getRecipe();
        ItemStack result = recipe.getResult();

        boolean isBook = result.getType() == Material.ENCHANTED_BOOK;
        boolean isGear = itemManager.isGear(result);
        if (!isBook && !isGear) return;

        int villagerLevel = Compat.parseEntityLevel(abstractVillager);
        if (abstractVillager instanceof Villager v) {
            villagerLevel = Math.max(villagerLevel, v.getVillagerLevel() * 5);
        }

        ItemStack upgradedResult = isGear ? itemManager.rollItemQuality(result) : result.clone();
        scaleEnchantments(upgradedResult, villagerLevel);

        MerchantRecipe newRecipe = new MerchantRecipe(
                upgradedResult,
                recipe.getUses(),
                recipe.getMaxUses(),
                recipe.hasExperienceReward(),
                recipe.getVillagerExperience(),
                recipe.getPriceMultiplier()
        );
        newRecipe.setIngredients(recipe.getIngredients());
        event.setRecipe(newRecipe);
    }

    private void scaleEnchantments(ItemStack item, int villagerLevel) {
        if (villagerLevel <= 3) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int maxScale = Math.max(2, 1 + (villagerLevel / 3));
        int minScale = Math.max(1, maxScale / 2);

        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(storageMeta.getStoredEnchants()).entrySet()) {
                Enchantment ench = entry.getKey();
                int scaled = rollScaledLevel(ench, entry.getValue(), minScale, maxScale);
                storageMeta.addStoredEnchant(ench, scaled, true);
            }
        } else {
            for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(meta.getEnchants()).entrySet()) {
                Enchantment ench = entry.getKey();
                int scaled = rollScaledLevel(ench, entry.getValue(), minScale, maxScale);
                meta.addEnchant(ench, scaled, true);
            }
        }

        item.setItemMeta(meta);
    }

    private int rollScaledLevel(Enchantment ench, int currentLevel, int minScale, int maxScale) {
        int rolled = minScale + (int) (Math.random() * (maxScale - minScale + 1));
        int target = Math.max(currentLevel, rolled);

        if (ench.equals(Enchantment.PROTECTION) || ench.equals(Enchantment.FIRE_PROTECTION)
                || ench.equals(Enchantment.BLAST_PROTECTION) || ench.equals(Enchantment.PROJECTILE_PROTECTION)) {
            return Math.min(20, target);
        }
        if (ench.equals(Enchantment.THORNS) || ench.equals(Enchantment.FEATHER_FALLING)
                || ench.equals(Enchantment.KNOCKBACK) || ench.equals(Enchantment.PUNCH)) {
            return Math.min(10, target);
        }
        if (ench.equals(Enchantment.LOOTING) || ench.equals(Enchantment.FORTUNE)) {
            return Math.min(20, target);
        }
        return target;
    }
}
