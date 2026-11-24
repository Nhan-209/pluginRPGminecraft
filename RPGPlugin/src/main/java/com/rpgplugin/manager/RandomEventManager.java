package com.rpgplugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class RandomEventManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public RandomEventManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startEventTask();
    }

    private void startEventTask() {
        // Run every 20 minutes (24000 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double roll = random.nextDouble();
            if (roll < 0.40) { // 40% chance cho Skeleton
                triggerSkeletonInvasion();
            } else if (roll < 0.70) { // 30% chance cho Zombie (từ 0.40 đến 0.70)
                triggerZombieInvasion();
            }
        }, 24000L, 24000L);
    }

    private void triggerZombieInvasion() {
        Bukkit.broadcastMessage("§c[EVENT] Cuộc xâm lăng của Zombie đã bắt đầu!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            World w = loc.getWorld();
            for (int i = 0; i < 15; i++) {
                double offsetX = (random.nextDouble() * 30) - 15;
                double offsetZ = (random.nextDouble() * 30) - 15;
                Location spawnLoc = loc.clone().add(offsetX, 0, offsetZ);
                spawnLoc.setY(w.getHighestBlockYAt(spawnLoc) + 1);
                
                w.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            }
        }
    }

    private void triggerSkeletonInvasion() {
        Bukkit.broadcastMessage("§e[EVENT] Cảnh báo! Một cuộc tập kích của Skeleton đã bắt đầu!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            World w = loc.getWorld();
            for (int i = 0; i < 15; i++) {
                double offsetX = (random.nextDouble() * 30) - 15;
                double offsetZ = (random.nextDouble() * 30) - 15;
                Location spawnLoc = loc.clone().add(offsetX, 0, offsetZ);
                spawnLoc.setY(w.getHighestBlockYAt(spawnLoc) + 1);
                
                w.spawnEntity(spawnLoc, EntityType.SKELETON);
            }
        }
    }
}
