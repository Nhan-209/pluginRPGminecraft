package com.rpgplugin;

import com.rpgplugin.command.AdminCommand;
import com.rpgplugin.command.ItemEffectCommand;
import com.rpgplugin.command.RPGCommand;
import com.rpgplugin.gui.StatsGUI;
import com.rpgplugin.listener.BossMechanicsListener;
import com.rpgplugin.listener.CombatListener;
import com.rpgplugin.listener.EnchantListener;
import com.rpgplugin.listener.InventoryListener;
import com.rpgplugin.listener.ItemListener;
import com.rpgplugin.listener.MobListener;
import com.rpgplugin.listener.PassiveMobListener;
import com.rpgplugin.listener.PlayerListener;
import com.rpgplugin.listener.PlayerSkillListener;
import com.rpgplugin.listener.ToolEffectListener;
import com.rpgplugin.listener.VillagerListener;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.manager.MobManager;
import com.rpgplugin.manager.PlayerDataManager;
import com.rpgplugin.manager.RandomEventManager;
import com.rpgplugin.manager.StackManager;
import com.rpgplugin.manager.VillagerCombatManager;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Warden;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Wither;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RPGPlugin extends JavaPlugin {

    private static RPGPlugin instance;
    private Logger log;

    private PlayerDataManager playerDataManager;
    private ItemManager itemManager;
    private MobManager mobManager;
    private VillagerCombatManager villagerCombatManager;
    private PlayerSkillListener playerSkillListener;

    public static final Set<LivingEntity> trueInvisibleEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void applyTrueInvisibility(LivingEntity entity, long durationMs) {
        long expire = System.currentTimeMillis() + durationMs;
        RPGPlugin inst = RPGPlugin.getInstance();
        entity.setMetadata("true_invisibility", new FixedMetadataValue(inst, expire));
        trueInvisibleEntities.add(entity);
        if (inst != null) {
            for (Player p : inst.getServer().getOnlinePlayers()) {
                if (!p.equals(entity)) {
                    p.hideEntity(inst, entity);
                }
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        log = getLogger();
        log.info("RPGPlugin has been enabled!");

        // Initialize managers
        playerDataManager = new PlayerDataManager(this);
        itemManager = new ItemManager(this);
        mobManager = new MobManager(this);
        villagerCombatManager = new VillagerCombatManager(this);
        mobManager.setVillagerCombatManager(villagerCombatManager);

        StatsGUI statsGUI = new StatsGUI(playerDataManager);
        playerSkillListener = new PlayerSkillListener(this, playerDataManager);
        StackManager stackManager = new StackManager();

        // Register commands
        if (getCommand("rpg") != null) {
            getCommand("rpg").setExecutor(new RPGCommand(statsGUI));
        }
        if (getCommand("adminrpg") != null) {
            getCommand("adminrpg").setExecutor(new AdminCommand());
        }
        ItemEffectCommand effectCmd = new ItemEffectCommand(this);
        if (getCommand("rpgfly") != null) getCommand("rpgfly").setExecutor(effectCmd);
        if (getCommand("rpginf") != null) getCommand("rpginf").setExecutor(effectCmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this, playerDataManager), this);
        getServer().getPluginManager().registerEvents(new ItemListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new MobListener(mobManager, itemManager), this);
        getServer().getPluginManager().registerEvents(statsGUI, this);
        getServer().getPluginManager().registerEvents(new CombatListener(itemManager, playerDataManager, playerSkillListener), this);
        getServer().getPluginManager().registerEvents(new BossMechanicsListener(), this);
        getServer().getPluginManager().registerEvents(new EnchantListener(), this);
        getServer().getPluginManager().registerEvents(new VillagerListener(itemManager), this);
        getServer().getPluginManager().registerEvents(new PassiveMobListener(), this);
        getServer().getPluginManager().registerEvents(stackManager, this);
        getServer().getPluginManager().registerEvents(new InventoryListener(itemManager, stackManager), this);
        getServer().getPluginManager().registerEvents(villagerCombatManager, this);
        getServer().getPluginManager().registerEvents(playerSkillListener, this);
        getServer().getPluginManager().registerEvents(new ToolEffectListener(this, itemManager, playerDataManager), this);

        new RandomEventManager(this);

        // Load data for any already-online players (e.g., on /reload)
        for (Player p : getServer().getOnlinePlayers()) {
            playerDataManager.loadPlayerData(p);
        }

        // 1. Auto-save task (every 5 minutes = 6000 ticks)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            playerDataManager.saveAll();
        }, 6000L, 6000L);

        // 2. Action Bar task for numeric health, level, XP%, and active skill cooldowns (every 20 ticks = 1s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                int hp = (int) Math.ceil(p.getHealth());
                int maxHp = (int) Compat.getAttributeValue(p, Compat.MAX_HEALTH, 20.0);
                PlayerData pd = playerDataManager.getPlayerData(p.getUniqueId());
                int level = pd != null ? pd.getLevel() : 1;
                double xpPct = (pd != null && pd.getRequiredExperience() > 0)
                        ? Math.min(99.9, (pd.getExperience() * 100.0) / pd.getRequiredExperience())
                        : 0.0;
                String skillBar = playerSkillListener.getCooldownStatus(p.getUniqueId());
                p.sendActionBar("§c❤ " + hp + "/" + maxHp + "  §a⭐ Lv." + level
                        + " (" + String.format("%.0f", xpPct) + "%)  " + skillBar);
            }
        }, 20L, 20L);

        // 3. Vitality Passive HP Regeneration (every 40 ticks = 2s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                if (p.isDead() || p.getHealth() <= 0) continue;
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                PlayerData pd = playerDataManager.getPlayerData(p.getUniqueId());
                if (pd == null || pd.getVitalityStats() <= 0) continue;

                double maxHp = Compat.getAttributeValue(p, Compat.MAX_HEALTH, 20.0);
                if (p.getHealth() < maxHp) {
                    double regen = pd.getRegenPer2Sec(maxHp);
                    p.setHealth(Math.min(maxHp, p.getHealth() + regen));
                }
            }
        }, 40L, 40L);

        // 4. Aggressive AI & Entity Level Scaling Task (every 20 ticks = 1s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (World world : getServer().getWorlds()) {
                for (Player player : world.getPlayers()) {
                    if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;

                    for (Entity entity : player.getNearbyEntities(40, 40, 40)) {
                        if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                            if (living.getCustomName() == null || !living.getCustomName().contains("[Lv.")) {
                                if (living instanceof Wither || living instanceof EnderDragon) {
                                    mobManager.scaleBoss(living);
                                } else if (!living.hasMetadata("is_minion") && !living.hasMetadata("pet_owner")) {
                                    mobManager.scaleMob(living);
                                }
                            }
                        }

                        if (entity instanceof Monster monster) {
                            if (monster instanceof Enderman && world.getEnvironment() == World.Environment.THE_END) {
                                if (monster.getTarget() == null) {
                                    monster.setTarget(player);
                                }
                            }

                            if (monster instanceof Phantom && monster.hasMetadata("is_minion")) {
                                if (monster.getTarget() == null || monster.getTarget().isDead()) {
                                    monster.setTarget(player);
                                }
                            }

                            if (monster instanceof Blaze && monster.getTarget() != null && monster.getTarget().equals(player)) {
                                if (Math.random() < 0.1) {
                                    Block block = player.getLocation().getBlock();
                                    if (block.getType() == Material.AIR || block.getType() == Material.SHORT_GRASS || block.getType() == Material.TALL_GRASS) {
                                        block.setType(Material.LAVA);
                                        getServer().getScheduler().runTaskLater(this, () -> {
                                            if (block.getType() == Material.LAVA) {
                                                block.setType(Material.AIR);
                                            }
                                        }, 60L);
                                    }
                                }
                            }
                        } else if (entity instanceof Animals animal) {
                            String name = animal.getCustomName();
                            if (name != null && name.contains("[Lv.")) {
                                int mobLevel = Compat.parseEntityLevel(animal);
                                PlayerData pData = playerDataManager.getPlayerData(player.getUniqueId());
                                int playerLevel = pData != null ? pData.getLevel() : 1;

                                if (mobLevel >= playerLevel - 2) {
                                    if (animal.getTarget() == null && animal.getLocation().distanceSquared(player.getLocation()) < 100.0) {
                                        animal.setTarget(player);
                                    }
                                }

                                if (animal.getTarget() != null && animal.getTarget().equals(player)) {
                                    double distSq = animal.getLocation().distanceSquared(player.getLocation());
                                    if (distSq < 9.0) {
                                        double dmg = 2.0 + (mobLevel * 0.5);
                                        player.damage(dmg, animal);
                                        Vector dir = Compat.safeDirection(animal.getLocation(), player.getLocation());
                                        animal.setVelocity(dir.multiply(0.5).setY(0.3));
                                    } else if (distSq < 256.0) {
                                        ((Mob) animal).getPathfinder().moveTo(player, 1.3);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }, 20L, 20L);

        // 5. Skeleton, Breeze & Pet Follow Task (every 7 ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (World world : getServer().getWorlds()) {
                if (world.getPlayers().isEmpty()) continue;

                for (Skeleton skeleton : world.getEntitiesByClass(Skeleton.class)) {
                    LivingEntity target = skeleton.getTarget();
                    if (target != null) {
                        double distSq = skeleton.getLocation().distanceSquared(target.getLocation());
                        if (distSq < 400.0 && skeleton.hasLineOfSight(target)) {
                            Arrow arrow = skeleton.launchProjectile(Arrow.class);
                            Vector dir = Compat.safeDirection(skeleton.getEyeLocation(), target.getEyeLocation());
                            arrow.setVelocity(dir.multiply(2.5));
                            arrow.setMetadata("skeleton_fast", new FixedMetadataValue(this, true));

                            RPGPlugin.applyTrueInvisibility(skeleton, 1000);
                            skeleton.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0));

                            if (distSq < 64.0) {
                                Vector away = Compat.safeDirection(target.getLocation(), skeleton.getLocation());
                                skeleton.setVelocity(away.multiply(0.4).setY(0.2));
                            }
                        }
                    }
                }

                for (IronGolem golem : world.getEntitiesByClass(IronGolem.class)) {
                    handlePetFollow(golem);
                }

                for (Warden warden : world.getEntitiesByClass(Warden.class)) {
                    handlePetFollow(warden);
                }

                for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                    if (mob.getType().name().equals("BREEZE")) {
                        if (mob.getTarget() instanceof Player p) {
                            if (mob.getLocation().distanceSquared(p.getLocation()) < 400.0 && mob.hasLineOfSight(p)) {
                                WindCharge charge = mob.launchProjectile(WindCharge.class);
                                Vector dir = Compat.safeDirection(mob.getEyeLocation(), p.getEyeLocation());
                                charge.setVelocity(dir.multiply(2.0));
                            }
                        }
                    }
                }
            }
        }, 7L, 7L);

        // 6. True Invisibility Task (every 10 ticks = 0.5s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            Iterator<LivingEntity> iterator = trueInvisibleEntities.iterator();
            while (iterator.hasNext()) {
                LivingEntity e = iterator.next();
                if (e.isDead() || !e.isValid()) {
                    iterator.remove();
                    continue;
                }

                if (e.hasMetadata("true_invisibility")) {
                    long expire = e.getMetadata("true_invisibility").get(0).asLong();
                    if (System.currentTimeMillis() > expire) {
                        e.removeMetadata("true_invisibility", this);
                        iterator.remove();
                        for (Player p : getServer().getOnlinePlayers()) {
                            p.showEntity(this, e);
                        }
                    } else {
                        for (Player p : getServer().getOnlinePlayers()) {
                            if (!p.equals(e)) {
                                p.hideEntity(this, e);
                            }
                        }
                    }
                } else {
                    iterator.remove();
                }
            }
        }, 10L, 10L);

        // 7. Unified Item Passive Effects, Flight & Infinite Refill Backup Task (every 10 ticks = 0.5s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                boolean hasFlightItem = false;
                boolean hasNightVision = false;
                boolean hasLightweight = false;
                boolean hasFeatherweight = false;
                boolean hasWaterBreathing = false;
                boolean hasJumpBoost = false;

                List<ItemStack> itemsToCheck = new ArrayList<>();
                for (ItemStack i : player.getInventory().getContents()) {
                    itemsToCheck.add(i);
                }
                itemsToCheck.add(player.getItemOnCursor());

                for (ItemStack item : itemsToCheck) {
                    if (item == null || item.getType() == Material.AIR) continue;

                    if (itemManager.hasFlight(item)) {
                        hasFlightItem = true;
                    }
                    if (item.getType().getMaxDurability() == 0 && itemManager.hasInfinite(item) && item.getAmount() < 99) {
                        Compat.setMaxStackSizeSafe(item, 99);
                        item.setAmount(99);
                    }
                }

                if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                    if (hasFlightItem && !player.hasMetadata("rpg_flight_granted")) {
                        player.setMetadata("rpg_flight_granted", new FixedMetadataValue(this, true));
                        player.setAllowFlight(true);
                    } else if (!hasFlightItem && player.hasMetadata("rpg_flight_granted")) {
                        player.removeMetadata("rpg_flight_granted", this);
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                }

                ItemStack[] equipment = new ItemStack[6];
                System.arraycopy(player.getInventory().getArmorContents(), 0, equipment, 0, 4);
                equipment[4] = player.getInventory().getItemInMainHand();
                equipment[5] = player.getInventory().getItemInOffHand();

                for (int i = 0; i < equipment.length; i++) {
                    ItemStack eq = equipment[i];
                    if (eq == null || eq.getType() == Material.AIR) continue;

                    if (itemManager.hasEffect(eq, "NightVision")) {
                        hasNightVision = true;
                    }
                    if (i >= 4) { // Hands (Tools/Weapons)
                        if (itemManager.hasEffect(eq, "Lightweight") || itemManager.hasEffect(eq, "Haste")) {
                            hasLightweight = true;
                        }
                    } else { // Armor slots
                        if (itemManager.hasEffect(eq, "Featherweight")) hasFeatherweight = true;
                        if (itemManager.hasEffect(eq, "WaterBreathing")) hasWaterBreathing = true;
                        if (itemManager.hasEffect(eq, "JumpBoost")) hasJumpBoost = true;
                    }
                }

                if (hasNightVision) {
                    PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
                    if (current == null || current.getDuration() <= 300) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, false));
                        player.setMetadata("rpg_nv_granted", new FixedMetadataValue(this, true));
                    }
                } else if (player.hasMetadata("rpg_nv_granted")) {
                    player.removeMetadata("rpg_nv_granted", this);
                    PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
                    if (current != null && current.getDuration() <= 300) {
                        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    }
                }

                if (hasLightweight) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 1, false, false, false));
                }
                if (hasFeatherweight) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false, false));
                }
                if (hasWaterBreathing) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, false, false, false));
                }
                if (hasJumpBoost) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1, false, false, false));
                }
            }
        }, 10L, 10L);

        // 8. Boss Skeleton Raid Task (every 20 ticks = 1s)
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                tickCounter++;
                for (World world : getServer().getWorlds()) {
                    for (EnderDragon e : world.getEntitiesByClass(EnderDragon.class)) {
                        if (e.getWorld().getEnvironment() == World.Environment.THE_END && tickCounter % 100 == 0) {
                            triggerSkeletonRaid(e, 8);
                        }
                    }
                    for (Wither e : world.getEntitiesByClass(Wither.class)) {
                        if (!e.hasMetadata("is_minion") && tickCounter % 200 == 0) {
                            triggerSkeletonRaid(e, 12);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    private void handlePetFollow(LivingEntity pet) {
        if (!pet.hasMetadata("pet_owner")) return;
        String ownerName = pet.getMetadata("pet_owner").get(0).asString();
        Player owner = getServer().getPlayerExact(ownerName);
        if (owner != null && owner.isOnline() && !owner.isDead()) {
            if (pet.getWorld() != owner.getWorld() || pet.getLocation().distanceSquared(owner.getLocation()) > 169.0) {
                pet.teleport(owner.getLocation());
            }
        } else {
            pet.remove();
        }
    }

    private void triggerSkeletonRaid(LivingEntity boss, int amount) {
        Location loc = boss.getLocation();
        boss.getWorld().strikeLightningEffect(loc);
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 10000.0) {
                p.sendMessage("§c§l[CẢNH BÁO] §e" + boss.getName() + " §cvừa triệu hồi một đội quân Skeleton tập kích!");
            }
        }

        for (int i = 0; i < amount; i++) {
            double offsetX = (Math.random() - 0.5) * 20;
            double offsetZ = (Math.random() - 0.5) * 20;
            Location spawnLoc = loc.clone().add(offsetX, 0, offsetZ);
            spawnLoc.setY(boss.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            Skeleton skeleton = boss.getWorld().spawn(spawnLoc, Skeleton.class);
            skeleton.setCustomName("§c§lMinion of " + boss.getName());
            skeleton.setCustomNameVisible(true);
            skeleton.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9999, 1));
            mobManager.scaleMob(skeleton);
        }
    }

    @Override
    public void onDisable() {
        log.info("RPGPlugin has been disabled!");
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
    }

    public static RPGPlugin getInstance() {
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    public VillagerCombatManager getVillagerCombatManager() {
        return villagerCombatManager;
    }

    public PlayerSkillListener getPlayerSkillListener() {
        return playerSkillListener;
    }
}
