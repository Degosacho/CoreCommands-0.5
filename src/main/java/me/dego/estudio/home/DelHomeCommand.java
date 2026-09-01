package me.dego.estudio.home;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DelHomeCommand implements CommandExecutor {

    private final HomeDataBase homeManager;

    public DelHomeCommand(HomeDataBase homeManager){
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            System.out.println("No eres un usuario!");
            return true;
        }

        UUID ID = player.getUniqueId();
        String nombre;
        if(args.length==0){
            nombre = "home";
        } else {nombre = args[0];}

        homeManager.delHome(ID, nombre);
        player.sendMessage(ChatColor.GREEN + "Home " + ChatColor.GOLD + nombre + ChatColor.GREEN + " borrado correctamente.");

        return true;
    }
}
