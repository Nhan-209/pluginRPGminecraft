package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.manager.PlayerDataManager;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSkillListener implements Listener {

    private static final long DASH_BASE_CD_MS = 8_000L;
    private static final long WAR_CRY_BASE_CD_MS = 20_000L;
    private static final long SECOND_WIND_BASE_CD_MS = 45_000L;
    private static final long DASH_IFRAME_MS = 500L;

    private final RPGPlugin plugin;
    private final PlayerDataManager dataManager;

    private final Map<UUID, Long> dashCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warCryCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> secondWindCooldowns = new ConcurrentHashMap<>();

    public PlayerSkillListener(RPGPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        PlayerData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null || !data.isSkillsEnabled()) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long expire = dashCooldowns.get(player.getUniqueId());
        if (expire != null && now < expire) {
            long remSec = Math.max(1, (expire - now + 999L) / 1000L);
            player.sendMessage("§e[Dash] Đang hồi chiêu (" + remSec + "s)");
            return;
        }

        long cd = Math.round(DASH_BASE_CD_MS * data.getCooldownMultiplier());
        dashCooldowns.put(player.getUniqueId(), now + cd);

        Vector dir = player.getLocation().getDirection();
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(0, 0, 1);
        } else {
            dir = dir.normalize();
        }
        player.setVelocity(dir.multiply(1.45).setY(0.35));

        clearCobwebsAround(player);
        player.setMetadata("rpg_dash_iframe", new FixedMetadataValue(plugin, now + DASH_IFRAME_MS));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, true));

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.2f);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        PlayerData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null || !data.isSkillsEnabled()) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Material type = mainHand.getType();

        if (isMeleeWeapon(type)) {
            triggerWarCry(player, data);
        } else if (type == Material.AIR || type == Material.SHIELD) {
            if (triggerSecondWind(player, data, true)) {
                event.setCancelled(true);
            }
        }
    }

    private void triggerWarCry(Player player, PlayerData data) {
        long now = System.currentTimeMillis();
        Long expire = warCryCooldowns.get(player.getUniqueId());
        if (expire != null && now < expire) {
            long remSec = Math.max(1, (expire - now + 999L) / 1000L);
            player.sendMessage("§e[War Cry] Đang hồi chiêu (" + remSec + "s)");
            return;
        }

        long cd = Math.round(WAR_CRY_BASE_CD_MS * data.getCooldownMultiplier());
        warCryCooldowns.put(player.getUniqueId(), now + cd);

        double damage = 10.0 + (data.getFlatDamageBonus() * 1.5);
        for (Entity entity : player.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (entity instanceof Villager) continue;
            if (entity.hasMetadata("pet_owner")) continue;
            if (entity instanceof Monster monster && monster.isValid() && !monster.isDead()) {
                monster.damage(damage, player);
                Vector kb = Compat.safeDirection(player.getLocation(), monster.getLocation()).multiply(1.1).setY(0.35);
                monster.setVelocity(kb);
            }
        }

        cleanseDebuffs(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0, false, false, true));

        double maxHp = Compat.getAttributeValue(player, Compat.MAX_HEALTH, 20.0);
        double absorption = Math.min(20.0, maxHp * 0.15);
        if (player.getAbsorptionAmount() < absorption) {
            player.setAbsorptionAmount(absorption);
        }

        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1.0, 0), 12, 2.0, 0.5, 2.0, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1.1f);
        player.sendMessage("§6§l[War Cry] §eBộc phát chiến ý, giải trừ hiệu ứng xấu!");
    }

    public boolean triggerSecondWind(Player player, PlayerData data, boolean notifyCooldown) {
        if (data == null || !data.isSkillsEnabled()) return false;

        long now = System.currentTimeMillis();
        Long expire = secondWindCooldowns.get(player.getUniqueId());
        if (expire != null && now < expire) {
            if (notifyCooldown) {
                long remSec = Math.max(1, (expire - now + 999L) / 1000L);
                player.sendMessage("§e[Second Wind] Đang hồi chiêu (" + remSec + "s)");
            }
            return false;
        }

        long cd = Math.round(SECOND_WIND_BASE_CD_MS * data.getCooldownMultiplier());
        secondWindCooldowns.put(player.getUniqueId(), now + cd);

        double maxHp = Compat.getAttributeValue(player, Compat.MAX_HEALTH, 20.0);
        double healFraction = 0.25 + Math.min(0.25, data.getVitalityStats() * 0.0025);
        double healAmount = maxHp * healFraction * data.getHealingMultiplier();

        player.setHealth(Math.min(maxHp, player.getHealth() + healAmount));
        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1, false, false, true));

        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.2f);
        player.sendMessage("§a§l[Second Wind] §fHồi phục khẩn cấp §a+" + Math.round(healAmount) + " HP§f!");
        return true;
    }

    public void tryAutoSecondWind(Player player, PlayerData data, double hpAfterDamage) {
        if (player == null || data == null || !data.isSkillsEnabled()) return;
        double maxHp = Compat.getAttributeValue(player, Compat.MAX_HEALTH, 20.0);
        if (hpAfterDamage <= 0.0 || hpAfterDamage >= maxHp * 0.20) return;

        long now = System.currentTimeMillis();
        Long expire = secondWindCooldowns.get(player.getUniqueId());
        if (expire != null && now < expire) return;

        // Reserve cooldown immediately so simultaneous hits in the same tick don't double-schedule
        long cd = Math.round(SECOND_WIND_BASE_CD_MS * data.getCooldownMultiplier());
        secondWindCooldowns.put(player.getUniqueId(), now + cd);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            double currentMaxHp = Compat.getAttributeValue(player, Compat.MAX_HEALTH, 20.0);
            double healFraction = 0.25 + Math.min(0.25, data.getVitalityStats() * 0.0025);
            double healAmount = currentMaxHp * healFraction * data.getHealingMultiplier();

            player.setHealth(Math.min(currentMaxHp, player.getHealth() + healAmount));
            player.setFireTicks(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1, false, false, true));

            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.2f);
            player.sendMessage("§a§l[Second Wind] §fTự động kích hoạt! Hồi §a+" + Math.round(healAmount) + " HP§f!");
        }, 1L);
    }

    public String getCooldownStatus(UUID uuid) {
        PlayerData data = dataManager.getPlayerData(uuid);
        if (data != null && !data.isSkillsEnabled()) {
            return "§7Skills: OFF";
        }
        long now = System.currentTimeMillis();
        return "§bDash:" + formatCd(dashCooldowns.get(uuid), now)
                + " §6Cry:" + formatCd(warCryCooldowns.get(uuid), now)
                + " §aWind:" + formatCd(secondWindCooldowns.get(uuid), now);
    }

    private String formatCd(Long expire, long now) {
        if (expire == null || now >= expire) {
            return "§a✔";
        }
        long sec = Math.max(1, (expire - now + 999L) / 1000L);
        return "§e" + sec + "s";
    }

    private void clearCobwebsAround(Player player) {
        Block feet = player.getLocation().getBlock();
        Block head = player.getEyeLocation().getBlock();
        if (feet.getType() == Material.COBWEB) feet.setType(Material.AIR);
        if (head.getType() == Material.COBWEB) head.setType(Material.AIR);
    }

    private void cleanseDebuffs(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
    }

    private boolean isMeleeWeapon(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_SPEAR")
                || type == Material.MACE
                || type == Material.TRIDENT;
    }
}
