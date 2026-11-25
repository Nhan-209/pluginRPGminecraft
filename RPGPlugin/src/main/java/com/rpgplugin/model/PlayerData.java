package com.rpgplugin.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;

public class PlayerData {
    public static final int MAX_LEVEL = 100_000;

    private static final BigDecimal XP_BASE = BigDecimal.valueOf(100);
    private static final BigDecimal XP_GROWTH = new BigDecimal("1.1");
    private static final BigInteger TEN_POW_18 = BigInteger.TEN.pow(18);
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Qa"};

    private final UUID uuid;
    private int level = 1;
    private BigInteger experience = BigInteger.ZERO;
    private int statPoints = 0;

    // Cached required XP for current level
    private int cachedReqLevel = -1;
    private BigInteger cachedReqXp = BigInteger.valueOf(100);

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

    private static int safeAddNonNeg(int base, int delta) {
        long sum = (long) base + delta;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, sum));
    }

    public static int clampLevel(long raw) {
        return (int) Math.max(1L, Math.min(MAX_LEVEL, raw));
    }

    public UUID getUuid() { return uuid; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = clampLevel(level); }
    public void addLevel(int amount) { this.level = clampLevel((long) this.level + amount); }

    public BigInteger getExperience() { return experience; }
    public void setExperience(BigInteger experience) {
        this.experience = (experience == null || experience.signum() < 0) ? BigInteger.ZERO : experience;
    }
    public void setExperience(long experience) {
        setExperience(BigInteger.valueOf(Math.max(0L, experience)));
    }
    public void addExperience(BigInteger amount) {
        if (amount != null && amount.signum() > 0) {
            this.experience = this.experience.add(amount);
        }
    }
    public void addExperience(long amount) {
        if (amount > 0) {
            this.experience = this.experience.add(BigInteger.valueOf(amount));
        }
    }

    public int getStatPoints() { return statPoints; }
    public void setStatPoints(int statPoints) { this.statPoints = Math.max(0, statPoints); }
    public void addStatPoints(int amount) { this.statPoints = safeAddNonNeg(this.statPoints, amount); }
    public void addStatPoints(long amount) {
        long sum = (long) this.statPoints + amount;
        this.statPoints = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, sum));
    }
    public boolean consumeStatPoints(int amount) {
        if (amount > 0 && this.statPoints >= amount) {
            this.statPoints -= amount;
            return true;
        }
        return false;
    }

    // Attribute getters & setters (overflow-safe)
    public int getMaxHealthStats() { return maxHealthStats; }
    public void addMaxHealthStats(int amount) { this.maxHealthStats = safeAddNonNeg(this.maxHealthStats, amount); }
    public void setMaxHealthStats(int amount) { this.maxHealthStats = Math.max(0, amount); }

    public int getDamageStats() { return damageStats; }
    public void addDamageStats(int amount) { this.damageStats = safeAddNonNeg(this.damageStats, amount); }
    public void setDamageStats(int amount) { this.damageStats = Math.max(0, amount); }

    public int getSpeedStats() { return speedStats; }
    public void addSpeedStats(int amount) { this.speedStats = safeAddNonNeg(this.speedStats, amount); }
    public void setSpeedStats(int amount) { this.speedStats = Math.max(0, amount); }

    public int getMiningSpeedStats() { return miningSpeedStats; }
    public void addMiningSpeedStats(int amount) { this.miningSpeedStats = safeAddNonNeg(this.miningSpeedStats, amount); }
    public void setMiningSpeedStats(int amount) { this.miningSpeedStats = Math.max(0, amount); }

    public int getHungerResistance() { return hungerResistance; }
    public void addHungerResistance(int amount) { this.hungerResistance = safeAddNonNeg(this.hungerResistance, amount); }
    public void setHungerResistance(int amount) { this.hungerResistance = Math.max(0, amount); }

    public int getDefenseStats() { return defenseStats; }
    public void addDefenseStats(int amount) { this.defenseStats = safeAddNonNeg(this.defenseStats, amount); }
    public void setDefenseStats(int amount) { this.defenseStats = Math.max(0, amount); }

    public int getVitalityStats() { return vitalityStats; }
    public void addVitalityStats(int amount) { this.vitalityStats = safeAddNonNeg(this.vitalityStats, amount); }
    public void setVitalityStats(int amount) { this.vitalityStats = Math.max(0, amount); }

    public int getCritStats() { return critStats; }
    public void addCritStats(int amount) { this.critStats = safeAddNonNeg(this.critStats, amount); }
    public void setCritStats(int amount) { this.critStats = Math.max(0, amount); }

    public boolean isSpeedLimited() { return speedLimited; }
    public void setSpeedLimited(boolean speedLimited) { this.speedLimited = speedLimited; }

    public boolean isSkillsEnabled() { return skillsEnabled; }
    public void setSkillsEnabled(boolean skillsEnabled) { this.skillsEnabled = skillsEnabled; }

    public long getLastRespecTime() { return lastRespecTime; }
    public void setLastRespecTime(long lastRespecTime) { this.lastRespecTime = lastRespecTime; }

    public long getTotalUsedPoints() {
        return (long) maxHealthStats + damageStats + speedStats + miningSpeedStats
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

    public static long calculateExpectedTotalStatPoints(int level) {
        int lv = clampLevel(level);
        if (lv <= 1) return 0L;
        long q = lv / 5L;
        long r = lv % 5L;
        return 5L * (lv - 1L) + (5L * q * (q - 1L)) / 2L + q * (r + 1L);
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

    // Exact arbitrary-precision required XP (scales cleanly beyond Long.MAX_VALUE up to MAX_LEVEL)
    public BigInteger getRequiredExperience() {
        if (cachedReqLevel == level && cachedReqXp != null) {
            return cachedReqXp;
        }
        BigInteger req = XP_BASE.multiply(XP_GROWTH.pow(level - 1, MathContext.DECIMAL128)).toBigInteger();
        if (req.signum() <= 0) {
            req = BigInteger.ONE;
        }
        cachedReqLevel = level;
        cachedReqXp = req;
        return req;
    }

    public double getExperienceProgressPercent() {
        if (level >= MAX_LEVEL) return 100.0;
        BigInteger req = getRequiredExperience();
        if (req.signum() <= 0 || experience.signum() <= 0) return 0.0;
        if (experience.compareTo(req) >= 0) return 99.9;
        double pct = new BigDecimal(experience)
                .multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(req), 2, RoundingMode.DOWN)
                .doubleValue();
        return Math.max(0.0, Math.min(99.9, pct));
    }

    public static String formatBigNumber(BigInteger val) {
        if (val == null || val.signum() <= 0) return "0";
        if (val.compareTo(BigInteger.valueOf(1000)) < 0) return val.toString();
        if (val.compareTo(TEN_POW_18) >= 0) {
            String s = val.toString();
            return s.charAt(0) + "." + s.substring(1, 3) + "e" + (s.length() - 1);
        }
        BigDecimal bd = new BigDecimal(val);
        BigDecimal thousand = BigDecimal.valueOf(1000);
        int idx = 0;
        while (bd.compareTo(thousand) >= 0 && idx < SUFFIXES.length - 1) {
            bd = bd.divide(thousand, 4, RoundingMode.DOWN);
            idx++;
        }
        return bd.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + SUFFIXES[idx];
    }
}
