package com.rpgplugin.manager;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerCombatManager implements Listener {

    private static final long WARN_EXPIRE_MS = 60_000L;
    private static final long ENRAGE_DURATION_MS = 60_000L;
    private static final long MOB_TARGET_DURATION_MS = 20_000L;
    private static final long GUARD_REGION_COOLDOWN_MS = 120_000L;

    private final RPGPlugin plugin;
    private final Map<String, Long> guardCooldowns = new ConcurrentHashMap<>();
    private final NamespacedKey rageSpeedKey;

    public VillagerCombatManager(RPGPlugin plugin) {
        this.plugin = plugin;
        this.rageSpeedKey = new NamespacedKey(plugin, "villager_rage_speed");
        startTasks();
    }

    public void scaleVillager(Villager villager, int level) {
        double newMaxHp = 60.0 * (1.0 + (level * 0.25));
        Compat.setAttributeBase(villager, Compat.MAX_HEALTH, newMaxHp);
        villager.setHealth(newMaxHp);

        Compat.setAttributeBase(villager, Compat.ARMOR, Math.min(20.0, 8.0 + (level * 0.30)));
        Compat.setAttributeBase(villager, Compat.ARMOR_TOUGHNESS, Math.min(12.0, 4.0 + (level * 0.15)));
        Compat.setAttributeBase(villager, Compat.KNOCKBACK_RESISTANCE, 0.45);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        long now = System.currentTimeMillis();

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            LivingEntity actualAttacker = resolveAttacker(byEntity);

            if (actualAttacker instanceof Player player) {
                handlePlayerHitVillager(villager, player, byEntity, now);
                return;
            }

            if (actualAttacker instanceof Monster monster) {
                applyVillagerDamageProtection(villager, event);

                setMobTarget(villager, monster, now);
                int allies = 0;
                for (Entity near : villager.getNearbyEntities(16, 8, 16)) {
                    if (allies >= 4) break;
                    if (near instanceof Villager ally && ally.isAdult() && !ally.hasMetadata("villager_enraged_target")) {
                        setMobTarget(ally, monster, now);
                        allies++;
                    }
                }

                double maxHp = Compat.getAttributeValue(villager, Compat.MAX_HEALTH, 60.0);
                double hpAfter = villager.getHealth() - event.getFinalDamage();
                if (hpAfter < maxHp * 0.50) {
                    trySpawnVillageGuard(villager, monster, now);
                }
                return;
            }
        }

        if (isProtectedCause(event.getCause())) {
            applyVillagerDamageProtection(villager, event);
        }
    }

    private void handlePlayerHitVillager(Villager villager, Player player, EntityDamageByEntityEvent event, long now) {
        String playerUuid = player.getUniqueId().toString();

        // Case 1: Already enraged at this player -> refresh timer
        if (villager.hasMetadata("villager_enraged_target")) {
            String targetUuid = villager.getMetadata("villager_enraged_target").get(0).asString();
            if (targetUuid.equals(playerUuid)) {
                villager.setMetadata("villager_enraged_expire", new FixedMetadataValue(plugin, now + ENRAGE_DURATION_MS));
                return;
            }
        }

        // Case 2: Previously warned this player -> trigger full rage (+70% speed + stun hits)
        if (villager.hasMetadata("villager_warned_player") && villager.hasMetadata("villager_warned_time")) {
            String warnedUuid = villager.getMetadata("villager_warned_player").get(0).asString();
            long warnedAt = villager.getMetadata("villager_warned_time").get(0).asLong();
            if (warnedUuid.equals(playerUuid) && (now - warnedAt) <= WARN_EXPIRE_MS) {
                villager.removeMetadata("villager_warned_player", plugin);
                villager.removeMetadata("villager_warned_time", plugin);

                villager.setMetadata("villager_enraged_target", new FixedMetadataValue(plugin, playerUuid));
                villager.setMetadata("villager_enraged_expire", new FixedMetadataValue(plugin, now + ENRAGE_DURATION_MS));

                if (Compat.MOVEMENT_SPEED != null) {
                    AttributeInstance speedAttr = villager.getAttribute(Compat.MOVEMENT_SPEED);
                    if (speedAttr != null) {
                        speedAttr.removeModifier(rageSpeedKey);
                        speedAttr.addTransientModifier(new AttributeModifier(rageSpeedKey, 0.70, AttributeModifier.Operation.ADD_SCALAR));
                    }
                }

                notifyNearby(villager, player, "§c§l[Dân Làng] §f\"Nói không nghe! Đứng lại đó cho tao!\"");
                villager.getWorld().playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.65f);
                villager.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, villager.getLocation().add(0, 2.0, 0), 12, 0.4, 0.3, 0.4, 0.0);
                return;
            }
        }

        // Case 3: First warning -> 50% chance to challenge the player
        if (Math.random() < 0.50) {
            double maxHp = Compat.getAttributeValue(villager, Compat.MAX_HEALTH, 60.0);
            if (villager.getHealth() - event.getFinalDamage() <= 0.0) {
                double safeHp = Math.min(maxHp, Math.max(10.0, maxHp * 0.30));
                if (villager.getHealth() < safeHp) {
                    villager.setHealth(safeHp);
                }
                event.setDamage(Math.max(0.0, villager.getHealth() - safeHp));
            }

            villager.setMetadata("villager_warned_player", new FixedMetadataValue(plugin, playerUuid));
            villager.setMetadata("villager_warned_time", new FixedMetadataValue(plugin, now));

            notifyNearby(villager, player, "§e§l[Dân Làng] §f\"Ngon thì đánh tao thêm cái nữa!\"");
            villager.getWorld().playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.75f);
            villager.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, villager.getLocation().add(0, 2.0, 0), 8, 0.3, 0.3, 0.3, 0.0);
        }
    }

    private void applyVillagerDamageProtection(Villager villager, EntityDamageEvent event) {
        double maxHp = Compat.getAttributeValue(villager, Compat.MAX_HEALTH, 60.0);
        double reduced = event.getDamage() * 0.50;
        double cap = maxHp * 0.20;
        event.setDamage(Math.min(reduced, cap));
    }

    private boolean isProtectedCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.LIGHTNING
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.WITHER;
    }

    private void trySpawnVillageGuard(Villager villager, Monster attacker, long now) {
        int regionX = villager.getLocation().getChunk().getX() >> 2;
        int regionZ = villager.getLocation().getChunk().getZ() >> 2;
        String regionKey = villager.getWorld().getUID() + ":" + regionX + ":" + regionZ;

        Long cdExpire = guardCooldowns.get(regionKey);
        if (cdExpire != null && now < cdExpire) return;

        int existingGolems = 0;
        for (Entity near : villager.getNearbyEntities(24, 12, 24)) {
            if (near instanceof IronGolem) {
                existingGolems++;
            }
        }
        if (existingGolems >= 2) return;

        guardCooldowns.put(regionKey, now + GUARD_REGION_COOLDOWN_MS);

        IronGolem guard = villager.getWorld().spawn(villager.getLocation(), IronGolem.class);
        guard.setMetadata("village_guard", new FixedMetadataValue(plugin, true));

        int vLv = Compat.parseEntityLevel(villager);
        int highest = plugin.getMobManager() != null ? plugin.getMobManager().getHighestPlayerLevel() : 1;
        int guardLv = Math.max(vLv, highest);

        if (plugin.getMobManager() != null) {
            plugin.getMobManager().applyMobLevel(guard, guardLv, true);
        }
        guard.setCustomName("§b[Lv. " + guardLv + "] §fVệ Binh Làng");
        guard.setCustomNameVisible(true);
        guard.setTarget(attacker);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (guard.isValid() && !guard.isDead()) {
                guard.remove();
            }
        }, 1800L);
    }

    private void startTasks() {
        // Fast Villager Combat AI (every 4 ticks = 0.2s)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                if (world.getPlayers().isEmpty()) continue;

                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    if (!villager.isValid() || villager.isDead()) continue;

                    // Priority 1: Enraged against a Player
                    if (villager.hasMetadata("villager_enraged_target")) {
                        if (processEnragedPlayerCombat(villager, now)) {
                            continue;
                        }
                    }

                    // Priority 2: Self-defense against a Monster
                    if (villager.hasMetadata("villager_mob_target") && villager.isAdult()) {
                        processMonsterDefense(villager, now);
                    }
                }
            }
        }, 4L, 4L);

        // Passive Regen, Debuff Cleansing, Threat Detection & Metadata Cleanup (every 40 ticks = 2s)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                if (world.getPlayers().isEmpty()) continue;

                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    if (!villager.isValid() || villager.isDead()) continue;

                    boolean fighting = villager.hasMetadata("villager_enraged_target") || villager.hasMetadata("villager_mob_target");
                    double maxHp = Compat.getAttributeValue(villager, Compat.MAX_HEALTH, 60.0);
                    if (villager.getHealth() < maxHp) {
                        double healRate = fighting ? 0.02 : 0.04;
                        villager.setHealth(Math.min(maxHp, villager.getHealth() + (maxHp * healRate)));
                    }

                    trimDebuff(villager, PotionEffectType.POISON);
                    trimDebuff(villager, PotionEffectType.WITHER);

                    if (villager.hasMetadata("villager_warned_time")) {
                        long warnedAt = villager.getMetadata("villager_warned_time").get(0).asLong();
                        if (now - warnedAt > WARN_EXPIRE_MS) {
                            villager.removeMetadata("villager_warned_player", plugin);
                            villager.removeMetadata("villager_warned_time", plugin);
                        }
                    }

                    // Proactive village threat detection when idle
                    if (!fighting && villager.isAdult()) {
                        for (Entity near : villager.getNearbyEntities(10, 6, 10)) {
                            if (near instanceof Monster monster && !monster.hasMetadata("pet_owner") && monster.isValid() && !monster.isDead()) {
                                if (monster.getTarget() instanceof Villager || villager.getLocation().distanceSquared(monster.getLocation()) <= 64.0) {
                                    setMobTarget(villager, monster, now);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }, 40L, 40L);
    }

    private boolean processEnragedPlayerCombat(Villager villager, long now) {
        long expire = villager.hasMetadata("villager_enraged_expire")
                ? villager.getMetadata("villager_enraged_expire").get(0).asLong()
                : 0L;
        if (now > expire) {
            calmDownVillager(villager);
            return false;
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(villager.getMetadata("villager_enraged_target").get(0).asString());
        } catch (IllegalArgumentException ex) {
            calmDownVillager(villager);
            return false;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || target.isDead()
                || target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR
                || target.getWorld() != villager.getWorld()
                || villager.getLocation().distanceSquared(target.getLocation()) > 3600.0) {
            calmDownVillager(villager);
            return false;
        }

        villager.getPathfinder().moveTo(target, 1.35);

        double distSq = villager.getLocation().distanceSquared(target.getLocation());
        long lastHit = villager.hasMetadata("villager_last_hit") ? villager.getMetadata("villager_last_hit").get(0).asLong() : 0L;
        if (distSq <= 7.29 && (now - lastHit) >= 750L) {
            villager.setMetadata("villager_last_hit", new FixedMetadataValue(plugin, now));
            villager.swingMainHand();

            int lv = Compat.parseEntityLevel(villager);
            double dmg = 6.0 + (lv * 1.2);
            target.damage(dmg, villager);

            // Stun & dizziness effects
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));

            Vector kb = Compat.safeDirection(villager.getLocation(), target.getLocation()).multiply(0.45).setY(0.25);
            target.setVelocity(kb);

            villager.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.05);
            villager.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
        }

        return true;
    }

    private void processMonsterDefense(Villager villager, long now) {
        long expire = villager.hasMetadata("villager_mob_expire")
                ? villager.getMetadata("villager_mob_expire").get(0).asLong()
                : 0L;
        if (now > expire) {
            clearMobTarget(villager);
            return;
        }

        UUID mobId;
        try {
            mobId = UUID.fromString(villager.getMetadata("villager_mob_target").get(0).asString());
        } catch (IllegalArgumentException ex) {
            clearMobTarget(villager);
            return;
        }

        Entity ent = Bukkit.getEntity(mobId);
        if (!(ent instanceof Monster target) || !target.isValid() || target.isDead()
                || target.getWorld() != villager.getWorld()
                || villager.getLocation().distanceSquared(target.getLocation()) > 900.0) {
            clearMobTarget(villager);
            return;
        }

        int lv = Compat.parseEntityLevel(villager);
        double distSq = villager.getLocation().distanceSquared(target.getLocation());
        long lastHit = villager.hasMetadata("villager_last_hit") ? villager.getMetadata("villager_last_hit").get(0).asLong() : 0L;
        String profKey = villager.getProfession().getKey().getKey();

        switch (profKey) {
            case "cleric" -> {
                if (distSq > 64.0) {
                    villager.getPathfinder().moveTo(target, 1.15);
                }
                long lastHeal = villager.hasMetadata("villager_cleric_heal_cd")
                        ? villager.getMetadata("villager_cleric_heal_cd").get(0).asLong()
                        : 0L;
                if (now - lastHeal >= 4000L) {
                    villager.setMetadata("villager_cleric_heal_cd", new FixedMetadataValue(plugin, now));
                    for (Entity near : villager.getNearbyEntities(12, 6, 12)) {
                        if (near instanceof Villager || near instanceof IronGolem) {
                            LivingEntity ally = (LivingEntity) near;
                            double allyMax = Compat.getAttributeValue(ally, Compat.MAX_HEALTH, 20.0);
                            ally.setHealth(Math.min(allyMax, ally.getHealth() + (allyMax * 0.15)));
                            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 1.8, 0), 4, 0.3, 0.3, 0.3, 0.05);
                        }
                    }
                }
                if (distSq <= 196.0 && (now - lastHit) >= 1200L) {
                    villager.setMetadata("villager_last_hit", new FixedMetadataValue(plugin, now));
                    villager.swingMainHand();
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.damage(7.0 + (lv * 1.4), villager);
                }
            }
            case "fletcher", "librarian" -> {
                if (distSq <= 324.0 && villager.hasLineOfSight(target)) {
                    if (now - lastHit >= 1000L) {
                        villager.setMetadata("villager_last_hit", new FixedMetadataValue(plugin, now));
                        villager.swingMainHand();
                        Arrow arrow = villager.launchProjectile(Arrow.class);
                        Vector dir = Compat.safeDirection(villager.getEyeLocation(), target.getEyeLocation());
                        arrow.setVelocity(dir.multiply(2.2));
                        arrow.setMetadata("villager_arrow", new FixedMetadataValue(plugin, lv));
                    }
                } else {
                    villager.getPathfinder().moveTo(target, 1.2);
                }
            }
            case "weaponsmith", "armorer", "toolsmith", "butcher" -> {
                villager.getPathfinder().moveTo(target, 1.25);
                if (distSq <= 7.29 && (now - lastHit) >= 800L) {
                    villager.setMetadata("villager_last_hit", new FixedMetadataValue(plugin, now));
                    villager.swingMainHand();
                    target.damage((6.0 + (lv * 1.4)) * 1.35, villager);
                    villager.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false, false));
                    target.setVelocity(Compat.safeDirection(villager.getLocation(), target.getLocation()).multiply(0.55).setY(0.25));
                }
            }
            default -> {
                villager.getPathfinder().moveTo(target, 1.2);
                if (distSq <= 6.76 && (now - lastHit) >= 850L) {
                    villager.setMetadata("villager_last_hit", new FixedMetadataValue(plugin, now));
                    villager.swingMainHand();
                    target.damage(6.0 + (lv * 1.4), villager);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                }
            }
        }
    }

    private void calmDownVillager(Villager villager) {
        villager.removeMetadata("villager_enraged_target", plugin);
        villager.removeMetadata("villager_enraged_expire", plugin);
        if (Compat.MOVEMENT_SPEED != null) {
            AttributeInstance speedAttr = villager.getAttribute(Compat.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.removeModifier(rageSpeedKey);
            }
        }
    }

    private void setMobTarget(Villager villager, Monster monster, long now) {
        villager.setMetadata("villager_mob_target", new FixedMetadataValue(plugin, monster.getUniqueId().toString()));
        villager.setMetadata("villager_mob_expire", new FixedMetadataValue(plugin, now + MOB_TARGET_DURATION_MS));
    }

    private void clearMobTarget(Villager villager) {
        villager.removeMetadata("villager_mob_target", plugin);
        villager.removeMetadata("villager_mob_expire", plugin);
    }

    private void trimDebuff(Villager villager, PotionEffectType type) {
        PotionEffect effect = villager.getPotionEffect(type);
        if (effect != null && effect.getDuration() > 40) {
            villager.removePotionEffect(type);
            villager.addPotionEffect(new PotionEffect(type, 40, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
    }

    private void notifyNearby(Villager villager, Player attacker, String message) {
        boolean attackerSent = false;
        for (Player p : villager.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(villager.getLocation()) <= 256.0) {
                p.sendMessage(message);
                if (p.equals(attacker)) attackerSent = true;
            }
        }
        if (!attackerSent && attacker != null && attacker.isOnline()) {
            attacker.sendMessage(message);
        }
    }

    private LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity le) {
            return le;
        }
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }
}
