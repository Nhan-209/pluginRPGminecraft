package com.rpgplugin.manager;

import com.google.common.collect.Multimap;
import com.rpgplugin.model.ItemQuality;
import com.rpgplugin.util.Compat;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ItemManager {

    private final JavaPlugin plugin;
    public final NamespacedKey QUALITY_KEY;
    public final NamespacedKey EFFECTS_KEY;
    public final NamespacedKey FLIGHT_KEY;
    public final NamespacedKey INFINITE_KEY;

    private final NamespacedKey bonusDamageModKey;
    private final NamespacedKey bonusArmorModKey;

    public ItemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.QUALITY_KEY = new NamespacedKey(plugin, "rpg_quality");
        this.EFFECTS_KEY = new NamespacedKey(plugin, "rpg_effects");
        this.FLIGHT_KEY = new NamespacedKey(plugin, "rpg_flight");
        this.INFINITE_KEY = new NamespacedKey(plugin, "rpg_infinite");
        this.bonusDamageModKey = new NamespacedKey(plugin, "rpg_bonus_damage");
        this.bonusArmorModKey = new NamespacedKey(plugin, "rpg_bonus_armor");
    }

    public boolean isGear(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.getType().isItem()) return false;
        String type = item.getType().name();
        ItemMeta meta = item.getItemMeta();
        String name = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName().toLowerCase() : "";

        return type.contains("SWORD") || type.contains("PICKAXE") || type.contains("AXE") ||
                type.contains("SHOVEL") || type.contains("HOE") || type.contains("HELMET") ||
                type.contains("CHESTPLATE") || type.contains("LEGGINGS") || type.contains("BOOTS") ||
                type.contains("BOW") || type.contains("CROSSBOW") || type.contains("SHIELD") ||
                type.contains("MACE") || type.contains("TRIDENT") || type.contains("SPEAR") ||
                type.equals("FISHING_ROD") || type.equals("ELYTRA") ||
                name.contains("spear") || name.contains("lance") || name.contains("dagger");
    }

    public boolean hasEffect(ItemStack item, String effectName) {
        if (item == null || !item.hasItemMeta()) return false;
        String effects = item.getItemMeta().getPersistentDataContainer().get(EFFECTS_KEY, PersistentDataType.STRING);
        if (effects == null || effects.isEmpty()) return false;
        for (String token : effects.split(",")) {
            if (token.trim().equalsIgnoreCase(effectName)) {
                return true;
            }
        }
        return false;
    }

    public int getQualityStars(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        Integer stars = item.getItemMeta().getPersistentDataContainer().get(QUALITY_KEY, PersistentDataType.INTEGER);
        return stars != null ? stars : -1;
    }

    public boolean hasFlight(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(FLIGHT_KEY, PersistentDataType.BYTE)) return true;
        if (hasEffect(item, "Flight")) return true;
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line.contains("BAY LƯỢN")) return true;
            }
        }
        return false;
    }

    public boolean hasInfinite(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(INFINITE_KEY, PersistentDataType.BYTE)) return true;
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line.contains("VÔ HẠN")) return true;
            }
        }
        return false;
    }

    public ItemStack rollItemQuality(ItemStack item) {
        if (item == null || !item.getType().isItem()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(QUALITY_KEY, PersistentDataType.INTEGER)) {
            return item;
        }

        if (!isGear(item)) {
            return item;
        }

        ItemStack result = item.clone();
        ItemQuality quality = ItemQuality.rollRandom();
        return applyQuality(result, quality);
    }

    public ItemStack applyQuality(ItemStack item, ItemQuality quality) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(QUALITY_KEY, PersistentDataType.INTEGER, quality.getStars());

        List<String> lore = new ArrayList<>();
        lore.add("§6★ Quality: " + quality.getStars() + " Star" + (quality.getStars() > 1 ? "s" : ""));
        lore.add("§7Stats Multiplier: " + (int) ((1.0 + quality.getStatMultiplier()) * 100) + "%");

        // Handle durability tier bonuses cleanly
        if (quality.getStars() >= 8) {
            meta.setUnbreakable(true);
            lore.add("§eUnbreakable");
        } else if (quality.getStars() == 7 && meta instanceof Damageable dmg && item.getType().getMaxDurability() > 0) {
            dmg.setMaxDamage(item.getType().getMaxDurability() * 4);
            lore.add("§aDurability: 4x");
        } else if (quality.getStars() == 6 && meta instanceof Damageable dmg && item.getType().getMaxDurability() > 0) {
            dmg.setMaxDamage(item.getType().getMaxDurability() * 2);
            lore.add("§aDurability: 2x");
        }

        String type = item.getType().name();
        String name = meta.hasDisplayName() ? meta.getDisplayName().toLowerCase() : "";
        boolean isWeapon = type.contains("SWORD")
                || (type.contains("AXE") && !type.contains("PICKAXE"))
                || type.contains("MACE")
                || type.contains("TRIDENT")
                || type.contains("SPEAR")
                || name.contains("spear")
                || name.contains("lance")
                || name.contains("dagger");
        boolean isArmor = type.contains("HELMET") || type.contains("CHESTPLATE") || type.contains("LEGGINGS") || type.contains("BOOTS");
        boolean isRanged = type.contains("BOW") || type.contains("CROSSBOW");

        if (quality.getEffectsCount() > 0) {
            lore.add("§dSpecial Effects: " + quality.getEffectsCount());
            String effectsData = generateRandomEffects(quality, type);
            pdc.set(EFFECTS_KEY, PersistentDataType.STRING, effectsData);
            for (String effect : effectsData.split(",")) {
                lore.add("§8- " + effect);
            }
        } else {
            pdc.remove(EFFECTS_KEY);
        }

        // Apply real attributes to make lore stats real
        if (isWeapon || isArmor || isRanged) {
            EquipmentSlot defaultSlot;
            EquipmentSlotGroup slotGroup;
            if (isArmor) {
                if (type.contains("HELMET")) {
                    defaultSlot = EquipmentSlot.HEAD;
                    slotGroup = EquipmentSlotGroup.HEAD;
                } else if (type.contains("CHESTPLATE")) {
                    defaultSlot = EquipmentSlot.CHEST;
                    slotGroup = EquipmentSlotGroup.CHEST;
                } else if (type.contains("LEGGINGS")) {
                    defaultSlot = EquipmentSlot.LEGS;
                    slotGroup = EquipmentSlotGroup.LEGS;
                } else {
                    defaultSlot = EquipmentSlot.FEET;
                    slotGroup = EquipmentSlotGroup.FEET;
                }
            } else {
                defaultSlot = EquipmentSlot.HAND;
                slotGroup = EquipmentSlotGroup.MAINHAND;
            }

            if (!meta.hasAttributeModifiers()) {
                Multimap<Attribute, AttributeModifier> defaultAttributes = item.getType().getDefaultAttributeModifiers(defaultSlot);
                for (Map.Entry<Attribute, AttributeModifier> entry : defaultAttributes.entries()) {
                    meta.addAttributeModifier(entry.getKey(), entry.getValue());
                }
            }

            if (isWeapon && Compat.ATTACK_DAMAGE != null) {
                double baseDmg = type.contains("DIAMOND") || type.contains("NETHERITE") || type.contains("MACE") ? 8.0 : 6.0;
                double bonusDmg = baseDmg * quality.getStatMultiplier();
                if (bonusDmg > 0) {
                    AttributeModifier modifier = new AttributeModifier(
                            bonusDamageModKey, bonusDmg, AttributeModifier.Operation.ADD_NUMBER, slotGroup);
                    meta.addAttributeModifier(Compat.ATTACK_DAMAGE, modifier);
                    lore.add("§c+ " + formatNumber(bonusDmg) + " Bonus Damage");
                } else if (bonusDmg < 0) {
                    AttributeModifier modifier = new AttributeModifier(
                            bonusDamageModKey, bonusDmg, AttributeModifier.Operation.ADD_NUMBER, slotGroup);
                    meta.addAttributeModifier(Compat.ATTACK_DAMAGE, modifier);
                    lore.add("§c" + formatNumber(bonusDmg) + " Damage Penalty");
                }
            } else if (isArmor && Compat.ARMOR != null) {
                double baseArmor = type.contains("CHESTPLATE") ? 6.0 : 3.0;
                double bonusArmor = baseArmor * quality.getStatMultiplier();
                if (bonusArmor > 0) {
                    AttributeModifier modifier = new AttributeModifier(
                            bonusArmorModKey, bonusArmor, AttributeModifier.Operation.ADD_NUMBER, slotGroup);
                    meta.addAttributeModifier(Compat.ARMOR, modifier);
                    lore.add("§9+ " + formatNumber(bonusArmor) + " Bonus Armor");
                } else if (bonusArmor < 0) {
                    AttributeModifier modifier = new AttributeModifier(
                            bonusArmorModKey, bonusArmor, AttributeModifier.Operation.ADD_NUMBER, slotGroup);
                    meta.addAttributeModifier(Compat.ARMOR, modifier);
                    lore.add("§c" + formatNumber(bonusArmor) + " Armor Penalty");
                }
            } else if (isRanged && quality.getStatMultiplier() != 0.0) {
                int pct = (int) (quality.getStatMultiplier() * 100);
                lore.add((pct > 0 ? "§c+ " : "§c") + pct + "% Bow/Crossbow Damage");
            }
        }

        // Tiny cosmic chance for Infinite attribute on gear
        if (!pdc.has(INFINITE_KEY, PersistentDataType.BYTE)) {
            if (Math.random() < 0.00000000001) {
                pdc.set(INFINITE_KEY, PersistentDataType.BYTE, (byte) 1);
                lore.add(0, "§b§l[VÔ HẠN]");
            }
        } else {
            lore.add(0, "§b§l[VÔ HẠN]");
        }

        if (pdc.has(FLIGHT_KEY, PersistentDataType.BYTE)) {
            lore.add(0, "§e§l[BAY LƯỢN]");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String formatNumber(double num) {
        return new DecimalFormat("#.##").format(num);
    }

    private String generateRandomEffects(ItemQuality quality, String typeName) {
        boolean isArmor = typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE")
                || typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS")
                || typeName.equals("ELYTRA");
        boolean isTool = typeName.endsWith("_PICKAXE") || typeName.endsWith("_SHOVEL")
                || typeName.endsWith("_HOE") || typeName.equals("FISHING_ROD");
        boolean isShield = typeName.equals("SHIELD");
        boolean isRanged = typeName.endsWith("_BOW") || typeName.endsWith("_CROSSBOW") || typeName.endsWith("_TRIDENT");

        String[] normalEffects;
        String[] advancedEffects;
        String[] mythicalEffects;

        if (isArmor) {
            normalEffects = new String[]{"Resilience", "FireImmunity", "Regeneration", "Aegis", "Fortitude", "Featherweight", "WaterBreathing", "JumpBoost"};
            advancedEffects = new String[]{"Thorns", "Dodge", "ExplosionShield", "Spikes", "Grounded"};
            mythicalEffects = new String[]{"LastStand", "Retribution", "Immortality", "Ghost", "Forcefield", "Phoenix"};
        } else if (isShield) {
            normalEffects = new String[]{"Resilience", "FireImmunity", "Regeneration"};
            advancedEffects = new String[]{"Thorns", "ExplosionShield", "Spikes"};
            mythicalEffects = new String[]{"WorldTreeBlessing", "LastStand", "Forcefield", "Phoenix"};
        } else if (isTool) {
            normalEffects = new String[]{"AutoSmelt", "ExpBoost", "NightVision", "Lightweight", "AutoReplant", "Magnetic"};
            advancedEffects = new String[]{"VeinMiner", "TreasureHunter", "Haste", "Timber", "Excavator"};
            mythicalEffects = new String[]{"SpawnerSilkTouch", "BlackHole", "MidasTouch", "TimeWarp", "StarCatcher"};
        } else if (isRanged) {
            normalEffects = new String[]{"Burn", "Slow", "Poison", "Freeze", "Blindness", "Wither"};
            advancedEffects = new String[]{"Lifesteal", "ArmorPenetration", "MaxHPPoison", "Vampire", "Bleed", "IceSpikes"};
            mythicalEffects = new String[]{"Lightning", "KnockbackBurst", "Execute", "SoulEater", "TimeStop", "MeteorStrike"};
        } else {
            normalEffects = new String[]{"Burn", "Slow", "Poison", "Freeze", "Blindness", "Wither", "Weakness", "Venom"};
            advancedEffects = new String[]{"Lifesteal", "TrueDamage", "ArmorPenetration", "MaxHPPoison", "Vampire", "Crit", "Bleed", "ChainLightning", "IceSpikes"};
            mythicalEffects = new String[]{"SummonMiniGolem", "Flight", "SummonMiniWarden", "Lightning", "KnockbackBurst", "Execute", "SoulEater", "MeteorStrike", "TimeStop"};
        }

        int count = quality.getEffectsCount();
        int mythicalCount = 0;
        int advancedCount = 0;

        if (quality == ItemQuality.STAR_9) { mythicalCount = 1; advancedCount = 4; }
        else if (quality == ItemQuality.STAR_10) { mythicalCount = 2; advancedCount = 8; }
        else if (quality == ItemQuality.STAR_11) { mythicalCount = 3; advancedCount = 12; }
        else if (quality.getStars() >= 7) { advancedCount = count; }

        int remaining = Math.max(0, count - mythicalCount - advancedCount);

        List<String> effects = new ArrayList<>();
        pickEffectsNoDuplicates(effects, mythicalEffects, mythicalCount);
        pickEffectsNoDuplicates(effects, advancedEffects, advancedCount);
        pickEffectsNoDuplicates(effects, normalEffects, remaining);

        return String.join(",", effects);
    }

    private void pickEffectsNoDuplicates(List<String> out, String[] pool, int needed) {
        if (needed <= 0 || pool.length == 0) return;
        List<String> shuffled = new ArrayList<>(Arrays.asList(pool));
        Collections.shuffle(shuffled);
        for (int i = 0; i < needed; i++) {
            if (i > 0 && i % shuffled.size() == 0) {
                Collections.shuffle(shuffled);
            }
            out.add(shuffled.get(i % shuffled.size()));
        }
    }

    public ItemStack upgradeQuality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        if (!isGear(item)) return item;

        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(QUALITY_KEY, PersistentDataType.INTEGER)) {
            return applyQuality(item.clone(), ItemQuality.STAR_1);
        }

        int currentStars = meta.getPersistentDataContainer().get(QUALITY_KEY, PersistentDataType.INTEGER);
        if (currentStars >= 11) return item;

        ItemQuality newQuality = getQualityByStars(currentStars + 1);
        if (newQuality == null) return item;

        ItemStack result = item.clone();
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta.hasAttributeModifiers()) {
            resultMeta.setAttributeModifiers(null);
        }
        resultMeta.setUnbreakable(false);
        if (resultMeta instanceof Damageable dmg) {
            dmg.setMaxDamage(null);
        }
        result.setItemMeta(resultMeta);

        return applyQuality(result, newQuality);
    }

    private ItemQuality getQualityByStars(int stars) {
        for (ItemQuality q : ItemQuality.values()) {
            if (q.getStars() == stars) return q;
        }
        return null;
    }

    public ItemStack applyMobEnchantments(ItemStack item, int gap) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        double chance = Math.min(1.0, 0.8 + (gap * 0.005));

        if (Math.random() < chance) {
            String type = item.getType().name();
            boolean isWeapon = type.endsWith("_SWORD") || type.endsWith("_AXE") || type.endsWith("_BOW") || type.endsWith("_CROSSBOW") || type.endsWith("_TRIDENT");
            boolean isArmor = type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") || type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS");

            if (isWeapon) {
                int minLevel = 3 + (int) (gap * 0.25);
                int maxLevel = 7 + gap;
                int powerLevel = minLevel + (int) (Math.random() * (maxLevel - minLevel + 1));

                meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, powerLevel, true);
                if (type.endsWith("_BOW")) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.POWER, powerLevel, true);
                }

                if (Math.random() < 0.5) {
                    int fireLevel = Math.min(15, 1 + (int) (gap * 0.1));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, fireLevel, true);
                }
            } else if (isArmor) {
                int maxProt = Math.min(20, 3 + (int) (gap * 0.2));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, maxProt, true);

                if (Math.random() < 0.4) {
                    int thornsLevel = Math.min(10, 2 + (int) (gap * 0.1));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.THORNS, thornsLevel, true);
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
