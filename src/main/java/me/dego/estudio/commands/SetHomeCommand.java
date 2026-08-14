package me.dego.estudio.commands;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class SetHomeCommand implements CommandExecutor {

    private final HomeManager homeManager;

    public SetHomeCommand(HomeManager homeManager){
        this.homeManager = homeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(!(sender instanceof Player player)){
            System.out.println("No eres un usuario!");
            return true;
        }

        int limite;

        if(player.hasPermission("homes.limit.13")){
            limite = 13;
        } else if (player.hasPermission("homes.limit.9")){
            limite = 9;
        } else if (player.hasPermission("homes.limit.6")){
            limite = 6;
        } else if (player.hasPermission("homes.limit.4")){
            limite = 4;
        } else { limite = 2; }

        UUID ID = player.getUniqueId();
        Location localizacion = player.getLocation();
        String nombre;
        if(args.length==0){
            nombre = "home";
        } else {nombre = args[0];}

        if(homeManager.getHomes(ID).size() >= limite){
            player.sendMessage(ChatColor.RED + "Has alcanzado tu límite de homes.");
            return true;
        } else {
            homeManager.guardarHome(ID, nombre, localizacion);
            player.sendMessage(ChatColor.GREEN + "Tu home " + ChatColor.GOLD + nombre + ChatColor.GREEN + " se ha creado correctamente!");

            return true;
        }
    }
}
