package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.manager.PlayerDataManager;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class ToolEffectListener implements Listener {

    private static final int VEIN_MINER_LIMIT = 16;
    private static final int TIMBER_LIMIT = 24;
    private static final int EXCAVATOR_EXTRA_LIMIT = 8;

    private final RPGPlugin plugin;
    private final ItemManager itemManager;
    private final PlayerDataManager dataManager;
    private final Set<UUID> activeBreakers = new HashSet<>();

    public ToolEffectListener(RPGPlugin plugin, ItemManager itemManager, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.dataManager = dataManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir() || !tool.hasItemMeta()) return;

        String effectsStr = tool.getItemMeta().getPersistentDataContainer().get(itemManager.EFFECTS_KEY, PersistentDataType.STRING);
        if (effectsStr == null || effectsStr.isEmpty()) return;

        Set<String> effects = new HashSet<>();
        for (String token : effectsStr.split(",")) {
            effects.add(token.trim());
        }

        Block block = event.getBlock();
        Material originalType = block.getType();
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        boolean isSpawner = originalType == Material.SPAWNER;
        boolean handledDrops = false;

        // 1. SpawnerSilkTouch
        if (effects.contains("SpawnerSilkTouch") && isSpawner) {
            if (block.getState() instanceof CreatureSpawner spawner) {
                EntityType spawnedType = spawner.getSpawnedType();
                ItemStack spawnerItem = new ItemStack(Material.SPAWNER, 1);
                if (spawnerItem.getItemMeta() instanceof BlockStateMeta bsm) {
                    if (bsm.getBlockState() instanceof CreatureSpawner itemSpawner) {
                        itemSpawner.setSpawnedType(spawnedType);
                        bsm.setBlockState(itemSpawner);
                    }
                    String typeLabel = spawnedType != null ? spawnedType.name() : "PIG";
                    bsm.setDisplayName("§d§lSpawner (" + typeLabel + ")");
                    bsm.setLore(Collections.singletonList("§7Loại sinh vật: §e" + typeLabel));
                    spawnerItem.setItemMeta(bsm);
                }
                event.setExpToDrop(0);
                event.setDropItems(false);
                handledDrops = true;

                if (effects.contains("Magnetic")) {
                    giveOrDrop(player, spawnerItem, dropLoc);
                } else {
                    block.getWorld().dropItemNaturally(dropLoc, spawnerItem);
                }
            }
        }

        // 2. AutoSmelt
        if (!handledDrops && effects.contains("AutoSmelt") && !(block.getState() instanceof Container)) {
            Material smelted = getSmeltedMaterial(originalType);
            if (smelted != null) {
                int count = 1;
                if (isOre(originalType)) {
                    int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
                    if (fortune > 0) {
                        count += (int) (Math.random() * (Math.min(fortune, 10) + 1));
                    }
                }
                ItemStack smeltedStack = new ItemStack(smelted, count);
                event.setDropItems(false);
                handledDrops = true;
                block.getWorld().spawnParticle(Particle.FLAME, dropLoc, 6, 0.2, 0.2, 0.2, 0.02);

                if (effects.contains("Magnetic")) {
                    giveOrDrop(player, smeltedStack, dropLoc);
                } else {
                    block.getWorld().dropItemNaturally(dropLoc, smeltedStack);
                }
            }
        }

        // 3. Magnetic (for normal drops not already handled)
        if (!handledDrops && effects.contains("Magnetic") && !(block.getState() instanceof Container)) {
            Collection<ItemStack> drops = block.getDrops(tool, player);
            event.setDropItems(false);
            for (ItemStack drop : drops) {
                giveOrDrop(player, drop, dropLoc);
            }
        }

        // 4. ExpBoost
        if (effects.contains("ExpBoost") && event.getExpToDrop() > 0) {
            event.setExpToDrop((int) Math.ceil(event.getExpToDrop() * 1.5));
        }

        // 5. AutoReplant
        if (effects.contains("AutoReplant") && isReplantableCrop(originalType)) {
            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                    && ageable.getAge() >= ageable.getMaximumAge()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (block.getType().isAir()) {
                        block.setType(originalType);
                        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable newCrop) {
                            newCrop.setAge(0);
                            block.setBlockData(newCrop);
                        }
                    }
                }, 1L);
            }
        }

        // 6. TreasureHunter (8% bonus drop on stone/deepslate/ores)
        if (effects.contains("TreasureHunter") && (isOre(originalType) || isStoneLike(originalType))) {
            if (Math.random() < 0.08) {
                ItemStack treasure = rollTreasure();
                if (effects.contains("Magnetic")) {
                    giveOrDrop(player, treasure, dropLoc);
                } else {
                    block.getWorld().dropItemNaturally(dropLoc, treasure);
                }
            }
        }

        // 7. MidasTouch (10% nugget, 1% ingot)
        if (effects.contains("MidasTouch")) {
            double roll = Math.random();
            if (roll < 0.01) {
                ItemStack gold = new ItemStack(Material.GOLD_INGOT, 1);
                if (effects.contains("Magnetic")) giveOrDrop(player, gold, dropLoc);
                else block.getWorld().dropItemNaturally(dropLoc, gold);
            } else if (roll < 0.11) {
                ItemStack nugget = new ItemStack(Material.GOLD_NUGGET, Math.random() < 0.5 ? 1 : 2);
                if (effects.contains("Magnetic")) giveOrDrop(player, nugget, dropLoc);
                else block.getWorld().dropItemNaturally(dropLoc, nugget);
            }
        }

        // 8. TimeWarp (Haste IV + Speed II for 4s)
        if (effects.contains("TimeWarp")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 3, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, true, false, false));
        }

        // 9. StarCatcher (2% bonus RPG XP + 0.3% Enchanted Golden Apple on ores/mature crops)
        if (effects.contains("StarCatcher") && isStarEligible(block, originalType)) {
            if (Math.random() < 0.02) {
                PlayerData data = dataManager.getPlayerData(player.getUniqueId());
                int lv = data != null ? data.getLevel() : 1;
                long bonusXp = 50L + (lv * 5L);
                dataManager.addExperience(player, bonusXp);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dropLoc, 15, 0.3, 0.3, 0.3, 0.1);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                player.sendMessage("§e✦ [StarCatcher] §fBạn bắt được ánh sao và nhận §b+" + bonusXp + " XP§f!");
            }
            if (Math.random() < 0.003) {
                giveOrDrop(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), dropLoc);
                player.sendMessage("§6✦ [StarCatcher] §eNhân phẩm bùng nổ! Nhận được §6Táo Vàng Phù Phép§e!");
            }
        }

        // 10. BlackHole (pull nearby items/XP orbs within 12 blocks)
        if (effects.contains("BlackHole")) {
            Location playerPullLoc = player.getLocation().add(0, 0.8, 0);
            for (Entity near : block.getWorld().getNearbyEntities(dropLoc, 12, 12, 12)) {
                if (near instanceof Item || near instanceof ExperienceOrb) {
                    Vector pull = Compat.safeDirection(near.getLocation(), playerPullLoc).multiply(0.65);
                    near.setVelocity(pull);
                }
            }
        }

        // 11. Recursive Area Mining (VeinMiner, Timber, Excavator) - guarded against recursion
        UUID pid = player.getUniqueId();
        if (!activeBreakers.contains(pid)) {
            activeBreakers.add(pid);
            try {
                if (effects.contains("VeinMiner") && isOre(originalType)) {
                    breakConnected(player, block, originalType, VEIN_MINER_LIMIT, false);
                } else if (effects.contains("Timber") && isLog(originalType)) {
                    breakConnected(player, block, originalType, TIMBER_LIMIT, true);
                } else if (effects.contains("Excavator") && isExcavatorTarget(originalType)) {
                    breakExcavatorArea(player, block);
                }
            } finally {
                activeBreakers.remove(pid);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;
        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof CreatureSpawner itemSpawner) {
            EntityType type = itemSpawner.getSpawnedType();
            if (type != null && event.getBlockPlaced().getState() instanceof CreatureSpawner placedSpawner) {
                placedSpawner.setSpawnedType(type);
                placedSpawner.update(true, false);
            }
        }
    }

    private void breakConnected(Player player, Block origin, Material matchType, int limit, boolean includeDiagonals) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        visited.add(origin);
        enqueueNeighbors(origin, matchType, queue, visited, includeDiagonals);

        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            Block current = queue.poll();
            if (current.getType() != matchType) continue;
            if (player.getInventory().getItemInMainHand().getType().isAir()) break;

            if (player.breakBlock(current)) {
                broken++;
                enqueueNeighbors(current, matchType, queue, visited, includeDiagonals);
            }
        }
    }

    private void enqueueNeighbors(Block center, Material matchType, Queue<Block> queue, Set<Block> visited, boolean includeDiagonals) {
        if (includeDiagonals) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = center.getRelative(dx, dy, dz);
                        if (visited.add(neighbor) && neighbor.getType() == matchType) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        } else {
            BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block neighbor = center.getRelative(face);
                if (visited.add(neighbor) && neighbor.getType() == matchType) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private void breakExcavatorArea(Player player, Block center) {
        float pitch = player.getLocation().getPitch();
        List<Block> targets = new ArrayList<>();

        if (Math.abs(pitch) > 50) {
            // Horizontal 3x3
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    targets.add(center.getRelative(dx, 0, dz));
                }
            }
        } else {
            BlockFace facing = player.getFacing();
            boolean xWall = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH);
            for (int d = -1; d <= 1; d++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (d == 0 && dy == 0) continue;
                    targets.add(xWall ? center.getRelative(d, dy, 0) : center.getRelative(0, dy, d));
                }
            }
        }

        int broken = 0;
        for (Block target : targets) {
            if (broken >= EXCAVATOR_EXTRA_LIMIT) break;
            if (player.getInventory().getItemInMainHand().getType().isAir()) break;
            Material t = target.getType();
            if (t.isAir() || t == Material.BEDROCK || t == Material.BARRIER || t == Material.SPAWNER) continue;
            if (target.getState() instanceof Container) continue;
            if (isExcavatorTarget(t)) {
                if (player.breakBlock(target)) {
                    broken++;
                }
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack stack, Location fallbackLoc) {
        if (stack == null || stack.getType().isAir()) return;
        Compat.setMaxStackSizeSafe(stack, 99);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rem : leftover.values()) {
            fallbackLoc.getWorld().dropItemNaturally(fallbackLoc, rem);
        }
    }

    private Material getSmeltedMaterial(Material blockType) {
        return switch (blockType) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD_BLOCK -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER_BLOCK -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case SAND, RED_SAND -> Material.GLASS;
            case COBBLESTONE -> Material.STONE;
            case COBBLED_DEEPSLATE -> Material.DEEPSLATE;
            case STONE -> Material.SMOOTH_STONE;
            case CLAY -> Material.BRICK;
            default -> isLog(blockType) ? Material.CHARCOAL : null;
        };
    }

    private ItemStack rollTreasure() {
        double r = Math.random();
        if (r < 0.25) return new ItemStack(Material.IRON_INGOT, 1);
        if (r < 0.45) return new ItemStack(Material.GOLD_INGOT, 1);
        if (r < 0.65) return new ItemStack(Material.AMETHYST_SHARD, 2);
        if (r < 0.82) return new ItemStack(Material.EMERALD, 1);
        if (r < 0.94) return new ItemStack(Material.EXPERIENCE_BOTTLE, 2);
        return new ItemStack(Material.DIAMOND, 1);
    }

    private boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    private boolean isLog(Material type) {
        String name = type.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM");
    }

    private boolean isStoneLike(Material type) {
        return type == Material.STONE || type == Material.DEEPSLATE
                || type == Material.COBBLESTONE || type == Material.NETHERRACK || type == Material.END_STONE;
    }

    private boolean isExcavatorTarget(Material type) {
        return type == Material.STONE || type == Material.DEEPSLATE || type == Material.COBBLESTONE
                || type == Material.DIRT || type == Material.GRASS_BLOCK || type == Material.SAND
                || type == Material.RED_SAND || type == Material.GRAVEL || type == Material.NETHERRACK
                || type == Material.END_STONE || type == Material.TUFF;
    }

    private boolean isReplantableCrop(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES
                || type == Material.BEETROOTS || type == Material.NETHER_WART || type == Material.COCOA;
    }

    private boolean isStarEligible(Block block, Material type) {
        if (isOre(type)) return true;
        if (isReplantableCrop(type) && block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }
}
