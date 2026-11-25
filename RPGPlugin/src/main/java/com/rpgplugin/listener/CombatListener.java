package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.manager.PlayerDataManager;
import com.rpgplugin.model.ItemQuality;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class CombatListener implements Listener {

    private final ItemManager itemManager;
    private final PlayerDataManager dataManager;
    private final PlayerSkillListener skillListener;
    private final NamespacedKey projMultKey;

    public CombatListener(ItemManager itemManager, PlayerDataManager dataManager, PlayerSkillListener skillListener) {
        this.itemManager = itemManager;
        this.dataManager = dataManager;
        this.skillListener = skillListener;
        this.projMultKey = new NamespacedKey(RPGPlugin.getInstance(), "rpg_proj_mult");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getProjectile() instanceof Projectile proj)) return;

        ItemStack bow = event.getBow();
        if (bow == null || !bow.hasItemMeta()) return;

        PersistentDataContainer bowPdc = bow.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer projPdc = proj.getPersistentDataContainer();

        if (bowPdc.has(itemManager.EFFECTS_KEY, PersistentDataType.STRING)) {
            String effects = bowPdc.get(itemManager.EFFECTS_KEY, PersistentDataType.STRING);
            if (effects != null) {
                projPdc.set(itemManager.EFFECTS_KEY, PersistentDataType.STRING, effects);
            }
        }

        int stars = itemManager.getQualityStars(bow);
        ItemQuality quality = getQualityByStars(stars);
        if (quality != null) {
            projPdc.set(projMultKey, PersistentDataType.DOUBLE, 1.0 + quality.getStatMultiplier());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTridentThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player)) return;

        ItemStack item = trident.getItemStack();
        if (!item.hasItemMeta()) return;

        PersistentDataContainer itemPdc = item.getItemMeta().getPersistentDataContainer();
        PersistentDataContainer projPdc = trident.getPersistentDataContainer();

        if (itemPdc.has(itemManager.EFFECTS_KEY, PersistentDataType.STRING)) {
            String effects = itemPdc.get(itemManager.EFFECTS_KEY, PersistentDataType.STRING);
            if (effects != null) {
                projPdc.set(itemManager.EFFECTS_KEY, PersistentDataType.STRING, effects);
            }
        }

        int stars = itemManager.getQualityStars(item);
        ItemQuality quality = getQualityByStars(stars);
        if (quality != null) {
            projPdc.set(projMultKey, PersistentDataType.DOUBLE, 1.0 + quality.getStatMultiplier());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        LivingEntity actualAttacker = null;
        if (event.getDamager() instanceof LivingEntity le) {
            actualAttacker = le;
        } else if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof LivingEntity le) {
                actualAttacker = le;
            }
        }

        // Wild Monsters do not hurt other Monsters (allow player-owned Warden pets to attack Monsters)
        boolean victimIsMonster = victim instanceof Monster;
        if (victimIsMonster && actualAttacker instanceof Monster && !actualAttacker.hasMetadata("pet_owner")) {
            event.setCancelled(true);
            return;
        }

        // Snow Golem hit by Snowball heals instead of taking damage
        if (victim instanceof Snowman && event.getDamager() instanceof Snowball) {
            event.setCancelled(true);
            double maxHp = Compat.getAttributeBase(victim, Compat.MAX_HEALTH, 20.0);
            victim.setHealth(Math.min(maxHp, victim.getHealth() + 20.0));
            return;
        }

        // Prevent Pets and Owners (and Pets and Villagers) from harming each other
        if (actualAttacker != null && actualAttacker.hasMetadata("pet_owner")) {
            String ownerName = actualAttacker.getMetadata("pet_owner").get(0).asString();
            if (victim.getName().equals(ownerName) || victim instanceof Villager) {
                event.setCancelled(true);
                return;
            }
        }
        if (victim.hasMetadata("pet_owner") && actualAttacker != null) {
            String ownerName = victim.getMetadata("pet_owner").get(0).asString();
            if (actualAttacker.getName().equals(ownerName)) {
                event.setCancelled(true);
                return;
            }
        }

        // Villager Fletcher/Librarian Arrow Scaling
        if (event.getDamager().hasMetadata("villager_arrow")) {
            int lv = event.getDamager().getMetadata("villager_arrow").get(0).asInt();
            double arrowDmg = 7.0 + (lv * 1.4);
            if (victim instanceof Phantom || victim instanceof Creeper) {
                arrowDmg *= 1.50;
            }
            event.setDamage(arrowDmg);
        }

        // Player Attack Logic (Melee + Ranged)
        if (actualAttacker instanceof Player player) {
            PlayerData pData = dataManager.getPlayerData(player.getUniqueId());

            if (event.getDamager() instanceof Projectile proj) {
                PersistentDataContainer projPdc = proj.getPersistentDataContainer();
                if (projPdc.has(projMultKey, PersistentDataType.DOUBLE)) {
                    double mult = projPdc.getOrDefault(projMultKey, PersistentDataType.DOUBLE, 1.0);
                    event.setDamage(event.getDamage() * Math.max(0.5, mult));
                }
                if (pData != null) {
                    event.setDamage((event.getDamage() + pData.getFlatDamageBonus()) * pData.getDamageMultiplier());
                }
            }

            if (pData != null) {
                if (Math.random() < pData.getCritChance()) {
                    event.setDamage(event.getDamage() * pData.getCritMultiplier());
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.0, 0), 14, 0.3, 0.3, 0.3, 0.1);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.1f);
                }
                double armorPen = pData.getArmorPenetration();
                if (armorPen > 0 && Compat.getAttributeValue(victim, Compat.ARMOR, 0.0) > 0) {
                    event.setDamage(event.getDamage() * (1.0 + armorPen * 0.8));
                }
            }

            // Command nearby player pets to focus the victim
            if (!victim.equals(player) && !victim.hasMetadata("pet_owner") && !(victim instanceof Villager)) {
                retargetPlayerPets(player, victim);
            }

            // Read Special Effects (from main hand on melee, or projectile PDC on ranged)
            String effectsStr = null;
            if (event.getDamager() instanceof Player) {
                ItemStack weapon = player.getInventory().getItemInMainHand();
                if (weapon.hasItemMeta()) {
                    effectsStr = weapon.getItemMeta().getPersistentDataContainer().get(itemManager.EFFECTS_KEY, PersistentDataType.STRING);
                }
            } else if (event.getDamager() instanceof Projectile proj) {
                effectsStr = proj.getPersistentDataContainer().get(itemManager.EFFECTS_KEY, PersistentDataType.STRING);
            }

            if (effectsStr != null && !effectsStr.isEmpty()) {
                double extraTrueDamage = 0.0;
                for (String effect : effectsStr.split(",")) {
                    extraTrueDamage += applyEffect(effect.trim(), player, victim, pData, event);
                }
                if (extraTrueDamage > 0.0 && !event.isCancelled()) {
                    applyTrueDamageSafely(victim, extraTrueDamage, event);
                }
            }
        }

        // Victim is Player
        if (victim instanceof Player player) {
            long now = System.currentTimeMillis();

            // Dash invulnerability frame
            if (player.hasMetadata("rpg_dash_iframe")) {
                long expire = player.getMetadata("rpg_dash_iframe").get(0).asLong();
                if (now <= expire) {
                    event.setCancelled(true);
                    player.sendMessage("§b[Né Đòn!]");
                    return;
                }
                player.removeMetadata("rpg_dash_iframe", RPGPlugin.getInstance());
            }

            PlayerData pData = dataManager.getPlayerData(player.getUniqueId());

            // Agility Dodge
            if (pData != null && Math.random() < pData.getDodgeChance()) {
                event.setCancelled(true);
                player.sendMessage("§b§l[NÉ TRÁNH!]");
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1.0, 0), 8, 0.2, 0.2, 0.2, 0.05);
                return;
            }

            // Nearby player pets defend their owner
            if (actualAttacker != null && !actualAttacker.equals(player) && !actualAttacker.hasMetadata("pet_owner") && !(actualAttacker instanceof Villager)) {
                retargetPlayerPets(player, actualAttacker);
            }

            // Armor Effects
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null && armor.hasItemMeta() && armor.getItemMeta().getPersistentDataContainer().has(itemManager.EFFECTS_KEY, PersistentDataType.STRING)) {
                    String effectsStr = armor.getItemMeta().getPersistentDataContainer().get(itemManager.EFFECTS_KEY, PersistentDataType.STRING);
                    if (effectsStr != null) {
                        for (String effect : effectsStr.split(",")) {
                            applyArmorEffect(effect.trim(), actualAttacker, player, event);
                            if (event.isCancelled()) return;
                        }
                    }
                }
            }

            if (actualAttacker != null) {
                if (actualAttacker instanceof Zombie zombie && !actualAttacker.hasMetadata("is_minion")) {
                    double bonusDmg;
                    if (zombie.isAdult()) {
                        bonusDmg = player.getHealth() * 0.10;
                    } else {
                        bonusDmg = Compat.getAttributeValue(player, Compat.MAX_HEALTH, 20.0) * 0.03;
                    }
                    event.setDamage(event.getDamage() + bonusDmg);
                }

                if (actualAttacker instanceof PolarBear && actualAttacker.hasMetadata("is_mini_boss_bear")) {
                    double maxHp = Compat.getAttributeValue(player, Compat.MAX_HEALTH, 20.0);
                    event.setDamage(event.getDamage() + (maxHp * 0.40));
                }

                if (actualAttacker instanceof org.bukkit.entity.Enderman) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                } else if (actualAttacker.getType().name().equals("BREEZE")) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                } else if (actualAttacker instanceof Spider) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1));
                    Block block = player.getLocation().getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.COBWEB);
                    }
                } else if (actualAttacker instanceof Phantom) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                    player.getWorld().strikeLightning(player.getLocation());
                } else if (actualAttacker instanceof Ravager) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 1));
                } else if (actualAttacker instanceof Vindicator) {
                    player.setFireTicks(200);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1));
                } else if (actualAttacker instanceof Vex) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                } else if (actualAttacker instanceof Zombie && actualAttacker.hasMetadata("is_minion")) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                } else if (actualAttacker instanceof Silverfish sf) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));

                    long nowMs = System.currentTimeMillis();
                    sf.setMetadata("sf_retreat_until", new FixedMetadataValue(RPGPlugin.getInstance(), nowMs + 2200L));
                    int nextSign = sf.hasMetadata("sf_flank_sign") ? -sf.getMetadata("sf_flank_sign").get(0).asInt() : 1;
                    sf.setMetadata("sf_flank_sign", new FixedMetadataValue(RPGPlugin.getInstance(), nextSign));

                    Vector away = Compat.safeDirection(player.getLocation(), sf.getLocation()).setY(0);
                    Vector pLook = player.getLocation().getDirection().setY(0);
                    if (pLook.lengthSquared() > 1e-6) {
                        pLook = pLook.normalize();
                    }
                    Vector side = new Vector(-pLook.getZ() * nextSign, 0, pLook.getX() * nextSign);
                    Vector escape = away.add(side.multiply(0.8));
                    if (escape.lengthSquared() > 1e-6) {
                        sf.setVelocity(escape.normalize().multiply(0.75).setY(0.28));
                    }
                }
            }

            // Skeleton fast arrow effects
            if (event.getDamager().hasMetadata("skeleton_fast")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 0));

                if (actualAttacker != null) {
                    Vector knockback = Compat.safeDirection(actualAttacker.getLocation(), player.getLocation()).multiply(1.5).setY(0.5);
                    player.setVelocity(knockback);

                    if (!actualAttacker.isDead()) {
                        double healAmount = event.getFinalDamage() * 0.5;
                        double atkMaxHp = Compat.getAttributeValue(actualAttacker, Compat.MAX_HEALTH, 20.0);
                        actualAttacker.setHealth(Math.min(atkMaxHp, actualAttacker.getHealth() + healAmount));
                    }
                }
            }

            // Shield WorldTreeBlessing
            if (player.isBlocking()) {
                ItemStack offhand = player.getInventory().getItemInOffHand();
                ItemStack mainhand = player.getInventory().getItemInMainHand();
                ItemStack shield = (offhand.getType() == Material.SHIELD) ? offhand
                        : ((mainhand.getType() == Material.SHIELD) ? mainhand : null);

                if (shield != null && itemManager.hasEffect(shield, "WorldTreeBlessing")) {
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
                    player.sendMessage("§a[Cây Thần] Sự ban phước đã được kích hoạt khi bạn đỡ đòn!");
                }
            }

            // RPG Defense % Damage Reduction (beyond vanilla armor cap)
            if (pData != null && pData.getRpgDamageReduction() > 0 && !event.isCancelled()) {
                event.setDamage(event.getDamage() * (1.0 - pData.getRpgDamageReduction()));
            }

            // Auto Second Wind trigger if HP drops below 20%
            if (pData != null && skillListener != null && !event.isCancelled()) {
                double hpAfter = player.getHealth() - event.getFinalDamage();
                skillListener.tryAutoSecondWind(player, pData, hpAfter);
            }
        }

        // Snowball from Snowman
        if (event.getDamager() instanceof Snowball && actualAttacker instanceof Snowman) {
            double maxHpTarget = Compat.getAttributeBase(victim, Compat.MAX_HEALTH, 20.0);
            double bonusDamage = maxHpTarget * 0.0004;
            double totalDamage = 5.0 + bonusDamage;

            event.setDamage(totalDamage);

            victim.setFireTicks(200);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));

            if (!actualAttacker.isDead()) {
                double atkMaxHp = Compat.getAttributeBase(actualAttacker, Compat.MAX_HEALTH, 20.0);
                actualAttacker.setHealth(Math.min(atkMaxHp, actualAttacker.getHealth() + totalDamage));

                double addedHp = maxHpTarget * 0.01;
                double targetDamage = Compat.getAttributeBase(victim, Compat.ATTACK_DAMAGE, 1.0);
                double addedDmg = targetDamage * 0.01;

                Compat.setAttributeBase(actualAttacker, Compat.MAX_HEALTH, atkMaxHp + addedHp);
                if (Compat.ATTACK_DAMAGE != null && actualAttacker.getAttribute(Compat.ATTACK_DAMAGE) != null) {
                    double curDmg = Compat.getAttributeBase(actualAttacker, Compat.ATTACK_DAMAGE, 1.0);
                    Compat.setAttributeBase(actualAttacker, Compat.ATTACK_DAMAGE, curDmg + addedDmg);
                }

                if (victim instanceof Monster) {
                    double newTargetHp = Math.max(1.0, maxHpTarget - addedHp);
                    Compat.setAttributeBase(victim, Compat.MAX_HEALTH, newTargetHp);
                    if (victim.getHealth() > newTargetHp) victim.setHealth(newTargetHp);

                    if (Compat.ATTACK_DAMAGE != null && victim.getAttribute(Compat.ATTACK_DAMAGE) != null) {
                        double newTargetDmg = Math.max(0.5, targetDamage - addedDmg);
                        Compat.setAttributeBase(victim, Compat.ATTACK_DAMAGE, newTargetDmg);
                    }
                }
            }
        }

        // Iron Golem attack scaling & lifesteal
        if (actualAttacker instanceof IronGolem golem) {
            double golemMaxHp = Compat.getAttributeBase(golem, Compat.MAX_HEALTH, 100.0);
            Compat.setAttributeBase(golem, Compat.MAX_HEALTH, golemMaxHp * 1.01);

            if (Compat.ATTACK_DAMAGE != null && golem.getAttribute(Compat.ATTACK_DAMAGE) != null) {
                double golemDmg = Compat.getAttributeBase(golem, Compat.ATTACK_DAMAGE, 15.0);
                Compat.setAttributeBase(golem, Compat.ATTACK_DAMAGE, golemDmg * 1.01);
            }

            if (!golem.isDead()) {
                double healAmount = event.getFinalDamage() * 0.7;
                double newMax = Compat.getAttributeBase(golem, Compat.MAX_HEALTH, 100.0);
                golem.setHealth(Math.min(newMax, golem.getHealth() + healAmount));
            }
        }

        // Ender Dragon 5% spawn End Crystal when hit
        if (victim instanceof EnderDragon) {
            if (Math.random() < 0.05) {
                victim.getWorld().spawnEntity(victim.getLocation(), EntityType.END_CRYSTAL);
            }
        }

        // Ravager 10% lightning + 2 Pillagers
        if (victim instanceof Ravager) {
            if (Math.random() < 0.1) {
                if (actualAttacker != null) {
                    actualAttacker.getWorld().strikeLightning(actualAttacker.getLocation());
                }
                for (int i = 0; i < 2; i++) {
                    victim.getWorld().spawnEntity(victim.getLocation(), EntityType.PILLAGER);
                }
            }
        }

        // Monster low-HP reactions
        double currentHpAfterDmg = victim.getHealth() - event.getFinalDamage();
        double maxHp = Compat.getAttributeValue(victim, Compat.MAX_HEALTH, 20.0);

        if (currentHpAfterDmg > 0 && currentHpAfterDmg <= maxHp * 0.1) {
            if (victim instanceof Witch && !victim.hasMetadata("summoned_creeper")) {
                victim.setMetadata("summoned_creeper", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                Creeper creeper = victim.getWorld().spawn(victim.getLocation(), Creeper.class);
                creeper.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1));
            }
        }

        if (currentHpAfterDmg > 0 && currentHpAfterDmg <= maxHp * 0.3) {
            if ((victim instanceof Piglin || victim instanceof PigZombie) && !victim.hasMetadata("bed_bombed")) {
                victim.setMetadata("bed_bombed", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                boolean playerNearby = false;
                for (Entity e : victim.getNearbyEntities(10, 10, 10)) {
                    if (e instanceof Player) { playerNearby = true; break; }
                }
                if (playerNearby && victim.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
                    Block block = victim.getLocation().getBlock();
                    block.setType(Material.RED_BED);
                    victim.getWorld().createExplosion(block.getLocation(), 5.0f, true);
                    victim.getWorld().spawnEntity(block.getLocation(), EntityType.TNT);
                }
            }
        }

        // Update Mob Health Display accurately 1 tick later
        RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
            if (victim.isValid() && !victim.isDead() && !(victim instanceof Player)) {
                String originalName = victim.getCustomName();
                if (originalName != null) {
                    String baseName = originalName.replaceAll(" §a\\[\\d+/\\d+ HP\\]", "");
                    int currentHp = (int) Math.ceil(Math.max(0.0, victim.getHealth()));
                    int displayMaxHp = (int) Compat.getAttributeValue(victim, Compat.MAX_HEALTH, 20.0);
                    victim.setCustomName(baseName + " §a[" + currentHp + "/" + displayMaxHp + " HP]");
                }
            }
        }, 1L);
    }

    private void applyTrueDamageSafely(LivingEntity victim, double extraTrueDamage, EntityDamageByEntityEvent event) {
        double finalDmg = event.getFinalDamage();
        double currentHp = victim.getHealth();
        if (currentHp - finalDmg - extraTrueDamage <= 0.0) {
            // Boost raw event damage so Minecraft attributes the kill properly to the player
            event.setDamage(event.getDamage() + extraTrueDamage * 5.0 + currentHp);
        } else {
            victim.setHealth(Math.max(0.5, currentHp - extraTrueDamage));
        }
    }

    private void retargetPlayerPets(Player owner, LivingEntity target) {
        String ownerName = owner.getName();
        for (Entity near : owner.getNearbyEntities(24, 12, 24)) {
            if ((near instanceof IronGolem || near instanceof Warden) && near.hasMetadata("pet_owner")) {
                if (near.getMetadata("pet_owner").get(0).asString().equals(ownerName)) {
                    ((org.bukkit.entity.Mob) near).setTarget(target);
                }
            }
        }
    }

    private double applyEffect(String effect, Player attacker, LivingEntity victim, PlayerData pData, EntityDamageByEntityEvent event) {
        double healMult = pData != null ? pData.getHealingMultiplier() : 1.0;
        int playerLevel = pData != null ? pData.getLevel() : 1;
        double victimMaxHp = Compat.getAttributeValue(victim, Compat.MAX_HEALTH, 20.0);
        double attackerMaxHp = Compat.getAttributeValue(attacker, Compat.MAX_HEALTH, 20.0);

        switch (effect) {
            case "Burn":
                victim.setFireTicks(60);
                break;
            case "Slow":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                break;
            case "Poison":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                break;
            case "Freeze":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 4));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 4));
                break;
            case "Blindness":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                break;
            case "Wither":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                break;
            case "Weakness":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
                break;
            case "Venom":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 3));
                break;
            case "Lifesteal":
                double lifesteal = event.getFinalDamage() * 0.2 * healMult;
                attacker.setHealth(Math.min(attackerMaxHp, attacker.getHealth() + lifesteal));
                break;
            case "TrueDamage":
                return victimMaxHp * 0.05;
            case "ArmorPenetration":
                event.setDamage(event.getDamage() * 1.5);
                break;
            case "MaxHPPoison":
                return victimMaxHp * 0.02;
            case "Bleed":
                return event.getFinalDamage() * 0.3;
            case "IceSpikes":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
                return 5.0;
            case "Crit":
                if (Math.random() < 0.2) {
                    event.setDamage(event.getDamage() * 2);
                    attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 10);
                }
                break;
            case "ChainLightning":
                if (Math.random() < 0.15) {
                    for (Entity e : victim.getNearbyEntities(5, 5, 5)) {
                        if (e instanceof LivingEntity target && !e.equals(attacker) && !(e instanceof Villager) && !e.hasMetadata("pet_owner")) {
                            attacker.getWorld().strikeLightningEffect(e.getLocation());
                            target.damage(event.getFinalDamage() * 0.5, attacker);
                        }
                    }
                }
                break;
            case "SummonMiniGolem":
                int golemCount = 0;
                for (Entity ent : attacker.getNearbyEntities(20, 20, 20)) {
                    if (ent instanceof IronGolem && ent.hasMetadata("pet_owner")) {
                        if (ent.getMetadata("pet_owner").get(0).asString().equals(attacker.getName())) {
                            golemCount++;
                        }
                    }
                }
                if (golemCount < 3) {
                    int petLv = Math.max(1, playerLevel - 2);
                    IronGolem golem = attacker.getWorld().spawn(attacker.getLocation(), IronGolem.class);
                    Compat.setAttributeBase(golem, Compat.SCALE, 0.5);
                    double gHp = 40.0 + (petLv * 12.0);
                    Compat.setAttributeBase(golem, Compat.MAX_HEALTH, gHp);
                    golem.setHealth(gHp);
                    Compat.setAttributeBase(golem, Compat.ATTACK_DAMAGE, 6.0 + (petLv * 1.5));
                    golem.setMetadata("pet_owner", new FixedMetadataValue(RPGPlugin.getInstance(), attacker.getName()));
                    golem.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                    golem.setCustomName("§a[Lv. " + petLv + "] Pet of " + attacker.getName());
                    golem.setCustomNameVisible(true);
                    if (!victim.equals(attacker) && !(victim instanceof Villager)) {
                        golem.setTarget(victim);
                    }
                    RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), golem::remove, 1200L);
                }
                break;
            case "SummonMiniWarden":
                int wardenCount = 0;
                for (Entity ent : attacker.getNearbyEntities(20, 20, 20)) {
                    if (ent instanceof Warden && ent.hasMetadata("pet_owner")) {
                        if (ent.getMetadata("pet_owner").get(0).asString().equals(attacker.getName())) {
                            wardenCount++;
                        }
                    }
                }
                if (wardenCount < 3) {
                    int petLv = Math.max(1, playerLevel - 2);
                    Warden warden = attacker.getWorld().spawn(attacker.getLocation(), Warden.class);
                    Compat.setAttributeBase(warden, Compat.SCALE, 0.5);
                    double wHp = 80.0 + (petLv * 18.0);
                    Compat.setAttributeBase(warden, Compat.MAX_HEALTH, wHp);
                    warden.setHealth(wHp);
                    Compat.setAttributeBase(warden, Compat.ATTACK_DAMAGE, 8.0 + (petLv * 2.0));
                    warden.setMetadata("pet_owner", new FixedMetadataValue(RPGPlugin.getInstance(), attacker.getName()));
                    warden.setMetadata("is_minion", new FixedMetadataValue(RPGPlugin.getInstance(), true));
                    warden.setCustomName("§3[Lv. " + petLv + "] Pet of " + attacker.getName());
                    warden.setCustomNameVisible(true);
                    if (!victim.equals(attacker) && !(victim instanceof Villager)) {
                        warden.setTarget(victim);
                    }
                    RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), warden::remove, 1200L);
                }
                break;
            case "Lightning":
                victim.getWorld().strikeLightning(victim.getLocation());
                break;
            case "TimeStop":
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                attacker.sendMessage("§d[TimeStop] Thời gian đã ngưng đọng!");
                break;
            case "MeteorStrike":
                Fireball fireball = victim.getWorld().spawn(victim.getLocation().add(0, 10, 0), Fireball.class);
                fireball.setDirection(new Vector(0, -1, 0));
                fireball.setYield(4.0f);
                break;
            case "Vampire":
                double vampHeal = event.getFinalDamage() * 0.5 * healMult;
                attacker.setHealth(Math.min(attackerMaxHp, attacker.getHealth() + vampHeal));
                break;
            case "KnockbackBurst":
                for (Entity e : victim.getNearbyEntities(5, 5, 5)) {
                    if (e instanceof LivingEntity && !e.equals(attacker) && !e.hasMetadata("pet_owner")) {
                        Vector knockback = Compat.safeDirection(victim.getLocation(), e.getLocation()).multiply(1.5);
                        e.setVelocity(knockback);
                    }
                }
                break;
            case "Execute":
                if (victim.getHealth() <= victimMaxHp * 0.30) {
                    event.setDamage(event.getDamage() * 1.50);
                    victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);
                }
                break;
            case "SoulEater":
                if (victim.getHealth() - event.getFinalDamage() <= 0) {
                    double soulHeal = attackerMaxHp * 0.10 * healMult;
                    attacker.setHealth(Math.min(attackerMaxHp, attacker.getHealth() + soulHeal));
                    attacker.setFoodLevel(Math.min(20, attacker.getFoodLevel() + 4));
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
                    victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.05);
                }
                break;
        }
        return 0.0;
    }

    private void applyArmorEffect(String effect, LivingEntity attacker, Player victim, EntityDamageByEntityEvent event) {
        double maxHp = Compat.getAttributeValue(victim, Compat.MAX_HEALTH, 20.0);

        switch (effect) {
            case "Resilience":
                event.setDamage(event.getDamage() * 0.95);
                break;
            case "FireImmunity":
                if (victim.getFireTicks() > 0 && Math.random() < 0.25) {
                    victim.setFireTicks(0);
                }
                break;
            case "Regeneration":
                if (Math.random() < 0.1) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                }
                break;
            case "Thorns":
                if (attacker != null) {
                    double reflect = event.getDamage() * 0.1;
                    attacker.damage(reflect);
                }
                break;
            case "Dodge":
                if (Math.random() < 0.05) {
                    event.setCancelled(true);
                    victim.sendMessage("§aDODGE!");
                }
                break;
            case "ExplosionShield":
                if (attacker instanceof Creeper || attacker instanceof TNTPrimed) {
                    event.setDamage(event.getDamage() * 0.7);
                }
                break;
            case "LastStand":
                if (victim.getHealth() <= maxHp * 0.2) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                }
                break;
            case "Retribution":
                if (attacker != null && victim.getHealth() - event.getFinalDamage() <= 0) {
                    attacker.getWorld().createExplosion(victim.getLocation(), 4.0F, false, false);
                }
                break;
            case "Immortality":
                if (victim.getHealth() - event.getFinalDamage() <= 0 && Math.random() < 0.05) {
                    event.setDamage(0);
                    victim.setHealth(maxHp * 0.5);
                    victim.sendMessage("§bIMMORTALITY TRIGGERED!");
                }
                break;
            case "Ghost":
                if (Math.random() < 0.1) {
                    RPGPlugin.applyTrueInvisibility(victim, 5000);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
                    victim.sendMessage("§8[Ghost] Bạn đã hóa thành bóng ma!");
                }
                break;
            case "Aegis":
                if (victim.getHealth() < maxHp * 0.5) {
                    event.setDamage(event.getDamage() * 0.8);
                }
                break;
            case "Fortitude":
                if (Math.random() < 0.1 && victim.getAbsorptionAmount() < 10) {
                    victim.setAbsorptionAmount(victim.getAbsorptionAmount() + 2);
                }
                break;
            case "Spikes":
                if (attacker != null && Math.random() < 0.2) {
                    for (Entity e : victim.getNearbyEntities(3, 3, 3)) {
                        if (e instanceof LivingEntity living && !e.equals(victim) && !(e instanceof Villager) && !e.hasMetadata("pet_owner")) {
                            living.damage(event.getDamage() * 0.2);
                        }
                    }
                }
                break;
            case "Grounded":
                RPGPlugin.getInstance().getServer().getScheduler().runTaskLater(RPGPlugin.getInstance(), () -> {
                    if (victim.isValid()) {
                        victim.setVelocity(new Vector(0, victim.getVelocity().getY(), 0));
                    }
                }, 1L);
                break;
            case "Forcefield":
                if (attacker != null && Math.random() < 0.15) {
                    Vector push = Compat.safeDirection(victim.getLocation(), attacker.getLocation()).multiply(2.0).setY(0.5);
                    attacker.setVelocity(push);
                    victim.sendMessage("§3[Forcefield] Đẩy lùi kẻ địch!");
                }
                break;
            case "Phoenix":
                if (victim.getHealth() - event.getFinalDamage() <= 0) {
                    long now = System.currentTimeMillis();
                    boolean ready = !victim.hasMetadata("phoenix_cooldown")
                            || now >= victim.getMetadata("phoenix_cooldown").get(0).asLong();
                    if (ready) {
                        event.setDamage(0);
                        victim.setHealth(maxHp);
                        victim.setFireTicks(0);
                        victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation(), 100, 0.5, 1, 0.5, 0.1);
                        victim.sendMessage("§6[Phoenix] Bạn đã hồi sinh từ đống tro tàn!");
                        victim.setMetadata("phoenix_cooldown", new FixedMetadataValue(RPGPlugin.getInstance(), now + 300_000L));
                    }
                }
                break;
        }
    }

    private ItemQuality getQualityByStars(int stars) {
        if (stars < 0) return null;
        for (ItemQuality q : ItemQuality.values()) {
            if (q.getStars() == stars) return q;
        }
        return null;
    }
}
