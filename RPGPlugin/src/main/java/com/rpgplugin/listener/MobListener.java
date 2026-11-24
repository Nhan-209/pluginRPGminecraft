package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.manager.MobManager;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class MobListener implements Listener {

    private final MobManager mobManager;
    private final ItemManager itemManager;

    public MobListener(MobManager mobManager, ItemManager itemManager) {
        this.mobManager = mobManager;
        this.itemManager = itemManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.hasMetadata("is_minion")) return;

        if (entity instanceof Wither || entity instanceof EnderDragon) {
            mobManager.scaleBoss(entity);
            return;
        }

        boolean isMiniBossType = entity instanceof Warden || entity instanceof WitherSkeleton
                || entity instanceof Evoker || entity instanceof Ravager;

        if (isMiniBossType) {
            if (!mobManager.canSpawnMiniBoss(entity.getLocation())) {
                if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                        || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
                    event.setCancelled(true);
                    return;
                }
            } else {
                entity.setMetadata("is_mini_boss", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            }
        }

        if (entity instanceof Spider) {
            if (Math.random() < 0.15) {
                Creeper creeper = entity.getWorld().spawn(entity.getLocation(), Creeper.class);
                entity.addPassenger(creeper);
            }
        }

        if (entity instanceof Monster && Math.random() < 0.3) {
            ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING, (int) (Math.random() * 10) + 1);
            if (entity.getEquipment() != null) {
                entity.getEquipment().setItemInOffHand(totem);
                entity.getEquipment().setItemInOffHandDropChance(0.05f);
            }
        }

        mobManager.scaleMob(entity);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof EnderDragon) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Monster && event.getTarget() instanceof Monster
                && !event.getEntity().hasMetadata("pet_owner")) {
            event.setCancelled(true);
            return;
        }

        if ((event.getEntity() instanceof IronGolem || event.getEntity() instanceof Warden)
                && event.getEntity().hasMetadata("pet_owner") && event.getTarget() != null) {
            String ownerName = event.getEntity().getMetadata("pet_owner").get(0).asString();
            if (event.getTarget().getName().equals(ownerName) || event.getTarget() instanceof Villager) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper creeper) {
            Location loc = creeper.getLocation();
            for (int i = 0; i < 3; i++) {
                Location spawnLoc = loc.clone().add(Math.random() * 2 - 1, 1, Math.random() * 2 - 1);
                TNTPrimed tnt = (TNTPrimed) loc.getWorld().spawnEntity(spawnLoc, EntityType.TNT);
                tnt.setFuseTicks(40);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Fireball fireball && event.getEntity().getShooter() instanceof Ghast) {
            Vector vel = fireball.getVelocity();
            fireball.setVelocity(vel.multiply(2.0));
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Fireball && event.getEntity().getShooter() instanceof Ghast) {
            Location loc = event.getEntity().getLocation();
            Spider spider = loc.getWorld().spawn(loc, Spider.class);
            Creeper creeper = loc.getWorld().spawn(loc, Creeper.class);
            spider.addPassenger(creeper);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.hasMetadata("village_guard")) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        if (entity instanceof Slime || entity instanceof MagmaCube) {
            entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
        }

        if (entity instanceof Zombie zombie) {
            if (zombie.isAdult() && !zombie.hasMetadata("is_minion")) {
                for (int i = 0; i < 2; i++) {
                    Zombie baby = (Zombie) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
                    baby.setBaby();
                }
            }
        }

        if (entity instanceof Panda) {
            for (int i = 0; i < 3; i++) {
                entity.getWorld().spawnEntity(entity.getLocation(), EntityType.CREEPER);
            }
        }

        if (entity instanceof Blaze) {
            if (!entity.hasMetadata("is_minion") && Math.random() < 0.3) {
                for (int i = 0; i < 2; i++) {
                    Blaze miniBlaze = (Blaze) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.BLAZE);
                    miniBlaze.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                }
            }
        }

        if (entity instanceof Warden && !entity.hasMetadata("pet_owner") && !entity.hasMetadata("is_minion")) {
            int highestLevel = mobManager.getHighestPlayerLevel();
            for (int i = 0; i < 5; i++) {
                Skeleton skeleton = (Skeleton) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SKELETON);
                mobManager.applyMobLevel(skeleton, highestLevel, true);
            }
        }

        if (entity instanceof Snowman && !entity.hasMetadata("pet_owner")) {
            if (!entity.hasMetadata("is_minion") && Math.random() < 0.5) {
                Snowman minion = (Snowman) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SNOW_GOLEM);
                minion.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
            }
        }

        if (entity instanceof WitherSkeleton) {
            for (int i = 0; i < 2; i++) {
                entity.getWorld().spawnEntity(entity.getLocation(), EntityType.BLAZE);
            }
        }

        if (entity instanceof PolarBear) {
            if (!entity.hasMetadata("is_minion") && Math.random() < 0.5) {
                PolarBear miniBoss = (PolarBear) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.POLAR_BEAR);
                miniBoss.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                miniBoss.setMetadata("is_mini_boss_bear", new FixedMetadataValue(RPGPlugin.getInstance(), true));

                int playerLevel = 1;
                if (entity.getKiller() != null) {
                    PlayerData data = RPGPlugin.getInstance().getPlayerDataManager().getPlayerData(entity.getKiller().getUniqueId());
                    if (data != null) playerLevel = data.getLevel();
                }

                miniBoss.setCustomName("§c[Mini Boss Lv." + (playerLevel + 5) + "] Polar Bear");
                miniBoss.setCustomNameVisible(true);

                Compat.setAttributeBase(miniBoss, Compat.SCALE, 1.8);
                Compat.setAttributeBase(miniBoss, Compat.MOVEMENT_SPEED, 0.4);
                Compat.setAttributeBase(miniBoss, Compat.MAX_HEALTH, 90.0);
                miniBoss.setHealth(90.0);
            }
        }

        // Boss kill star upgrade for killer's main-hand weapon
        if (entity instanceof Warden || entity instanceof Wither
                || entity instanceof EnderDragon || entity instanceof ElderGuardian) {
            if (entity.hasMetadata("pet_owner")) return;

            Player killer = entity.getKiller();
            if (killer != null) {
                ItemStack weapon = killer.getInventory().getItemInMainHand();
                if (weapon.hasItemMeta() && weapon.getItemMeta().getPersistentDataContainer().has(itemManager.QUALITY_KEY, PersistentDataType.INTEGER)) {
                    int currentStars = weapon.getItemMeta().getPersistentDataContainer().get(itemManager.QUALITY_KEY, PersistentDataType.INTEGER);

                    double chance = 1.0;
                    if (currentStars == 7) chance = 0.50;
                    else if (currentStars == 8) chance = 0.25;
                    else if (currentStars == 9) chance = 0.05;
                    else if (currentStars >= 10) chance = 0.01;

                    if (Math.random() < chance) {
                        ItemStack upgraded = itemManager.upgradeQuality(weapon);
                        if (upgraded != null && !upgraded.equals(weapon)) {
                            killer.getInventory().setItemInMainHand(upgraded);
                            killer.sendMessage("§a§lVũ khí của bạn đã được nâng cấp thêm 1 sao sau khi hạ gục Boss!");
                            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }
    }
}
