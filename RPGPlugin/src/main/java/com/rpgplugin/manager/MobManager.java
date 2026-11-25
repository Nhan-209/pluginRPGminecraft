package com.rpgplugin.manager;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class MobManager {

    private final JavaPlugin plugin;
    private VillagerCombatManager villagerCombatManager;

    public MobManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startAIUpdateTask();
    }

    public void setVillagerCombatManager(VillagerCombatManager villagerCombatManager) {
        this.villagerCombatManager = villagerCombatManager;
    }

    private void startAIUpdateTask() {
        // 20-tick (1s) AI task
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World w : Bukkit.getWorlds()) {
                if (w.getPlayers().isEmpty()) continue;

                for (LivingEntity e : w.getLivingEntities()) {
                    if (e instanceof Sheep sheep && e.hasMetadata("angry_sheep")) {
                        LivingEntity target = sheep.getTarget();
                        if (target == null || target.isDead() || target.getLocation().distanceSquared(sheep.getLocation()) > 225.0) {
                            for (Entity near : sheep.getNearbyEntities(10, 5, 10)) {
                                if (near instanceof LivingEntity living && near != sheep) {
                                    sheep.setTarget(living);
                                    target = living;
                                    break;
                                }
                            }
                        }
                        if (target != null && sheep.getLocation().distanceSquared(target.getLocation()) < 4.0) {
                            target.damage(5.0, sheep);
                        }
                    } else if (e instanceof IronGolem golem && !e.hasMetadata("pet_owner")) {
                        if (golem.getTarget() instanceof Player p) {
                            if (p.getLocation().getY() - golem.getLocation().getY() >= 3.0 && Math.random() < 0.2) {
                                EnderPearl pearl = golem.launchProjectile(EnderPearl.class);
                                Vector dir = Compat.safeDirection(golem.getLocation(), p.getLocation());
                                pearl.setVelocity(dir.multiply(1.5));
                            }
                        }
                    } else if (e instanceof Evoker evoker) {
                        if (evoker.getTarget() != null && Math.random() < 0.1) {
                            evoker.getWorld().strikeLightningEffect(evoker.getTarget().getLocation());
                            evoker.getTarget().damage(6.0, evoker);
                        }
                    } else if (e instanceof Snowman snowman) {
                        if (snowman.getTarget() == null || snowman.getTarget().isDead()) {
                            for (Entity near : snowman.getNearbyEntities(20, 10, 20)) {
                                if (near instanceof Monster m && !m.hasMetadata("pet_owner")) {
                                    snowman.setTarget(m);
                                    break;
                                }
                            }
                        }
                    } else if (e instanceof Silverfish sf) {
                        if (sf.getTarget() == null || sf.getTarget().isDead()) {
                            Player nearest = null;
                            double bestDistSq = 48.0 * 48.0;
                            for (Player p : w.getPlayers()) {
                                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                                double dSq = p.getLocation().distanceSquared(sf.getLocation());
                                if (dSq < bestDistSq) {
                                    bestDistSq = dSq;
                                    nearest = p;
                                }
                            }
                            if (nearest != null) {
                                sf.setTarget(nearest);
                            }
                        }
                    }
                }
            }
        }, 20L, 20L);

        // Fast Task (2 ticks = 0.1s) - original burst attack cadence
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (World w : Bukkit.getWorlds()) {
                if (w.getPlayers().isEmpty()) continue;

                for (Mob mob : w.getEntitiesByClass(Mob.class)) {
                    if (mob.getTarget() == null) continue;

                    if (mob instanceof IronGolem golem) {
                        if (golem.getLocation().distanceSquared(golem.getTarget().getLocation()) < 9.0) {
                            if (Math.random() < 0.2) {
                                double dmg = Compat.getAttributeValue(golem, Compat.ATTACK_DAMAGE, 15.0) * 0.5;
                                golem.getTarget().damage(dmg, golem);
                            }
                        }
                    } else if (mob instanceof Warden warden && !mob.hasMetadata("pet_owner")) {
                        LivingEntity target = warden.getTarget();
                        if (target instanceof Player) {
                            Location wLoc = warden.getLocation();
                            Location tLoc = target.getLocation();

                            Vector lookDir = wLoc.getDirection();
                            Vector stepDir = lookDir.lengthSquared() > 1e-6 ? lookDir.normalize().multiply(1.5) : new Vector(0, 0, 0);

                            Block frontBlock1 = wLoc.clone().add(stepDir).add(0, 1, 0).getBlock();
                            Block frontBlock2 = wLoc.clone().add(stepDir).add(0, 2, 0).getBlock();
                            if (frontBlock1.getType().isSolid() && frontBlock1.getType() != Material.BEDROCK && frontBlock1.getType() != Material.BARRIER) {
                                frontBlock1.breakNaturally();
                                warden.getWorld().spawnParticle(Particle.CLOUD, frontBlock1.getLocation(), 10);
                            }
                            if (frontBlock2.getType().isSolid() && frontBlock2.getType() != Material.BEDROCK && frontBlock2.getType() != Material.BARRIER) {
                                frontBlock2.breakNaturally();
                                warden.getWorld().spawnParticle(Particle.CLOUD, frontBlock2.getLocation(), 10);
                            }

                            if (tLoc.getY() - wLoc.getY() >= 2.0 && wLoc.distanceSquared(tLoc) < 225.0) {
                                if (((Entity) warden).isOnGround()) {
                                    Block blockAtFeet = wLoc.getBlock();
                                    warden.teleport(wLoc.clone().add(0, 1.1, 0));
                                    blockAtFeet.setType(Material.COBBLESTONE);
                                }
                            }

                            if (((Entity) warden).isOnGround()) {
                                Vector flatDir = tLoc.toVector().subtract(wLoc.toVector()).setY(0);
                                if (flatDir.lengthSquared() > 1e-6) {
                                    flatDir = flatDir.normalize();
                                    Block gapBlock = wLoc.clone().add(flatDir.multiply(1.5)).subtract(0, 0.5, 0).getBlock();
                                    if (!gapBlock.getType().isSolid()) {
                                        gapBlock.setType(Material.COBBLESTONE);
                                    }
                                }
                            }
                        }
                    } else if (mob instanceof Snowman snowman) {
                        LivingEntity target = snowman.getTarget();
                        if (target.isValid() && snowman.hasLineOfSight(target)) {
                            Location sLoc = snowman.getLocation();
                            Location tLoc = target.getLocation();
                            double distSq = sLoc.distanceSquared(tLoc);

                            if (distSq < 625.0) {
                                Snowball snowball = snowman.launchProjectile(Snowball.class);
                                Location fromEye = sLoc.clone().add(0, snowman.getHeight(), 0);
                                Location toCenter = tLoc.clone().add(0, target.getHeight() / 2, 0);
                                Vector dir = Compat.safeDirection(fromEye, toCenter);
                                snowball.setVelocity(dir.multiply(3.0));
                                snowball.setMetadata("snowman_fast", new FixedMetadataValue(plugin, true));

                                snowman.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0));
                                snowman.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1));

                                if (Math.random() < 0.2) {
                                    Block targetFeet = tLoc.getBlock();
                                    if (!targetFeet.getType().isSolid() && targetFeet.getType() != Material.WATER && targetFeet.getType() != Material.LAVA) {
                                        targetFeet.setType(Material.POWDER_SNOW);
                                    }
                                }
                            }

                            if (tLoc.getY() - sLoc.getY() >= 2.0 && distSq < 225.0) {
                                if (((Entity) snowman).isOnGround()) {
                                    Block blockAtFeet = sLoc.getBlock();
                                    snowman.teleport(sLoc.clone().add(0, 1.1, 0));
                                    blockAtFeet.setType(Material.DIRT);
                                }
                            }

                            if (((Entity) snowman).isOnGround()) {
                                Vector flatDir = tLoc.toVector().subtract(sLoc.toVector()).setY(0);
                                if (flatDir.lengthSquared() > 1e-6) {
                                    flatDir = flatDir.normalize();
                                    Block gapBlock = sLoc.clone().add(flatDir.multiply(1.5)).subtract(0, 0.5, 0).getBlock();
                                    if (!gapBlock.getType().isSolid() && gapBlock.getType() != Material.WATER && gapBlock.getType() != Material.LAVA) {
                                        gapBlock.setType(Material.ICE);
                                    }
                                }
                            }
                        }
                    } else if (mob instanceof Enderman enderman) {
                        Location eLoc = enderman.getLocation();
                        Location tLoc = enderman.getTarget().getLocation();
                        if (tLoc.getY() - eLoc.getY() >= 2.0 && eLoc.distanceSquared(tLoc) < 225.0) {
                            if (((Entity) enderman).isOnGround()) {
                                Block blockAtFeet = eLoc.getBlock();
                                enderman.teleport(eLoc.clone().add(0, 1.1, 0));
                                blockAtFeet.setType(Material.END_STONE);
                            }
                        }
                    } else if (mob instanceof ElderGuardian guardian) {
                        if (guardian.hasLineOfSight(guardian.getTarget())) {
                            if (Math.random() < 0.2) {
                                LivingEntity target = guardian.getTarget();
                                double dmg = Compat.getAttributeValue(guardian, Compat.ATTACK_DAMAGE, 8.0);
                                target.damage(dmg, guardian);
                                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                                target.getWorld().strikeLightning(target.getLocation());

                                if (Math.random() < 0.05) {
                                    for (int i = 0; i < 3; i++) {
                                        guardian.getWorld().spawnEntity(guardian.getLocation(), EntityType.GUARDIAN);
                                    }
                                }
                            }
                        }
                    } else if (mob instanceof Guardian guardian) {
                        Compat.setAttributeBase(guardian, Compat.MOVEMENT_SPEED, 0.7);
                        if (guardian.hasLineOfSight(guardian.getTarget())) {
                            if (Math.random() < 0.33) {
                                double dmg = Compat.getAttributeValue(guardian, Compat.ATTACK_DAMAGE, 6.0);
                                guardian.getTarget().damage(dmg, guardian);
                            }
                        }
                    } else if (mob instanceof Pillager pillager) {
                        if (pillager.hasLineOfSight(pillager.getTarget())) {
                            if (Math.random() < 0.1) {
                                Arrow arrow = pillager.launchProjectile(Arrow.class);
                                Location from = pillager.getLocation().add(0, pillager.getHeight(), 0);
                                Location to = pillager.getTarget().getLocation().add(0, pillager.getTarget().getHeight() / 2, 0);
                                Vector dir = Compat.safeDirection(from, to);
                                arrow.setVelocity(dir.multiply(2.0));
                            }
                        }
                    } else if (mob instanceof Shulker shulker) {
                        if (shulker.hasLineOfSight(shulker.getTarget())) {
                            if (Math.random() < 0.2) {
                                ShulkerBullet bullet = shulker.launchProjectile(ShulkerBullet.class);
                                Location from = shulker.getLocation().add(0, shulker.getHeight() / 2, 0);
                                Location to = shulker.getTarget().getLocation().add(0, shulker.getTarget().getHeight() / 2, 0);
                                Vector dir = Compat.safeDirection(from, to);
                                bullet.setVelocity(dir.multiply(2.5));
                            }
                        }
                    } else if (mob instanceof Phantom phantom) {
                        Compat.setAttributeBase(phantom, Compat.FLYING_SPEED, 1.5);
                        Compat.setAttributeBase(phantom, Compat.MOVEMENT_SPEED, 1.5);
                        if (Math.random() < 0.2) {
                            Vector dir = Compat.safeDirection(phantom.getLocation(), phantom.getTarget().getLocation());
                            phantom.setVelocity(dir.multiply(1.5));
                        }
                    } else if (mob instanceof Silverfish sf) {
                        handleSilverfishAI(sf, now);
                    }
                }
            }
        }, 2L, 2L);
    }

    private void handleSilverfishAI(Silverfish sf, long now) {
        if (!(sf.getTarget() instanceof Player player) || !player.isValid() || player.isDead()
                || player.getWorld() != sf.getWorld()) {
            return;
        }

        Location sfLoc = sf.getLocation();
        Location pLoc = player.getLocation();
        double distSq = sfLoc.distanceSquared(pLoc);
        if (distSq > 2500.0) return;

        Vector playerLook = pLoc.getDirection().setY(0);
        if (playerLook.lengthSquared() < 1e-6) {
            playerLook = new Vector(0, 0, 1);
        } else {
            playerLook = playerLook.normalize();
        }

        int sign = sf.hasMetadata("sf_flank_sign")
                ? sf.getMetadata("sf_flank_sign").get(0).asInt()
                : ((sf.getEntityId() & 1) == 0 ? 1 : -1);
        Vector sideVec = new Vector(-playerLook.getZ() * sign, 0, playerLook.getX() * sign);

        long retreatUntil = sf.hasMetadata("sf_retreat_until")
                ? sf.getMetadata("sf_retreat_until").get(0).asLong()
                : 0L;
        if (now < retreatUntil) {
            Location retreatTarget = pLoc.clone()
                    .subtract(playerLook.clone().multiply(8.5))
                    .add(sideVec.clone().multiply(4.0));
            sf.getPathfinder().moveTo(retreatTarget, 1.65);
            if (distSq < 16.0 && ((Entity) sf).isOnGround()) {
                Vector dashAway = Compat.safeDirection(pLoc, retreatTarget).setY(0);
                if (dashAway.lengthSquared() > 1e-6) {
                    sf.setVelocity(dashAway.normalize().multiply(0.55).setY(0.22));
                }
            }
            return;
        }

        Vector toSf = sfLoc.toVector().subtract(pLoc.toVector()).setY(0);
        double dot = toSf.lengthSquared() > 1e-6 ? playerLook.dot(toSf.normalize()) : 0.0;

        if (dot > 0.30 && distSq > 4.0 && distSq < 256.0) {
            Location flankLoc = pLoc.clone()
                    .add(sideVec.clone().multiply(4.5))
                    .subtract(playerLook.clone().multiply(2.5));
            sf.getPathfinder().moveTo(flankLoc, 1.6);
            if (((Entity) sf).isOnGround() && Math.random() < 0.25) {
                Vector scurry = Compat.safeDirection(sfLoc, flankLoc).setY(0);
                if (scurry.lengthSquared() > 1e-6) {
                    sf.setVelocity(scurry.normalize().multiply(0.45).setY(0.15));
                }
            }
        } else if (distSq <= 5.76) {
            sf.getPathfinder().moveTo(player, 1.6);
        } else {
            Location backSpot = pLoc.clone().subtract(playerLook.clone().multiply(1.5));
            sf.getPathfinder().moveTo(backSpot, 1.6);
        }
    }

    public void scaleMob(LivingEntity entity) {
        if (entity instanceof Player) return;

        Location loc = entity.getLocation();
        double distance = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
        int avgLv = getAveragePlayerLevel();
        int highestLv = getHighestPlayerLevel();
        World.Environment env = loc.getWorld().getEnvironment();

        int level;
        if (env == World.Environment.THE_END) {
            int endRef = Math.max(avgLv, (int) Math.round(highestLv * 0.85));
            if (distance < 500.0) {
                level = Math.max(30, endRef + (int) (Math.random() * 11));
            } else {
                int outerBonus = 10 + Math.min(40, (int) ((distance - 500.0) / 100.0));
                level = Math.max(40, endRef + outerBonus);
            }
        } else if (env == World.Environment.NETHER) {
            int distLv = 1 + (int) (distance / 50);
            level = Math.min(avgLv + 10, Math.max(distLv, (int) (avgLv * 0.5)));
        } else {
            level = 1 + (int) (distance / 50);
            if (level > avgLv + 10) {
                level = avgLv + 10;
            }
        }
        if (level < 1) level = 1;

        applyMobLevel(entity, level, false);
    }

    public void scaleBoss(LivingEntity boss) {
        int highestLevel = getHighestPlayerLevel();
        int bossLevel = highestLevel + 10 + (int) (Math.random() * 90);
        applyMobLevel(boss, bossLevel, false);
    }

    public void applyMobLevel(LivingEntity entity, int level) {
        applyMobLevel(entity, level, false);
    }

    public void applyMobLevel(LivingEntity entity, int level, boolean force) {
        if (!force && entity.getCustomName() != null && entity.getCustomName().contains("[Lv.")) {
            return;
        }

        String rawName = entity.getCustomName();
        String baseName;
        if (rawName != null && !rawName.isEmpty()) {
            baseName = rawName.replaceAll("§c\\[Lv\\. \\d+\\] §f", "").replaceAll(" §a\\[\\d+/\\d+ HP\\]", "");
        } else {
            baseName = entity.getName();
        }

        entity.setCustomName("§c[Lv. " + level + "] §f" + baseName);
        entity.setCustomNameVisible(true);

        if (entity instanceof Villager villager && villagerCombatManager != null) {
            villagerCombatManager.scaleVillager(villager, level);
            return;
        }

        double baseHealth;
        if (entity.hasMetadata("rpg_base_hp")) {
            baseHealth = entity.getMetadata("rpg_base_hp").get(0).asDouble();
        } else {
            baseHealth = Compat.getAttributeBase(entity, Compat.MAX_HEALTH, 20.0);
            entity.setMetadata("rpg_base_hp", new FixedMetadataValue(plugin, baseHealth));
        }
        double newHealth = baseHealth * (1.0 + (level * 0.20));
        Compat.setAttributeBase(entity, Compat.MAX_HEALTH, newHealth);
        entity.setHealth(newHealth);

        if (Compat.ATTACK_DAMAGE != null && entity.getAttribute(Compat.ATTACK_DAMAGE) != null) {
            double baseDamage;
            if (entity.hasMetadata("rpg_base_dmg")) {
                baseDamage = entity.getMetadata("rpg_base_dmg").get(0).asDouble();
            } else {
                baseDamage = Compat.getAttributeBase(entity, Compat.ATTACK_DAMAGE, 2.0);
                entity.setMetadata("rpg_base_dmg", new FixedMetadataValue(plugin, baseDamage));
            }
            double newDamage = baseDamage * (1.0 + (level * 0.10));
            Compat.setAttributeBase(entity, Compat.ATTACK_DAMAGE, newDamage);
        }

        if (Compat.MOVEMENT_SPEED != null && entity.getAttribute(Compat.MOVEMENT_SPEED) != null) {
            double baseSpeed;
            if (entity.hasMetadata("rpg_base_spd")) {
                baseSpeed = entity.getMetadata("rpg_base_spd").get(0).asDouble();
            } else {
                baseSpeed = Compat.getAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.25);
                entity.setMetadata("rpg_base_spd", new FixedMetadataValue(plugin, baseSpeed));
            }
            double newSpeed = baseSpeed * (1.0 + (level * 0.002));
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, newSpeed);
        }

        if (entity instanceof AbstractSkeleton || entity instanceof Silverfish) {
            Compat.setAttributeBase(entity, Compat.FOLLOW_RANGE, 100.0);
        }

        if (entity instanceof Snowman) {
            double currentHp = Compat.getAttributeBase(entity, Compat.MAX_HEALTH, 20.0);
            Compat.setAttributeBase(entity, Compat.MAX_HEALTH, currentHp + 200.0);
            entity.setHealth(currentHp + 200.0);

            if (Compat.MOVEMENT_SPEED != null && entity.getAttribute(Compat.MOVEMENT_SPEED) != null) {
                double spd = Compat.getAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.2);
                Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, spd * 2.0);
            }
        }

        int gap = Math.max(0, getAveragePlayerLevel() - level);

        if (entity instanceof Monster && entity.getEquipment() != null) {
            if (Math.random() < 0.50) {
                ItemManager itemManager = RPGPlugin.getInstance().getItemManager();
                Material[] helmets = {Material.IRON_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET};
                Material[] chestplates = {Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE};
                Material[] leggings = {Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS};
                Material[] boots = {Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS};

                int r = (int) (Math.random() * 3);
                entity.getEquipment().setHelmet(itemManager.applyMobEnchantments(itemManager.rollItemQuality(new ItemStack(helmets[r])), gap));
                entity.getEquipment().setChestplate(itemManager.applyMobEnchantments(itemManager.rollItemQuality(new ItemStack(chestplates[r])), gap));
                entity.getEquipment().setLeggings(itemManager.applyMobEnchantments(itemManager.rollItemQuality(new ItemStack(leggings[r])), gap));
                entity.getEquipment().setBoots(itemManager.applyMobEnchantments(itemManager.rollItemQuality(new ItemStack(boots[r])), gap));

                entity.getEquipment().setHelmetDropChance(0.05f);
                entity.getEquipment().setChestplateDropChance(0.05f);
                entity.getEquipment().setLeggingsDropChance(0.05f);
                entity.getEquipment().setBootsDropChance(0.05f);
            }
        }

        applySpecificMobTraits(entity, level, gap);

        if (entity.getWorld().getEnvironment() == World.Environment.THE_END
                && !(entity instanceof Villager) && level >= 50) {
            double curArmor = Compat.getAttributeBase(entity, Compat.ARMOR, 0.0);
            double curTough = Compat.getAttributeBase(entity, Compat.ARMOR_TOUGHNESS, 0.0);
            Compat.setAttributeBase(entity, Compat.ARMOR, Math.max(curArmor, Math.min(16.0, level * 0.08)));
            Compat.setAttributeBase(entity, Compat.ARMOR_TOUGHNESS, Math.max(curTough, Math.min(10.0, level * 0.05)));
        }
    }

    private void applySpecificMobTraits(LivingEntity entity, int level, int gap) {
        ItemManager itemManager = RPGPlugin.getInstance().getItemManager();

        if (entity instanceof AbstractSkeleton) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.3);
            if (Compat.ATTACK_DAMAGE != null && entity.getAttribute(Compat.ATTACK_DAMAGE) != null) {
                Compat.setAttributeBase(entity, Compat.ATTACK_DAMAGE, Compat.getAttributeBase(entity, Compat.ATTACK_DAMAGE, 2.0) * 1.5);
            }
            if (entity.getEquipment() != null) {
                ItemStack bow = new ItemStack(Material.BOW);
                entity.getEquipment().setItemInMainHand(itemManager.applyMobEnchantments(itemManager.rollItemQuality(bow), gap));
                entity.getEquipment().setItemInMainHandDropChance(0.05f);
            }
        } else if (entity instanceof Zombie zombie) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, zombie.isBaby() ? 0.35 : 0.15);
            if (Compat.ATTACK_DAMAGE != null && entity.getAttribute(Compat.ATTACK_DAMAGE) != null) {
                Compat.setAttributeBase(entity, Compat.ATTACK_DAMAGE, Compat.getAttributeBase(entity, Compat.ATTACK_DAMAGE, 3.0) * 2.0);
            }
            Compat.setAttributeBase(entity, Compat.ARMOR, 10.0);
            if (entity.getEquipment() != null) {
                ItemStack sword = new ItemStack(Material.IRON_SWORD);
                entity.getEquipment().setItemInMainHand(itemManager.applyMobEnchantments(itemManager.rollItemQuality(sword), gap));
                entity.getEquipment().setItemInMainHandDropChance(0.05f);
            }
        } else if (entity instanceof Creeper) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.35);
        } else if (entity.getType().name().equals("BREEZE")) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.5);
        } else if (entity instanceof Spider) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.45);
        } else if (entity instanceof Vindicator) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.42);
        } else if (entity instanceof Guardian && !(entity instanceof ElderGuardian)) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.7);
        } else if (entity instanceof Phantom) {
            Compat.setAttributeBase(entity, Compat.FLYING_SPEED, 1.5);
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 1.5);
        } else if (entity instanceof Silverfish) {
            Compat.setAttributeBase(entity, Compat.MOVEMENT_SPEED, 0.45);
        }
    }

    private int getAveragePlayerLevel() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return 1;
        int totalLevel = 0;
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = RPGPlugin.getInstance().getPlayerDataManager().getPlayerData(p.getUniqueId());
            if (data != null) {
                totalLevel += data.getLevel();
                count++;
            }
        }
        return count == 0 ? 1 : totalLevel / count;
    }

    public int getHighestPlayerLevel() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return 1;
        int highest = 1;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = RPGPlugin.getInstance().getPlayerDataManager().getPlayerData(p.getUniqueId());
            if (data != null && data.getLevel() > highest) {
                highest = data.getLevel();
            }
        }
        return highest;
    }

    public boolean canSpawnMiniBoss(Location loc) {
        int chunkX = loc.getChunk().getX();
        int chunkZ = loc.getChunk().getZ();
        World world = loc.getWorld();

        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (world.isChunkLoaded(x, z)) {
                    for (Entity e : world.getChunkAt(x, z).getEntities()) {
                        if (e.hasMetadata("is_mini_boss")) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
