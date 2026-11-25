package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.manager.EndermiteBossManager;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.manager.PlayerDataManager;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private static final int MAX_PLACED_TRACKED = 4096;

    private final RPGPlugin plugin;
    private final PlayerDataManager dataManager;

    private final Set<String> playerPlacedBlocks = Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>(MAX_PLACED_TRACKED + 1, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_PLACED_TRACKED;
                }
            }
    );

    private final Set<UUID> modifyingEffects = new HashSet<>();

    public PlayerListener(RPGPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        dataManager.loadPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        dataManager.removePlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        if (isOre(type) || isLog(type) || isCropBlock(type)) {
            playerPlacedBlocks.add(blockKey(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Block block = event.getBlock();
        if (playerPlacedBlocks.remove(blockKey(block))) {
            return;
        }

        PlayerData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        long baseXp = calculateBlockXp(block);
        if (baseXp <= 0) return;

        double mult = data.getMiningXpMultiplier();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (plugin.getItemManager().hasEffect(tool, "ExpBoost")) {
            mult *= 1.5;
        }

        long finalXp = Math.max(1L, Math.round(baseXp * mult));
        dataManager.addExperience(player, finalXp);
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        int mobLevel = Compat.parseEntityLevel(entity);
        double maxHp = Compat.getAttributeValue(entity, Compat.MAX_HEALTH, 20.0);
        double baseXp = Math.max(5.0, (mobLevel * 8.0) + (maxHp * 0.5));

        int mult = 1;
        boolean isEndermiteFatherP2 = "father".equals(EndermiteBossManager.getRole(entity))
                && EndermiteBossManager.getPhase(entity) == 2;
        if (isEndermiteFatherP2 || entity instanceof Wither || entity instanceof EnderDragon
                || (entity instanceof Warden && !entity.hasMetadata("is_minion") && !entity.hasMetadata("pet_owner"))) {
            mult = 8;
        } else if (entity.hasMetadata("is_mini_boss") || entity.hasMetadata("is_mini_boss_bear")) {
            mult = 3;
        }

        BigInteger xp = BigDecimal.valueOf(baseXp).multiply(BigDecimal.valueOf(mult)).toBigInteger();
        dataManager.addExperience(killer, xp);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            PlayerData data = dataManager.getPlayerData(player.getUniqueId());
            if (data != null) {
                double baseDamage = event.getDamage();
                event.setDamage((baseDamage + data.getFlatDamageBonus()) * data.getDamageMultiplier());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Defense attribute & RPG DR% handled in PlayerDataManager & CombatListener
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }

        PotionEffect newEff = event.getNewEffect();
        if (newEff == null || !isNegativeEffect(newEff.getType())) return;
        if (newEff.getDuration() <= 20 || newEff.getDuration() >= Integer.MAX_VALUE / 2) return;

        PlayerData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null || data.getDebuffReduction() <= 0) return;

        UUID pid = player.getUniqueId();
        if (!modifyingEffects.add(pid)) return;
        try {
            int reducedDuration = Math.max(10, (int) Math.round(newEff.getDuration() * (1.0 - data.getDebuffReduction())));
            if (reducedDuration < newEff.getDuration()) {
                event.setCancelled(true);
                PotionEffect adjusted = new PotionEffect(
                        newEff.getType(),
                        reducedDuration,
                        newEff.getAmplifier(),
                        newEff.isAmbient(),
                        newEff.hasParticles(),
                        newEff.hasIcon()
                );
                player.addPotionEffect(adjusted);
            }
        } finally {
            modifyingEffects.remove(pid);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = dataManager.getPlayerData(player.getUniqueId());
            if (data != null && event.getFoodLevel() < player.getFoodLevel()) {
                double chanceToSave = data.getHungerSaveChance();
                if (Math.random() < chanceToSave) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) return;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        boolean wasOffHand = offHand.getType() == Material.TOTEM_OF_UNDYING;
        ItemStack activeTotem = wasOffHand ? offHand : (mainHand.getType() == Material.TOTEM_OF_UNDYING ? mainHand : null);

        if (activeTotem != null) {
            player.getInventory().addItem(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));

            ItemManager itemManager = plugin.getItemManager();
            boolean isGodTotem = activeTotem.hasItemMeta() && (
                    activeTotem.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "totem_god"), PersistentDataType.BYTE)
                            || activeTotem.getItemMeta().getPersistentDataContainer().has(itemManager.INFINITE_KEY, PersistentDataType.BYTE)
            );

            if (isGodTotem) {
                ItemStack godTotem = activeTotem.clone();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (wasOffHand) {
                        player.getInventory().setItemInOffHand(godTotem);
                    } else {
                        player.getInventory().setItemInMainHand(godTotem);
                    }
                }, 1L);
            }
        }
    }

    private long calculateBlockXp(Block block) {
        Material type = block.getType();
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE
                || type == Material.EMERALD_ORE || type == Material.DEEPSLATE_EMERALD_ORE
                || type == Material.ANCIENT_DEBRIS) {
            return 15L;
        }
        if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE || type == Material.NETHER_GOLD_ORE
                || type == Material.REDSTONE_ORE || type == Material.DEEPSLATE_REDSTONE_ORE
                || type == Material.LAPIS_ORE || type == Material.DEEPSLATE_LAPIS_ORE
                || type == Material.NETHER_QUARTZ_ORE) {
            return 8L;
        }
        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE
                || type == Material.COPPER_ORE || type == Material.DEEPSLATE_COPPER_ORE
                || type == Material.COAL_ORE || type == Material.DEEPSLATE_COAL_ORE) {
            return 5L;
        }
        if (type == Material.MELON || type == Material.PUMPKIN) {
            return 6L;
        }
        if (isCropBlock(type)) {
            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                return ageable.getAge() >= ageable.getMaximumAge() ? 6L : 0L;
            }
            return 0L;
        }
        if (isLog(type)) {
            return 3L;
        }
        return 1L;
    }

    private boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    private boolean isLog(Material type) {
        String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM");
    }

    private boolean isCropBlock(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES
                || type == Material.BEETROOTS || type == Material.NETHER_WART || type == Material.COCOA;
    }

    private boolean isNegativeEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.POISON)
                || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.SLOWNESS)
                || type.equals(PotionEffectType.BLINDNESS)
                || type.equals(PotionEffectType.DARKNESS)
                || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.NAUSEA);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
