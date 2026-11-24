package com.rpgplugin.manager;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private static final long RESPEC_COOLDOWN_MS = 60_000L;

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final RPGPlugin plugin;
    private final File dataFolder;

    public PlayerDataManager(RPGPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public PlayerData loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        File file = new File(dataFolder, uuid.toString() + ".yml");
        PlayerData data = new PlayerData(uuid);

        synchronized (data) {
            if (file.exists()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                data.setLevel(config.getInt("level", 1));
                data.setExperience(config.getLong("experience", 0));
                data.setStatPoints(config.getInt("statPoints", 0));

                data.setMaxHealthStats(config.getInt("stats.health", 0));
                data.setDamageStats(config.getInt("stats.damage", 0));
                data.setSpeedStats(config.getInt("stats.speed", 0));
                data.setMiningSpeedStats(config.getInt("stats.mining", 0));
                data.setHungerResistance(config.getInt("stats.hunger", 0));
                data.setDefenseStats(config.getInt("stats.defense", 0));
                data.setVitalityStats(config.getInt("stats.vitality", 0));
                data.setCritStats(config.getInt("stats.crit", 0));

                data.setSpeedLimited(config.getBoolean("settings.speedLimited", false));
                data.setSkillsEnabled(config.getBoolean("settings.skillsEnabled", true));
            }

            playerDataMap.put(uuid, data);
            validateAndFixStats(data);
        }

        applyStatsToPlayer(player, data);
        return data;
    }

    public void validateAndFixStats(PlayerData data) {
        synchronized (data) {
            int expectedTotal = 0;
            for (int i = 2; i <= data.getLevel(); i++) {
                expectedTotal += 5 + (i / 5);
            }

            int currentUsed = data.getTotalUsedPoints();
            int actualTotal = currentUsed + data.getStatPoints();

            if (actualTotal != expectedTotal) {
                if (actualTotal < expectedTotal) {
                    data.setStatPoints(data.getStatPoints() + (expectedTotal - actualTotal));
                } else {
                    data.resetAllStats();
                    data.setStatPoints(expectedTotal);
                }
            }
        }
    }

    public boolean respecStats(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        if (data == null) return false;

        long now = System.currentTimeMillis();
        synchronized (data) {
            long elapsed = now - data.getLastRespecTime();
            if (elapsed < RESPEC_COOLDOWN_MS) {
                long remainingSec = Math.max(1, (RESPEC_COOLDOWN_MS - elapsed + 999L) / 1000L);
                player.sendMessage("§c[RPG] Vui lòng đợi " + remainingSec + " giây trước khi tẩy điểm lại!");
                return false;
            }

            int used = data.getTotalUsedPoints();
            if (used <= 0) {
                player.sendMessage("§e[RPG] Bạn chưa cộng điểm thuộc tính nào để tẩy!");
                return false;
            }

            data.resetAllStats();
            data.addStatPoints(used);
            data.setLastRespecTime(now);
        }

        applyStatsToPlayer(player, data);
        savePlayerData(player.getUniqueId());
        player.sendMessage("§a[RPG] Đã tẩy toàn bộ chỉ số và hoàn trả tất cả điểm tiềm năng!");
        return true;
    }

    public void savePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;

        File file = new File(dataFolder, uuid.toString() + ".yml");
        synchronized (data) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            config.set("level", data.getLevel());
            config.set("experience", data.getExperience());
            config.set("statPoints", data.getStatPoints());

            config.set("stats.health", data.getMaxHealthStats());
            config.set("stats.damage", data.getDamageStats());
            config.set("stats.speed", data.getSpeedStats());
            config.set("stats.mining", data.getMiningSpeedStats());
            config.set("stats.hunger", data.getHungerResistance());
            config.set("stats.defense", data.getDefenseStats());
            config.set("stats.vitality", data.getVitalityStats());
            config.set("stats.crit", data.getCritStats());

            config.set("settings.speedLimited", data.isSpeedLimited());
            config.set("settings.skillsEnabled", data.isSkillsEnabled());

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save player data for " + uuid + ": " + e.getMessage());
            }
        }
    }

    public void saveAll() {
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayerData(uuid);
        }
    }

    public void removePlayerData(UUID uuid) {
        savePlayerData(uuid);
        playerDataMap.remove(uuid);
    }

    public void applyStatsToPlayer(Player player, PlayerData data) {
        if (player == null || data == null) return;

        double maxHealth;
        float walkSpeed;
        double armor;
        double toughness;
        double knockbackResist;
        double attackSpeed;
        double blockBreakSpeed;
        int miningStats;

        synchronized (data) {
            maxHealth = 20.0 + data.getBonusMaxHealth();
            walkSpeed = data.getWalkSpeed();
            armor = data.getArmorBase();
            toughness = data.getArmorToughness();
            knockbackResist = data.getKnockbackResistance();
            attackSpeed = 4.0 + data.getAttackSpeedBonus();
            blockBreakSpeed = data.getMiningBreakSpeed();
            miningStats = data.getMiningSpeedStats();
        }

        Compat.setAttributeBase(player, Compat.MAX_HEALTH, maxHealth);
        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }

        player.setHealthScale(20.0);
        player.setHealthScaled(true);
        player.setWalkSpeed(walkSpeed);

        Compat.setAttributeBase(player, Compat.ARMOR, armor);
        Compat.setAttributeBase(player, Compat.ARMOR_TOUGHNESS, toughness);
        Compat.setAttributeBase(player, Compat.KNOCKBACK_RESISTANCE, knockbackResist);
        Compat.setAttributeBase(player, Compat.ATTACK_SPEED, attackSpeed);

        if (Compat.BLOCK_BREAK_SPEED != null && player.getAttribute(Compat.BLOCK_BREAK_SPEED) != null) {
            Compat.setAttributeBase(player, Compat.BLOCK_BREAK_SPEED, blockBreakSpeed);
        } else if (miningStats > 0) {
            int hasteLevel = Math.min(miningStats - 1, 10);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE, Integer.MAX_VALUE, hasteLevel, true, false, false));
        }
    }

    public void addExperience(Player player, long amount) {
        if (amount <= 0) return;
        PlayerData data = getPlayerData(player.getUniqueId());
        if (data == null) return;

        boolean leveledUp = false;
        int newLevel;
        int statPoints;

        synchronized (data) {
            data.addExperience(amount);
            while (data.getExperience() >= data.getRequiredExperience()) {
                data.setExperience(data.getExperience() - data.getRequiredExperience());
                data.addLevel(1);
                int pointsToGive = 5 + (data.getLevel() / 5);
                data.addStatPoints(pointsToGive);
                leveledUp = true;
            }
            newLevel = data.getLevel();
            statPoints = data.getStatPoints();
        }

        if (leveledUp) {
            player.sendMessage("§a[RPG] Cấp độ của bạn tăng lên: " + newLevel + "!");
            player.sendMessage("§a[RPG] Bạn có " + statPoints + " điểm tiềm năng. Dùng /rpg để cộng điểm!");
            applyStatsToPlayer(player, data);
        }
    }
}
