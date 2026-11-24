package com.rpgplugin.model;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private int level = 1;
    private long experience = 0;
    private int statPoints = 0;

    // Attributes (8 RPG Stats)
    private int maxHealthStats = 0;
    private int damageStats = 0;
    private int speedStats = 0;
    private int miningSpeedStats = 0;
    private int hungerResistance = 0;
    private int defenseStats = 0;
    private int vitalityStats = 0;
    private int critStats = 0;

    // Flexible Player Settings
    private boolean speedLimited = false;
    private boolean skillsEnabled = true;
    private long lastRespecTime = 0L;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); }
    public void addLevel(int amount) { this.level = Math.max(1, this.level + amount); }

    public long getExperience() { return experience; }
    public void setExperience(long experience) { this.experience = Math.max(0L, experience); }
    public void addExperience(long amount) { this.experience += amount; }

    public int getStatPoints() { return statPoints; }
    public void setStatPoints(int statPoints) { this.statPoints = Math.max(0, statPoints); }
    public void addStatPoints(int amount) { this.statPoints += amount; }
    public boolean consumeStatPoints(int amount) {
        if (amount > 0 && this.statPoints >= amount) {
            this.statPoints -= amount;
            return true;
        }
        return false;
    }

    // Attribute getters & setters
    public int getMaxHealthStats() { return maxHealthStats; }
    public void addMaxHealthStats(int amount) { this.maxHealthStats += amount; }
    public void setMaxHealthStats(int amount) { this.maxHealthStats = amount; }

    public int getDamageStats() { return damageStats; }
    public void addDamageStats(int amount) { this.damageStats += amount; }
    public void setDamageStats(int amount) { this.damageStats = amount; }

    public int getSpeedStats() { return speedStats; }
    public void addSpeedStats(int amount) { this.speedStats += amount; }
    public void setSpeedStats(int amount) { this.speedStats = amount; }

    public int getMiningSpeedStats() { return miningSpeedStats; }
    public void addMiningSpeedStats(int amount) { this.miningSpeedStats += amount; }
    public void setMiningSpeedStats(int amount) { this.miningSpeedStats = amount; }

    public int getHungerResistance() { return hungerResistance; }
    public void addHungerResistance(int amount) { this.hungerResistance += amount; }
    public void setHungerResistance(int amount) { this.hungerResistance = amount; }

    public int getDefenseStats() { return defenseStats; }
    public void addDefenseStats(int amount) { this.defenseStats += amount; }
    public void setDefenseStats(int amount) { this.defenseStats = amount; }

    public int getVitalityStats() { return vitalityStats; }
    public void addVitalityStats(int amount) { this.vitalityStats += amount; }
    public void setVitalityStats(int amount) { this.vitalityStats = amount; }

    public int getCritStats() { return critStats; }
    public void addCritStats(int amount) { this.critStats += amount; }
    public void setCritStats(int amount) { this.critStats = amount; }

    public boolean isSpeedLimited() { return speedLimited; }
    public void setSpeedLimited(boolean speedLimited) { this.speedLimited = speedLimited; }

    public boolean isSkillsEnabled() { return skillsEnabled; }
    public void setSkillsEnabled(boolean skillsEnabled) { this.skillsEnabled = skillsEnabled; }

    public long getLastRespecTime() { return lastRespecTime; }
    public void setLastRespecTime(long lastRespecTime) { this.lastRespecTime = lastRespecTime; }

    public int getTotalUsedPoints() {
        return maxHealthStats + damageStats + speedStats + miningSpeedStats
                + hungerResistance + defenseStats + vitalityStats + critStats;
    }

    public void resetAllStats() {
        this.maxHealthStats = 0;
        this.damageStats = 0;
        this.speedStats = 0;
        this.miningSpeedStats = 0;
        this.hungerResistance = 0;
        this.defenseStats = 0;
        this.vitalityStats = 0;
        this.critStats = 0;
    }

    // Single-source-of-truth stat calculations
    public double getBonusMaxHealth() {
        return (maxHealthStats * 3.0) + ((maxHealthStats / 25) * 10.0);
    }

    public double getFlatDamageBonus() {
        return damageStats * 1.0;
    }

    public double getDamageMultiplier() {
        return 1.0 + Math.min(3.0, damageStats * 0.015);
    }

    public double getArmorBase() {
        return Math.min(20.0, defenseStats * 0.5);
    }

    public double getArmorToughness() {
        return Math.min(20.0, defenseStats * 0.4);
    }

    public double getKnockbackResistance() {
        return Math.min(0.80, defenseStats * 0.01);
    }

    public double getRpgDamageReduction() {
        if (defenseStats <= 0) return 0.0;
        return Math.min(0.75, defenseStats / (defenseStats + 60.0));
    }

    public double getRegenPer2Sec(double maxHp) {
        if (vitalityStats <= 0) return 0.0;
        return 0.5 + (vitalityStats * 0.25) + (maxHp * Math.min(0.035, vitalityStats * 0.0006));
    }

    public double getHealingMultiplier() {
        return 1.0 + Math.min(1.0, vitalityStats * 0.015);
    }

    public double getDebuffReduction() {
        return Math.min(0.50, vitalityStats * 0.005);
    }

    public float getWalkSpeed() {
        if (speedLimited) return 0.2f;
        return Math.min(0.65f, 0.2f + (speedStats * 0.004f));
    }

    public double getAttackSpeedBonus() {
        return Math.min(4.0, speedStats * 0.04);
    }

    public double getDodgeChance() {
        if (speedStats <= 0) return 0.0;
        return Math.min(0.30, speedStats / (speedStats + 180.0));
    }

    public double getCritChance() {
        return Math.min(0.65, 0.05 + (critStats * 0.006));
    }

    public double getCritMultiplier() {
        return 1.5 + Math.min(2.0, critStats * 0.015);
    }

    public double getArmorPenetration() {
        return Math.min(0.50, critStats * 0.004);
    }

    public double getMiningBreakSpeed() {
        return 1.0 + (miningSpeedStats * 0.2);
    }

    public double getMiningXpMultiplier() {
        return 1.0 + ((miningSpeedStats / 10) * 0.05);
    }

    public double getHungerSaveChance() {
        return Math.min(0.90, hungerResistance * 0.05);
    }

    public double getCooldownMultiplier() {
        return 1.0 - Math.min(0.45, hungerResistance * 0.006);
    }

    // Required XP calculation. Formula scales up indefinitely.
    public long getRequiredExperience() {
        return (long) (100 * Math.pow(1.1, level - 1));
    }
}
