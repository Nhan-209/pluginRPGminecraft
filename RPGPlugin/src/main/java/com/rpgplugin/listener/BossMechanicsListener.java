package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.util.Compat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BossMechanicsListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.hasMetadata("is_minion") || entity.hasMetadata("pet_owner")) return;

        double maxHp = Compat.getAttributeValue(entity, Compat.MAX_HEALTH, 20.0);
        double hpAfterDamage = entity.getHealth() - event.getFinalDamage();
        double hpPercentage = hpAfterDamage / maxHp;

        if (entity instanceof Warden warden) {
            handleWardenMechanics(warden, hpPercentage);
        } else if (entity instanceof Wither wither) {
            handleWitherMechanics(wither, hpPercentage);
        } else if (entity instanceof EnderDragon dragon) {
            handleDragonMechanics(dragon, hpPercentage);
        }
    }

    private void handleWardenMechanics(Warden warden, double hpPercentage) {
        if (!warden.hasMetadata("warden_spawned_minis") && hpPercentage < 0.99) {
            warden.setMetadata("warden_spawned_minis", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWardenMinis(warden.getLocation(), 3, warden);
        }
        if (hpPercentage < 0.5 && !warden.hasMetadata("warden_p2")) {
            warden.setMetadata("warden_p2", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWitherSkeletonMinis(warden.getLocation(), 3, warden);
        }
    }

    private void handleWitherMechanics(Wither wither, double hpPercentage) {
        if (!wither.hasMetadata("wither_spawned_minis") && hpPercentage < 0.99) {
            wither.setMetadata("wither_spawned_minis", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWitherMinis(wither.getLocation(), 3, wither);
        }
        if (hpPercentage < 0.5 && !wither.hasMetadata("wither_p2")) {
            wither.setMetadata("wither_p2", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            wither.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 2));
            spawnWitherSkeletonMinis(wither.getLocation(), 2, wither);

            World w = wither.getWorld();
            for (int i = 0; i < 6; i++) {
                w.spawn(wither.getLocation().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), TNTPrimed.class);
            }
        }
        if (hpPercentage < 0.2 && !wither.hasMetadata("wither_p3")) {
            wither.setMetadata("wither_p3", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWitherMinis(wither.getLocation(), 2, wither);
        }
        if (hpPercentage < 0.1 && !wither.hasMetadata("wither_p4")) {
            wither.setMetadata("wither_p4", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWardenMinis(wither.getLocation(), 1, wither);
        }
    }

    private void handleDragonMechanics(EnderDragon dragon, double hpPercentage) {
        if (!dragon.hasMetadata("dragon_spawned_minis") && hpPercentage < 0.99) {
            dragon.setMetadata("dragon_spawned_minis", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWitherMinis(dragon.getLocation(), 3, dragon);
        }
        if (hpPercentage < 0.5 && !dragon.hasMetadata("dragon_p2")) {
            dragon.setMetadata("dragon_p2", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnZombieMinis(dragon.getLocation(), 15, dragon);
        }
        if (hpPercentage < 0.4 && !dragon.hasMetadata("dragon_p3")) {
            dragon.setMetadata("dragon_p3", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            for (Player p : dragon.getWorld().getPlayers()) {
                dragon.getWorld().strikeLightning(p.getLocation());
            }
        }
        if (hpPercentage < 0.3 && !dragon.hasMetadata("dragon_p4")) {
            dragon.setMetadata("dragon_p4", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnPhantoms(dragon.getLocation(), 10, dragon);
        }
        if (hpPercentage < 0.2 && !dragon.hasMetadata("dragon_p5")) {
            dragon.setMetadata("dragon_p5", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnPhantoms(dragon.getLocation(), 20, dragon);
        }
        if (hpPercentage < 0.1 && !dragon.hasMetadata("dragon_p6")) {
            dragon.setMetadata("dragon_p6", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            spawnWardenMinis(dragon.getLocation(), 1, dragon);
        }

        if (hpPercentage < 0.5 && !dragon.hasMetadata("dragon_crystal_cooldown")) {
            if (Math.random() < 0.15) {
                Location crystalLoc = dragon.getLocation().clone().add((Math.random() - 0.5) * 60, 0, (Math.random() - 0.5) * 60);
                crystalLoc.setY(dragon.getWorld().getHighestBlockYAt(crystalLoc) + 1);
                dragon.getWorld().spawn(crystalLoc, EnderCrystal.class);

                for (Player p : dragon.getWorld().getPlayers()) {
                    p.sendMessage("§d§l[BOSS] Rồng Ender đã triệu hồi một viên pha lê để hồi máu! Hãy phá hủy nó!");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                }

                dragon.setMetadata("dragon_crystal_cooldown", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
                    if (dragon.isValid()) {
                        dragon.removeMetadata("dragon_crystal_cooldown", RPGPlugin.getInstance());
                    }
                }, 400L);
            }
        }
    }

    private void spawnWardenMinis(Location loc, int count, LivingEntity parent) {
        for (int i = 0; i < count; i++) {
            Warden minion = loc.getWorld().spawn(loc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), Warden.class);
            minion.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            minion.setCustomName("§cMini Warden");
            minion.setCustomNameVisible(true);
            Compat.setAttributeBase(minion, Compat.MOVEMENT_SPEED, 0.4);
            Compat.setAttributeBase(minion, Compat.SCALE, 0.5);
            if (parent instanceof Mob mob && mob.getTarget() != null) {
                minion.setTarget(mob.getTarget());
            }
        }
    }

    private void spawnWitherSkeletonMinis(Location loc, int count, LivingEntity parent) {
        for (int i = 0; i < count; i++) {
            WitherSkeleton minion = loc.getWorld().spawn(loc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), WitherSkeleton.class);
            minion.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            minion.setCustomName("§cMini Wither Skeleton");
            minion.setCustomNameVisible(true);
            Compat.setAttributeBase(minion, Compat.MOVEMENT_SPEED, 0.35);
            Compat.setAttributeBase(minion, Compat.SCALE, 0.5);

            ItemStack sword = new ItemStack(Material.STONE_SWORD);
            sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 1);
            sword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 3);
            if (minion.getEquipment() != null) {
                minion.getEquipment().setItemInMainHand(sword);
            }
        }
    }

    private void spawnWitherMinis(Location loc, int count, LivingEntity parent) {
        for (int i = 0; i < count; i++) {
            Wither minion = loc.getWorld().spawn(loc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), Wither.class);
            minion.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            minion.setCustomName("§cMini Wither");
            minion.setCustomNameVisible(true);
            Compat.setAttributeBase(minion, Compat.MOVEMENT_SPEED, 0.4);
            Compat.setAttributeBase(minion, Compat.SCALE, 0.5);
        }
    }

    private void spawnZombieMinis(Location loc, int count, LivingEntity parent) {
        for (int i = 0; i < count; i++) {
            Zombie minion = loc.getWorld().spawn(loc.clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2), Zombie.class);
            minion.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            minion.setCustomName("§cMini Zombie");
            minion.setCustomNameVisible(true);
            if (minion.getEquipment() != null) {
                minion.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                minion.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                minion.getEquipment().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
                minion.getEquipment().setBoots(new ItemStack(Material.GOLDEN_BOOTS));
            }
            Compat.setAttributeBase(minion, Compat.MOVEMENT_SPEED, 0.3);
        }
    }

    private void spawnPhantoms(Location loc, int count, LivingEntity parent) {
        for (int i = 0; i < count; i++) {
            Phantom minion = loc.getWorld().spawn(loc.clone().add(Math.random() * 6 - 3, 10, Math.random() * 6 - 3), Phantom.class);
            minion.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            minion.setCustomName("§cMini Phantom");
            minion.setCustomNameVisible(true);
            if (parent instanceof Mob mob && mob.getTarget() != null) {
                minion.setTarget(mob.getTarget());
            }
        }
    }
}
