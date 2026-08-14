package me.dego.estudio;

import me.dego.estudio.commands.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class Estudio extends JavaPlugin {

    private static Estudio instance;
    private HomeManager homeManager;
    private File archivoHomes;

    @Override
    public void onEnable() {
        instance = this;

        homeManager = new HomeManager();
        archivoHomes = new File(getDataFolder(),"homes.yml");
        homeManager.cargarDesdeArchivo(archivoHomes);

        getCommand("sethome").setExecutor(new SetHomeCommand(homeManager));
        getCommand("home").setExecutor(new HomeCommand(homeManager));
        getCommand("homes").setExecutor(new HomesCommand(homeManager));
        getCommand("delhome").setExecutor(new DelHomeCommand(homeManager));

        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);

        System.out.println("El plugin ha sido cargado correctamente.");
    }

    @Override
    public void onDisable() {

        homeManager.guardarEnArchivo(archivoHomes);

        System.out.println("El plugin se ha deshabilitado.");
    }

    public static Estudio getInstance() { return instance;}
}