package com.rpgplugin.command;

import com.rpgplugin.gui.StatsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RPGCommand implements CommandExecutor {

    private final StatsGUI statsGUI;

    public RPGCommand(StatsGUI statsGUI) {
        this.statsGUI = statsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        statsGUI.openGUI(player);
        return true;
    }
}
