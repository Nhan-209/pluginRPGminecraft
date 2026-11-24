package com.rpgplugin.model;

public enum ItemQuality {
    STAR_0(0, -0.3, 0),
    STAR_1(1, 0.0, 0),
    STAR_2(2, 0.3, 0),
    STAR_3(3, 0.6, 0),
    STAR_4(4, 0.6, 1),
    STAR_5(5, 1.0, 2),
    STAR_6(6, 1.2, 2), // Also 2x durability (handled in ItemManager)
    STAR_7(7, 1.5, 3), // 4x durability
    STAR_8(8, 2.0, 4), // Unbreakable
    STAR_9(9, 4.0, 5), // 1 Mythical, 4 Advanced, Unbreakable
    STAR_10(10, 10.0, 10), // 2 Mythical, 8 Advanced, Unbreakable
    STAR_11(11, 20.0, 15); // Siêu phẩm GOD TIER: cực kỳ mạnh

    private final int stars;
    private final double statMultiplier; // +0.3 means 130% stats
    private final int effectsCount;

    ItemQuality(int stars, double statMultiplier, int effectsCount) {
        this.stars = stars;
        this.statMultiplier = statMultiplier;
        this.effectsCount = effectsCount;
    }

    public int getStars() { return stars; }
    public double getStatMultiplier() { return statMultiplier; }
    public int getEffectsCount() { return effectsCount; }
    
    // Method to randomly roll a quality (higher stars are rarer)
    public static ItemQuality rollRandom() {
        double rand = Math.random();
        if (rand < 0.00001) return STAR_10; // 0.001%
        if (rand < 0.0001) return STAR_9;   // 0.01%
        if (rand < 0.001) return STAR_8;    // 0.1%
        if (rand < 0.005) return STAR_7;    // 0.5%
        if (rand < 0.01) return STAR_6;     // 1%
        if (rand < 0.03) return STAR_5;     // 3%
        if (rand < 0.08) return STAR_4;     // 8%
        if (rand < 0.15) return STAR_3;     // 15%
        if (rand < 0.30) return STAR_2;     // 30%
        if (rand < 0.60) return STAR_1;     // 60%
        return STAR_0;                      // 40%
    }
}
