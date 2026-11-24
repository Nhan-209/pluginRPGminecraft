package com.rpgplugin.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Compat {

    public static final Attribute MAX_HEALTH = resolveAttribute("max_health", "generic.max_health");
    public static final Attribute ATTACK_DAMAGE = resolveAttribute("attack_damage", "generic.attack_damage");
    public static final Attribute ARMOR = resolveAttribute("armor", "generic.armor");
    public static final Attribute ARMOR_TOUGHNESS = resolveAttribute("armor_toughness", "generic.armor_toughness");
    public static final Attribute MOVEMENT_SPEED = resolveAttribute("movement_speed", "generic.movement_speed");
    public static final Attribute ATTACK_SPEED = resolveAttribute("attack_speed", "generic.attack_speed");
    public static final Attribute BLOCK_BREAK_SPEED = resolveAttribute("block_break_speed", "player.block_break_speed");
    public static final Attribute SCALE = resolveAttribute("scale", "generic.scale");
    public static final Attribute FOLLOW_RANGE = resolveAttribute("follow_range", "generic.follow_range");
    public static final Attribute FLYING_SPEED = resolveAttribute("flying_speed", "generic.flying_speed");
    public static final Attribute KNOCKBACK_RESISTANCE = resolveAttribute("knockback_resistance", "generic.knockback_resistance");

    private static final Pattern LEVEL_PATTERN = Pattern.compile("Lv\\.\\s*(\\d+)");

    private Compat() {}

    private static Attribute resolveAttribute(String modernKey, String legacyKey) {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modernKey));
        if (attr != null) {
            return attr;
        }
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacyKey));
    }

    public static double getAttributeValue(LivingEntity entity, Attribute attr, double fallback) {
        if (entity == null || attr == null) return fallback;
        AttributeInstance instance = entity.getAttribute(attr);
        return instance != null ? instance.getValue() : fallback;
    }

    public static double getAttributeBase(LivingEntity entity, Attribute attr, double fallback) {
        if (entity == null || attr == null) return fallback;
        AttributeInstance instance = entity.getAttribute(attr);
        return instance != null ? instance.getBaseValue() : fallback;
    }

    public static void setAttributeBase(LivingEntity entity, Attribute attr, double value) {
        if (entity == null || attr == null) return;
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static Vector safeDirection(Location from, Location to) {
        if (from == null || to == null) return new Vector(0, 0, 0);
        Vector diff = to.toVector().subtract(from.toVector());
        if (diff.lengthSquared() < 1e-6) {
            return new Vector(0, 0, 0);
        }
        return diff.normalize();
    }

    public static int parseEntityLevel(LivingEntity entity) {
        if (entity == null) return 1;
        String name = entity.getCustomName();
        if (name == null || name.isEmpty()) return 1;
        Matcher matcher = LEVEL_PATTERN.matcher(name);
        if (matcher.find()) {
            try {
                return Math.max(1, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    public static boolean setMaxStackSizeSafe(ItemStack item, int size) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getType().getMaxDurability() != 0) return false;
        int clamped = Math.min(99, Math.max(1, size));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.hasMaxStackSize() && meta.getMaxStackSize() == clamped) return false;
        meta.setMaxStackSize(clamped);
        item.setItemMeta(meta);
        return true;
    }
}
