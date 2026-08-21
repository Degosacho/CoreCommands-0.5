package me.dego.estudio;

import me.dego.estudio.commands.*;
import me.dego.estudio.managers.HomeManager;
import me.dego.estudio.managers.dependences.JobDataBase;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class Estudio extends JavaPlugin {

    private static Estudio instance;
    private HomeManager homeManager;
    private File archivoHomes;
    private JobDataBase jobDataBase;

    @Override
    public void onEnable() {
        instance = this;

        homeManager = new HomeManager();
        archivoHomes = new File(getDataFolder(),"homes.yml");
        homeManager.cargarDesdeArchivo(archivoHomes);

        Pickaxe3x3Listener pico3x3 = new Pickaxe3x3Listener(this);
        getServer().getPluginManager().registerEvents(pico3x3,this);

        Pala3x3Listener pala3x3 = new Pala3x3Listener(this);
        getServer().getPluginManager().registerEvents(pala3x3,this);

        // Conecta la DB de Jobs
        jobDataBase = new JobDataBase(this);
        jobDataBase.connect();

        getCommand("sethome").setExecutor(new SetHomeCommand(homeManager));
        getCommand("home").setExecutor(new HomeCommand(homeManager));
        getCommand("homes").setExecutor(new HomesCommand(homeManager));
        getCommand("delhome").setExecutor(new DelHomeCommand(homeManager));
        getCommand("pico3x3").setExecutor(new DarPico3x3(pico3x3, this));
        getCommand("pala3x3").setExecutor(new DarPala3x3(pala3x3, this));
        getCommand("jobs").setExecutor(new JobsCommand());


        System.out.println("El plugin ha sido cargado correctamente.");
    }

    @Override
    public void onDisable() {

        homeManager.guardarEnArchivo(archivoHomes);

        // Desconecta la DB de Jobs
        jobDataBase.disconnect();

        System.out.println("El plugin se ha deshabilitado.");
    }

    public static Estudio getInstance() { return instance;}
}