package com.rpgplugin.gui;

import com.rpgplugin.manager.PlayerDataManager;
import com.rpgplugin.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Arrays;

public class StatsGUI implements Listener {

    public static class StatsMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final PlayerDataManager dataManager;
    private static final String GUI_NAME = "§8§l[ RPG Character & Stats ]";
    private final DecimalFormat df = new DecimalFormat("#.##");

    public StatsGUI(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void openGUI(Player player) {
        PlayerData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(new StatsMenuHolder(), 54, GUI_NAME);
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        synchronized (data) {
            double totalMaxHp = 20.0 + data.getBonusMaxHealth();

            inv.setItem(4, createGuiItem(Material.BOOK, "§e§lHồ Sơ Nhân Vật: " + player.getName(),
                    "§7Level: §a" + data.getLevel(),
                    "§7XP: §b" + data.getExperience() + " §7/ §b" + data.getRequiredExperience(),
                    "§7Điểm tiềm năng: §e" + data.getStatPoints(),
                    "§8-----------------------------",
                    "§c❤ Máu tối đa: §f" + df.format(totalMaxHp),
                    "§4⚔ Sát thương: §f+" + df.format(data.getFlatDamageBonus()) + " §7(x" + df.format(data.getDamageMultiplier()) + ")",
                    "§9🛡 Giảm sát thương RPG: §f" + df.format(data.getRpgDamageReduction() * 100.0) + "%",
                    "§6✦ Chí mạng: §f" + df.format(data.getCritChance() * 100.0) + "% §7(x" + df.format(data.getCritMultiplier()) + ")",
                    "§b💨 Né tránh: §f" + df.format(data.getDodgeChance() * 100.0) + "%",
                    "§d💚 Hồi máu/2s: §f+" + df.format(data.getRegenPer2Sec(totalMaxHp)),
                    "§e⏱ Giảm hồi chiêu: §f" + df.format((1.0 - data.getCooldownMultiplier()) * 100.0) + "%"
            ));

            // Row 2: Combat
            inv.setItem(10, statItem(Material.APPLE, "§c§lSinh Lực (Max Health)", data.getMaxHealthStats(), data.getStatPoints(),
                    "§7Mỗi điểm: §c+3.0 Máu tối đa",
                    "§7Mỗi 25 điểm: §c+10.0 Máu thưởng thêm",
                    "§7Hiện tại: §c+" + df.format(data.getBonusMaxHealth()) + " HP"));

            inv.setItem(12, statItem(Material.DIAMOND_SWORD, "§4§lSức Mạnh (Strength)", data.getDamageStats(), data.getStatPoints(),
                    "§7Mỗi điểm: §4+1.0 Sát thương & +1.5% Nhân sát thương",
                    "§7Hỗ trợ cả cận chiến & cung/nỏ",
                    "§7Hiện tại: §4+" + df.format(data.getFlatDamageBonus()) + " Dmg (x" + df.format(data.getDamageMultiplier()) + ")"));

            inv.setItem(14, statItem(Material.IRON_CHESTPLATE, "§9§lPhòng Thủ (Defense)", data.getDefenseStats(), data.getStatPoints(),
                    "§7Tăng Giáp, Kháng Đẩy Lùi & Giảm Sát Thương %",
                    "§7Vượt qua giới hạn giáp vanilla",
                    "§7Hiện tại: §9Giảm " + df.format(data.getRpgDamageReduction() * 100.0) + "% sát thương"));

            inv.setItem(16, statItem(Material.NETHER_STAR, "§6§lChí Mạng & Xuyên Giáp (Crit)", data.getCritStats(), data.getStatPoints(),
                    "§7Tăng Tỉ Lệ Chí Mạng, Sát Thương Chí Mạng",
                    "§7và Xuyên Giáp kẻ địch",
                    "§7Hiện tại: §6" + df.format(data.getCritChance() * 100.0) + "% Crit | " + df.format(data.getArmorPenetration() * 100.0) + "% Xuyên giáp"));

            // Row 3: Sustain & Mobility/Utility
            inv.setItem(28, statItem(Material.GOLDEN_APPLE, "§d§lHồi Phục (Vitality)", data.getVitalityStats(), data.getStatPoints(),
                    "§7Tự hồi máu mỗi 2s, tăng hiệu quả Hút Máu",
                    "§7và giảm thời gian hiệu ứng bất lợi",
                    "§7Hiện tại: §d+" + df.format(data.getRegenPer2Sec(totalMaxHp)) + " HP/2s | -" + df.format(data.getDebuffReduction() * 100.0) + "% Debuff"));

            inv.setItem(30, statItem(Material.FEATHER, "§b§lNhanh Nhẹn (Agility)", data.getSpeedStats(), data.getStatPoints(),
                    "§7Tăng Tốc độ chạy, Tốc đánh & Né tránh",
                    "§7Có thể khóa tốc chạy ở nút bên dưới",
                    "§7Hiện tại: §b" + df.format(data.getDodgeChance() * 100.0) + "% Né | +" + df.format(data.getAttackSpeedBonus()) + " Tốc đánh"));

            inv.setItem(32, statItem(Material.GOLDEN_PICKAXE, "§e§lTốc Độ Đào (Mining)", data.getMiningSpeedStats(), data.getStatPoints(),
                    "§7Mỗi điểm: §e+20% Tốc độ đào",
                    "§7Mỗi 10 điểm: §e+5% XP đào quặng/cày cuốc",
                    "§7Hiện tại: §ex" + df.format(data.getMiningBreakSpeed()) + " Tốc độ | x" + df.format(data.getMiningXpMultiplier()) + " XP"));

            inv.setItem(34, statItem(Material.COOKED_BEEF, "§6§lBền Bỉ & Hồi Chiêu (Stamina)", data.getHungerResistance(), data.getStatPoints(),
                    "§7Mỗi điểm: §6+5% Giữ thức ăn (max 90%)",
                    "§7và giảm thời gian hồi kỹ năng (max 45%)",
                    "§7Hiện tại: §6" + df.format(data.getHungerSaveChance() * 100.0) + "% No | -" + df.format((1.0 - data.getCooldownMultiplier()) * 100.0) + "% CD"));

            // Row 5: Controls
            inv.setItem(47, createGuiItem(Material.LEATHER_BOOTS,
                    data.isSpeedLimited() ? "§e§lGiới Hạn Tốc Chạy: §cĐANG BẬT" : "§e§lGiới Hạn Tốc Chạy: §aĐANG TẮT",
                    "§7Khi bật: Giữ tốc chạy mặc định để dễ xây dựng",
                    "§7nhưng vẫn giữ nguyên Né Tránh & Tốc Đánh!",
                    "§eClick để bật/tắt"));

            inv.setItem(49, createGuiItem(Material.MILK_BUCKET, "§c§lTẩy Điểm (Respec)",
                    "§7Hoàn trả toàn bộ §e" + data.getTotalUsedPoints() + " §7điểm đã cộng",
                    "§7Hồi chiêu: §f60 giây",
                    "§cShift + Click để xác nhận tẩy điểm!"));

            inv.setItem(51, createGuiItem(Material.BLAZE_POWDER,
                    data.isSkillsEnabled() ? "§a§lKỹ Năng Chủ Động: §aĐANG BẬT" : "§a§lKỹ Năng Chủ Động: §cĐANG TẮT",
                    "§b• Shift + F: §fDash (Lướt & Né đòn 0.5s)",
                    "§6• Shift + Chuột phải (Vũ khí): §fWar Cry (Bộc phá & Giải hiệu ứng)",
                    "§a• Shift + Chuột phải (Tay không/Khiên): §fSecond Wind (Hồi máu khẩn)",
                    "§eClick để bật/tắt kỹ năng chủ động"));
        }

        player.openInventory(inv);
    }

    private ItemStack statItem(Material mat, String name, int current, int unspent, String desc1, String desc2, String desc3) {
        return createGuiItem(mat, name,
                desc1,
                desc2,
                desc3,
                "§8-----------------------------",
                "§7Điểm hiện tại: §f" + current,
                "§7Điểm chưa cộng: §e" + unspent,
                "§8Chuột trái: +1 | Chuột phải: +10",
                "§8Shift+Trái: +100 | Shift+Phải: +Tất cả");
    }

    protected ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof StatsMenuHolder)) return;

        e.setCancelled(true);
        if (e.getRawSlot() < 0 || e.getRawSlot() >= 54) return;

        final ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        final Player player = (Player) e.getWhoClicked();
        PlayerData data = dataManager.getPlayerData(player.getUniqueId());
        if (data == null) return;

        int slot = e.getRawSlot();
        if (slot == 47) {
            synchronized (data) {
                data.setSpeedLimited(!data.isSpeedLimited());
            }
            dataManager.applyStatsToPlayer(player, data);
            openGUI(player);
            return;
        }
        if (slot == 49) {
            if (!e.isShiftClick()) {
                player.sendMessage("§e[RPG] Hãy §cShift + Click §eđể xác nhận tẩy toàn bộ điểm!");
                return;
            }
            if (dataManager.respecStats(player)) {
                openGUI(player);
            }
            return;
        }
        if (slot == 51) {
            synchronized (data) {
                data.setSkillsEnabled(!data.isSkillsEnabled());
            }
            openGUI(player);
            return;
        }

        if (slot != 10 && slot != 12 && slot != 14 && slot != 16 && slot != 28 && slot != 30 && slot != 32 && slot != 34) {
            return;
        }

        boolean upgraded = false;
        synchronized (data) {
            if (data.getStatPoints() <= 0) {
                player.sendMessage("§cBạn không còn điểm tiềm năng để cộng!");
                return;
            }

            int pointsToAdd = 1;
            if (e.isShiftClick() && e.isRightClick()) {
                pointsToAdd = data.getStatPoints();
            } else if (e.isShiftClick() && e.isLeftClick()) {
                pointsToAdd = Math.min(100, data.getStatPoints());
            } else if (e.isRightClick()) {
                pointsToAdd = Math.min(10, data.getStatPoints());
            }

            switch (slot) {
                case 10:
                    data.addMaxHealthStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 12:
                    data.addDamageStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 14:
                    data.addDefenseStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 16:
                    data.addCritStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 28:
                    data.addVitalityStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 30:
                    data.addSpeedStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 32:
                    data.addMiningSpeedStats(pointsToAdd);
                    upgraded = true;
                    break;
                case 34:
                    data.addHungerResistance(pointsToAdd);
                    upgraded = true;
                    break;
            }

            if (upgraded) {
                data.consumeStatPoints(pointsToAdd);
            }
        }

        if (upgraded) {
            player.sendMessage("§a[RPG] Đã nâng cấp chỉ số!");
            dataManager.applyStatsToPlayer(player, data);
            openGUI(player);
        }
    }
}
