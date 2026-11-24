package com.rpgplugin.command;

import com.rpgplugin.RPGPlugin;
import com.rpgplugin.manager.ItemManager;
import com.rpgplugin.util.Compat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemEffectCommand implements CommandExecutor {

    private final RPGPlugin plugin;

    public ItemEffectCommand(RPGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("rpg.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || item.getItemMeta() == null) {
            player.sendMessage("§cBạn phải cầm một vật phẩm trên tay!");
            return true;
        }

        ItemManager itemManager = plugin.getItemManager();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        if (command.getName().equalsIgnoreCase("rpgfly")) {
            meta.getPersistentDataContainer().set(itemManager.FLIGHT_KEY, PersistentDataType.BYTE, (byte) 1);
            if (!lore.contains("§e§l[BAY LƯỢN]")) {
                lore.add(0, "§e§l[BAY LƯỢN]");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            player.sendMessage("§aĐã ép hiệu ứng Bay Lượn vào vật phẩm!");
        } else if (command.getName().equalsIgnoreCase("rpginf")) {
            meta.getPersistentDataContainer().set(itemManager.INFINITE_KEY, PersistentDataType.BYTE, (byte) 1);
            if (!lore.contains("§b§l[VÔ HẠN]")) {
                lore.add(0, "§b§l[VÔ HẠN]");
            }
            if (item.getType().getMaxDurability() > 0) {
                meta.setUnbreakable(true);
                meta.setLore(lore);
                item.setItemMeta(meta);
                player.sendMessage("§aĐã ép hiệu ứng Vô Hạn (Không thể phá hủy) vào trang bị!");
            } else {
                meta.setLore(lore);
                item.setItemMeta(meta);
                Compat.setMaxStackSizeSafe(item, 99);
                item.setAmount(99);
                player.sendMessage("§aĐã ép hiệu ứng Vô Hạn (99 stack) vào vật phẩm!");
            }
        }

        return true;
    }
}
