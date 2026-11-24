package com.rpgplugin.listener;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.model.PlayerData;
import com.rpgplugin.util.Compat;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.SheepRegrowWoolEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class PassiveMobListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPassiveMobHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Animals animal && event.getDamager() instanceof Player player) {
            PlayerData data = RPGPlugin.getInstance().getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (data == null) return;

            String name = animal.getCustomName();
            if (name != null && name.contains("[Lv.")) {
                int mobLevel = Compat.parseEntityLevel(animal);
                if (mobLevel >= data.getLevel() - 2) {
                    double retaliationDamage = 2.0 + (mobLevel * 0.5);
                    player.damage(retaliationDamage, animal);
                    animal.setTarget(player);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCowMilk(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Cow cow) {
            Player player = event.getPlayer();
            if (player.getInventory().getItemInMainHand().getType() == Material.BUCKET
                    || player.getInventory().getItemInOffHand().getType() == Material.BUCKET) {
                for (Entity e : cow.getNearbyEntities(5, 5, 5)) {
                    if (e instanceof LivingEntity living && e != cow) {
                        living.damage(5.0, cow);
                        Vector kb = Compat.safeDirection(cow.getLocation(), e.getLocation());
                        e.setVelocity(kb.multiply(1.2));
                        break;
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSheepShear(PlayerShearEntityEvent event) {
        if (event.getEntity() instanceof Sheep sheep) {
            sheep.setMetadata("angry_sheep", new FixedMetadataValue(RPGPlugin.getInstance(), true));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSheepRegrow(SheepRegrowWoolEvent event) {
        event.getEntity().removeMetadata("angry_sheep", RPGPlugin.getInstance());
        event.getEntity().setTarget(null);
    }
}
