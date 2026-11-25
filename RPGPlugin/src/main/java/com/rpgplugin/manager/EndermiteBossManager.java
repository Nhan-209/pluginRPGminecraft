package com.rpgplugin.manager;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.model.ItemQuality;
import com.rpgplugin.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EndermiteBossManager implements Listener {

    private static final String ROLE_KEY = "endermite_role";
    private static final String PHASE_KEY = "endermite_phase";

    private static final Material[] REWARD_GEAR = {
            Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_SWORD, Material.NETHERITE_CHESTPLATE, Material.BOW
    };

    private final RPGPlugin plugin;
    private final MobManager mobManager;
    private final ItemManager itemManager;

    public EndermiteBossManager(RPGPlugin plugin, MobManager mobManager, ItemManager itemManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.itemManager = itemManager;
        startFatherFlightTask();
    }

    public static String getRole(Entity entity) {
        if (entity == null || !entity.hasMetadata(ROLE_KEY)) return null;
        return entity.getMetadata(ROLE_KEY).get(0).asString();
    }

    public static int getPhase(Entity entity) {
        if (entity == null || !entity.hasMetadata(PHASE_KEY)) return 0;
        return entity.getMetadata(PHASE_KEY).get(0).asInt();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFatherEnvDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Endermite em)) return;
        if (!"father".equals(getRole(em))) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFatherHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity le) {
            attacker = le;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) {
            attacker = le;
        }

        if (attacker instanceof Endermite em && "father".equals(getRole(em))) {
            player.getWorld().strikeLightning(player.getLocation());
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }
    }

    @EventHandler
    public void onEndermiteDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Endermite em)) return;

        String role = getRole(em);
        Player killer = em.getKiller();
        Location deathLoc = em.getLocation().clone();

        if (role == null) {
            if (Math.random() < 0.50) {
                Player target = killer != null ? killer : findNearestPlayer(deathLoc, 32.0);
                spawnMotherEndermite(deathLoc, target);
            }
            return;
        }

        if ("mother".equals(role)) {
            Player target = killer != null ? killer : findNearestPlayer(deathLoc, 48.0);
            broadcastNearby(deathLoc, 64.0,
                    "§5§l[CẢNH BÁO] §cEndermite Mẹ đã ngã xuống! §5Endermite Bố §cvừa giáng lâm trả thù!");
            Location spawnLoc = deathLoc.clone().add(0, 3.0, 0);
            spawnFatherEndermite(spawnLoc, 1, target);
            return;
        }

        if ("father".equals(role)) {
            int phase = getPhase(em);
            if (phase == 1) {
                startTamKhiQuyLaiRevival(deathLoc, killer);
            } else if (phase == 2) {
                broadcastNearby(deathLoc, 64.0,
                        "§6§l[CHIẾN THẮNG] §aEndermite Bố (Tam Khí Quy Lai) đã bị tiêu diệt!");
                dropBossReward(deathLoc, killer);
            }
        }
    }

    private void spawnMotherEndermite(Location loc, Player target) {
        int motherLv = mobManager.getHighestPlayerLevel() + 2;
        Endermite mother = loc.getWorld().spawn(loc, Endermite.class, pre -> {
            pre.setMetadata(ROLE_KEY, new FixedMetadataValue(plugin, "mother"));
            pre.setMetadata("is_minion", new FixedMetadataValue(plugin, true));
            pre.setMetadata("is_mini_boss", new FixedMetadataValue(plugin, true));
        });

        mobManager.applyMobLevel(mother, motherLv, true);
        Compat.setAttributeBase(mother, Compat.SCALE, 2.0);
        Compat.setAttributeBase(mother, Compat.MOVEMENT_SPEED, 0.38);
        Compat.setAttributeBase(mother, Compat.FOLLOW_RANGE, 80.0);

        mother.setCustomName("§d§l[Lv. " + motherLv + "] Endermite Mẹ");
        mother.setCustomNameVisible(true);
        if (target != null && target.isOnline() && !target.isDead()) {
            mother.setTarget(target);
        }

        broadcastNearby(loc, 48.0,
                "§d§l[CẢNH BÁO] §5Một con Endermite vừa gọi MẸ ra trả thù! §7(Lv." + motherLv + ")");
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_SCREAM, 1.2f, 0.7f);
    }

    public void spawnFatherEndermite(Location loc, int phase, Player initialTarget) {
        int highest = mobManager.getHighestPlayerLevel();
        int lv = (phase == 2) ? (highest + 10) : (highest + 5);

        Endermite father = loc.getWorld().spawn(loc, Endermite.class, pre -> {
            pre.setMetadata(ROLE_KEY, new FixedMetadataValue(plugin, "father"));
            pre.setMetadata(PHASE_KEY, new FixedMetadataValue(plugin, phase));
            pre.setMetadata("is_minion", new FixedMetadataValue(plugin, true));
            pre.setMetadata("is_mini_boss", new FixedMetadataValue(plugin, true));
        });

        mobManager.applyMobLevel(father, lv, true);
        Compat.setAttributeBase(father, Compat.SCALE, 4.0);
        Compat.setAttributeBase(father, Compat.FOLLOW_RANGE, 100.0);

        if (phase == 2) {
            double maxHp = Compat.getAttributeBase(father, Compat.MAX_HEALTH, 20.0) * 2.0;
            Compat.setAttributeBase(father, Compat.MAX_HEALTH, maxHp);
            father.setHealth(maxHp);

            if (Compat.ATTACK_DAMAGE != null && father.getAttribute(Compat.ATTACK_DAMAGE) != null) {
                double dmg = Compat.getAttributeBase(father, Compat.ATTACK_DAMAGE, 4.0) * 2.0;
                Compat.setAttributeBase(father, Compat.ATTACK_DAMAGE, dmg);
            }
        }

        father.setGravity(false);
        father.setRemoveWhenFarAway(false);

        String name = (phase == 2)
                ? "§4§l[Lv. " + lv + "] Endermite Bố (Tam Khí Quy Lai)"
                : "§5§l[Lv. " + lv + "] Endermite Bố";
        father.setCustomName(name);
        father.setCustomNameVisible(true);

        long now = System.currentTimeMillis();
        father.setMetadata("father_state", new FixedMetadataValue(plugin, "ORBIT"));
        father.setMetadata("father_angle", new FixedMetadataValue(plugin, Math.random() * Math.PI * 2.0));
        father.setMetadata("father_next_swoop", new FixedMetadataValue(plugin, now + 2000L));
        father.setMetadata("father_next_aura", new FixedMetadataValue(plugin, now + (phase == 2 ? 5000L : 8000L)));
        if (initialTarget != null) {
            father.setMetadata("father_target", new FixedMetadataValue(plugin, initialTarget.getUniqueId().toString()));
            father.setTarget(initialTarget);
        }
    }

    private void startTamKhiQuyLaiRevival(Location deathLoc, Player killer) {
        World world = deathLoc.getWorld();
        int groundY = world.getHighestBlockYAt(deathLoc);
        double airY = Math.max(deathLoc.getY() + 4.0, Math.min(groundY + 7.0, deathLoc.getY() + 6.0));
        Location reviveLoc = new Location(world, deathLoc.getX(), airY, deathLoc.getZ());
        UUID targetUuid = killer != null ? killer.getUniqueId() : null;

        broadcastNearby(reviveLoc, 64.0, "§c§l[Endermite Bố] §f\"Ta không thể thua\"");

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                broadcastNearby(reviveLoc, 64.0, "§c§l[Endermite Bố] §f\"Gia đình vẫn cần ta trả thù\""), 25L);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                broadcastNearby(reviveLoc, 64.0, "§c§l[Endermite Bố] §f\"Thế giới này vẫn cần ta\""), 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastNearby(reviveLoc, 64.0, "§5§l[Endermite Bố] §e§l\"TAM KHÍ QUY LAI!\"");
            world.playSound(reviveLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
        }, 75L);

        // Converging particles in midair from 50L to 105L
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step > 18) {
                    cancel();
                    return;
                }
                double progress = step / 18.0;
                double radius = Math.max(0.5, 10.0 * (1.0 - progress));
                for (int i = 0; i < 24; i++) {
                    double theta = Math.random() * Math.PI * 2.0;
                    double phi = Math.acos(2.0 * Math.random() - 1.0);
                    double ox = radius * Math.sin(phi) * Math.cos(theta);
                    double oy = radius * Math.cos(phi);
                    double oz = radius * Math.sin(phi) * Math.sin(theta);
                    Location pLoc = reviveLoc.clone().add(ox, oy, oz);
                    world.spawnParticle(Particle.PORTAL, pLoc, 2, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 1, 0, 0, 0, 0.01);
                    if (step % 2 == 0) {
                        world.spawnParticle(Particle.FLAME, pLoc, 1, 0, 0, 0, 0.01);
                        world.spawnParticle(Particle.END_ROD, pLoc, 1, 0, 0, 0, 0.01);
                    }
                }
                step++;
            }
        }.runTaskTimer(plugin, 50L, 3L);

        // Spawn Phase 2 at 105L
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.strikeLightningEffect(reviveLoc);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, reviveLoc, 2, 0.5, 0.5, 0.5, 0.0);
            Player target = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;
            if (target == null || !target.isOnline() || target.isDead()) {
                target = findNearestPlayer(reviveLoc, 64.0);
            }
            spawnFatherEndermite(reviveLoc, 2, target);
        }, 105L);
    }

    private void startFatherFlightTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (World world : Bukkit.getWorlds()) {
                if (world.getPlayers().isEmpty()) continue;

                for (Endermite em : world.getEntitiesByClass(Endermite.class)) {
                    if (!em.isValid() || em.isDead() || !"father".equals(getRole(em))) continue;

                    int phase = Math.max(1, getPhase(em));
                    Player target = resolveFatherTarget(em);
                    if (target == null) {
                        em.setVelocity(new Vector(0, 0, 0));
                        continue;
                    }

                    long nextAura = em.hasMetadata("father_next_aura")
                            ? em.getMetadata("father_next_aura").get(0).asLong()
                            : now + 8000L;
                    if (now >= nextAura) {
                        em.setMetadata("father_next_aura",
                                new FixedMetadataValue(plugin, now + (phase == 2 ? 7000L : 10000L)));
                        triggerAuraQuyXuong(em, phase);
                    }

                    Location emLoc = em.getLocation();
                    Location pLoc = target.getLocation();

                    em.getWorld().spawnParticle(Particle.PORTAL, emLoc.clone().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0.05);
                    if (phase == 2) {
                        em.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, emLoc.clone().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.02);
                    }

                    String state = em.hasMetadata("father_state")
                            ? em.getMetadata("father_state").get(0).asString()
                            : "ORBIT";
                    long stateExpire = em.hasMetadata("father_state_expire")
                            ? em.getMetadata("father_state_expire").get(0).asLong()
                            : 0L;

                    switch (state) {
                        case "SWOOP" -> {
                            Location aimLoc = pLoc.clone().add(0, 1.0, 0);
                            double swoopSpeed = (phase == 2) ? 1.85 : 1.50;
                            Vector dir = Compat.safeDirection(emLoc, aimLoc);
                            em.setVelocity(dir.multiply(swoopSpeed));

                            if (emLoc.distanceSquared(aimLoc) <= 10.24) {
                                double dmg = Compat.getAttributeValue(em, Compat.ATTACK_DAMAGE, 12.0);
                                target.damage(dmg, em);
                                em.setMetadata("father_state", new FixedMetadataValue(plugin, "ASCEND"));
                                em.setMetadata("father_state_expire", new FixedMetadataValue(plugin, now + 1100L));
                                em.setMetadata("father_next_swoop",
                                        new FixedMetadataValue(plugin, now + (phase == 2 ? 2200L : 3200L)));
                            } else if (now >= stateExpire) {
                                em.setMetadata("father_state", new FixedMetadataValue(plugin, "ASCEND"));
                                em.setMetadata("father_state_expire", new FixedMetadataValue(plugin, now + 900L));
                                em.setMetadata("father_next_swoop",
                                        new FixedMetadataValue(plugin, now + (phase == 2 ? 2200L : 3200L)));
                            }
                        }
                        case "ASCEND" -> {
                            Vector away = Compat.safeDirection(pLoc, emLoc).setY(0);
                            if (away.lengthSquared() < 1e-6) {
                                away = new Vector(1, 0, 0);
                            }
                            em.setVelocity(away.normalize().multiply(1.1).setY(0.85));
                            if (now >= stateExpire) {
                                em.setMetadata("father_state", new FixedMetadataValue(plugin, "ORBIT"));
                            }
                        }
                        default -> { // ORBIT
                            double angle = em.hasMetadata("father_angle")
                                    ? em.getMetadata("father_angle").get(0).asDouble()
                                    : 0.0;
                            angle += (phase == 2) ? 0.24 : 0.18;
                            em.setMetadata("father_angle", new FixedMetadataValue(plugin, angle));

                            double radius = 9.5;
                            double desiredX = pLoc.getX() + Math.cos(angle) * radius;
                            double desiredY = pLoc.getY() + 7.5 + Math.sin(angle * 2.0) * 2.0;
                            double desiredZ = pLoc.getZ() + Math.sin(angle) * radius;
                            Location desired = new Location(world, desiredX, desiredY, desiredZ);

                            double orbitSpeed = (phase == 2) ? 1.15 : 0.90;
                            Vector dir = Compat.safeDirection(emLoc, desired);
                            double dist = emLoc.distance(desired);
                            em.setVelocity(dir.multiply(Math.min(orbitSpeed, Math.max(0.25, dist * 0.45))));

                            long nextSwoop = em.hasMetadata("father_next_swoop")
                                    ? em.getMetadata("father_next_swoop").get(0).asLong()
                                    : now + 2000L;
                            if (now >= nextSwoop) {
                                em.setMetadata("father_state", new FixedMetadataValue(plugin, "SWOOP"));
                                em.setMetadata("father_state_expire", new FixedMetadataValue(plugin, now + 2200L));
                                world.playSound(emLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.4f);
                            }
                        }
                    }
                }
            }
        }, 2L, 2L);
    }

    private void triggerAuraQuyXuong(Endermite father, int phase) {
        Location center = father.getLocation().clone();
        World world = center.getWorld();
        broadcastNearby(center, 36.0, "§5§l[Endermite Bố] §d✦ AURA QUỲ XUỐNG! ✦");
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 0.9f);

        Set<UUID> hitPlayers = new HashSet<>();
        new BukkitRunnable() {
            int tick = 1;

            @Override
            public void run() {
                if (tick > 22) {
                    cancel();
                    return;
                }
                double radius = tick * 1.0;
                int points = 36;
                for (int i = 0; i < points; i++) {
                    double a = (2.0 * Math.PI * i) / points;
                    double ox = Math.cos(a) * radius;
                    double oz = Math.sin(a) * radius;

                    Location ringHigh = center.clone().add(ox, 0, oz);
                    Location ringGround = new Location(world, center.getX() + ox,
                            world.getHighestBlockYAt((int) Math.floor(center.getX() + ox), (int) Math.floor(center.getZ() + oz)) + 0.5,
                            center.getZ() + oz);

                    world.spawnParticle(Particle.WITCH, ringHigh, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.PORTAL, ringGround, 2, 0.05, 0.05, 0.05, 0.02);
                    if (tick % 2 == 0) {
                        world.spawnParticle(Particle.END_ROD, ringGround, 1, 0, 0, 0, 0);
                    }
                }

                for (Player p : world.getPlayers()) {
                    if (p.isDead()) continue;
                    if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                    if (hitPlayers.contains(p.getUniqueId())) continue;

                    Location pLoc = p.getLocation();
                    double dx = pLoc.getX() - center.getX();
                    double dz = pLoc.getZ() - center.getZ();
                    double horizDist = Math.sqrt(dx * dx + dz * dz);
                    if (horizDist <= radius + 1.5 && Math.abs(pLoc.getY() - center.getY()) <= 30.0) {
                        hitPlayers.add(p.getUniqueId());

                        p.setFlying(false);
                        p.setGliding(false);
                        p.setAllowFlight(false);
                        p.removeMetadata("rpg_flight_granted", plugin);
                        p.setMetadata("rpg_grounded_until",
                                new FixedMetadataValue(plugin, System.currentTimeMillis() + 2500L));
                        p.setVelocity(new Vector(0, -2.5, 0));

                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0));

                        world.strikeLightning(pLoc);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (p.isOnline() && !p.isDead() && p.getWorld() == world) {
                                world.strikeLightning(p.getLocation());
                            }
                        }, 6L);

                        p.sendMessage("§5§l[AURA QUỲ XUỐNG] §cÁp lực kinh hoàng ép bạn quỳ xuống mặt đất!");
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void dropBossReward(Location loc, Player killer) {
        Material mat = REWARD_GEAR[(int) (Math.random() * REWARD_GEAR.length)];
        ItemQuality[] pool = {ItemQuality.STAR_5, ItemQuality.STAR_6, ItemQuality.STAR_7, ItemQuality.STAR_8};
        ItemQuality quality = pool[(int) (Math.random() * pool.length)];
        ItemStack reward = itemManager.applyQuality(new ItemStack(mat, 1), quality);
        loc.getWorld().dropItemNaturally(loc, reward);

        if (killer != null && killer.isOnline()) {
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
                        killer.sendMessage("§a§lVũ khí của bạn đã được nâng cấp thêm 1 sao sau khi hạ gục Endermite Bố!");
                        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private Player resolveFatherTarget(Endermite father) {
        if (father.hasMetadata("father_target")) {
            try {
                UUID uid = UUID.fromString(father.getMetadata("father_target").get(0).asString());
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.isOnline() && !p.isDead()
                        && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                        && p.getWorld() == father.getWorld()
                        && p.getLocation().distanceSquared(father.getLocation()) <= 4096.0) {
                    return p;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        Player nearest = findNearestPlayer(father.getLocation(), 48.0);
        if (nearest != null) {
            father.setMetadata("father_target", new FixedMetadataValue(plugin, nearest.getUniqueId().toString()));
        }
        return nearest;
    }

    private Player findNearestPlayer(Location loc, double radius) {
        double bestSq = radius * radius;
        Player best = null;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.isDead()) continue;
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            double dSq = p.getLocation().distanceSquared(loc);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = p;
            }
        }
        return best;
    }

    private void broadcastNearby(Location loc, double radius, String message) {
        double rSq = radius * radius;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rSq) {
                p.sendMessage(message);
            }
        }
    }
}
