package me.dego.estudio.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.util.Map;
import java.util.UUID;

public class HomesCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public HomesCommand(HomeManager homeManager){
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args){
        if(!(sender instanceof Player player)){
            System.out.println("No eres un usuario!");
            return true;
        }

        UUID ID = player.getUniqueId();

        Map<String, Location> misHomes = homeManager.getHomes(ID);

        if(misHomes.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "No tienes homes!");
            return true;
        }

        for(String nombreHome : misHomes.keySet()){
            player.sendMessage(ChatColor.GREEN + "· " + nombreHome);
        }
        return true;
    }
}