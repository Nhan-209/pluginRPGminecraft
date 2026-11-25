package com.rpgplugin.command;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("rpg.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("totem")) {
                ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
                ItemMeta meta = totem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§6§lTotem God");
                    NamespacedKey key = new NamespacedKey(RPGPlugin.getInstance(), "totem_god");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                    totem.setItemMeta(meta);
                }
                player.getInventory().addItem(totem);
                player.sendMessage("§aBạn đã nhận được Totem God!");
                return true;
            } else if (args[0].equalsIgnoreCase("resetlevel") && args.length == 2) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    PlayerData data = RPGPlugin.getInstance().getPlayerDataManager().getPlayerData(target.getUniqueId());
                    if (data != null) {
                        synchronized (data) {
                            data.setLevel(1);
                            data.setExperience(BigInteger.ZERO);
                            data.setStatPoints(0);
                            data.resetAllStats();
                        }
                        RPGPlugin.getInstance().getPlayerDataManager().applyStatsToPlayer(target, data);
                        RPGPlugin.getInstance().getPlayerDataManager().savePlayerData(target.getUniqueId());
                        player.sendMessage("§aĐã reset level và toàn bộ chỉ số của " + target.getName() + " về 1.");
                        target.sendMessage("§cLevel và toàn bộ chỉ số của bạn đã bị Admin reset về 1!");
                    }
                } else {
                    player.sendMessage("§cKhông tìm thấy người chơi.");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("addlevel") && args.length == 3) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    try {
                        int amount = Integer.parseInt(args[2]);
                        PlayerData data = RPGPlugin.getInstance().getPlayerDataManager().getPlayerData(target.getUniqueId());
                        if (data != null) {
                            synchronized (data) {
                                data.addLevel(amount);
                            }
                            RPGPlugin.getInstance().getPlayerDataManager().validateAndFixStats(data);
                            RPGPlugin.getInstance().getPlayerDataManager().applyStatsToPlayer(target, data);
                            RPGPlugin.getInstance().getPlayerDataManager().savePlayerData(target.getUniqueId());
                            player.sendMessage("§aĐã cập nhật level cho " + target.getName() + " (hiện tại: Lv." + data.getLevel() + ").");
                            target.sendMessage("§aAdmin đã điều chỉnh level của bạn thành Lv." + data.getLevel() + "!");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cSố lượng không hợp lệ.");
                    }
                } else {
                    player.sendMessage("§cKhông tìm thấy người chơi.");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("addstar")) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType() != Material.AIR) {
                    ItemStack upgraded = RPGPlugin.getInstance().getItemManager().upgradeQuality(item);
                    if (upgraded != null && !upgraded.equals(item)) {
                        player.getInventory().setItemInMainHand(upgraded);
                        player.sendMessage("§aĐã nâng cấp trang bị lên 1 sao!");
                    } else {
                        player.sendMessage("§cKhông thể nâng cấp (có thể đã max sao hoặc vật phẩm không hỗ trợ).");
                    }
                } else {
                    player.sendMessage("§cBạn phải cầm một vật phẩm trên tay.");
                }
                return true;
            }
        }

        player.sendMessage("§eHDSD: /adminrpg <totem | resetlevel <player> | addlevel <player> <amount> | addstar>");
        return true;
    }
}
