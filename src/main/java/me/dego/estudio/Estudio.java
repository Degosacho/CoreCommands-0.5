package me.dego.estudio;

import me.dego.estudio.commands.*;
import me.dego.estudio.home.DelHomeCommand;
import me.dego.estudio.home.HomeCommand;
import me.dego.estudio.home.HomesCommand;
import me.dego.estudio.home.SetHomeCommand;
import me.dego.estudio.home.HomeDataBase;
import me.dego.estudio.jobs.*;
import me.dego.estudio.dependences.VaultEconomy;
import me.dego.estudio.listeners.Pala3x3Listener;
import me.dego.estudio.listeners.Pickaxe3x3Listener;
import me.dego.estudio.shop.ShopCaptureCommand;
import me.dego.estudio.shop.ShopManager;
import me.dego.estudio.shop.ShopListener;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class Estudio extends JavaPlugin {

    private static Estudio instance;
    private HomeDataBase homeDataBase;
    private File archivoHomes;
    private JobDataBase jobDataBase;
    private VaultEconomy vaultEconomy;
    private JobManager jobManager;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        instance = this;

        // Sistema de tienda (/shop)
        shopManager = new ShopManager(this);
        getServer().getPluginManager().registerEvents(new ShopListener(shopManager), this);
        shopManager.loadAllMenus();

        // Conecta la DB de Jobs
        jobDataBase = new JobDataBase(this);
        jobDataBase.connect();

        // Conecta la DB de Homes
        homeDataBase = new HomeDataBase(this);
        homeDataBase.connect();
        homeDataBase.loadAll();

        // Configura el trabajo (Jobs)
        vaultEconomy = new VaultEconomy();
        vaultEconomy.setup(this);

        jobManager = new JobManager(this, jobDataBase, vaultEconomy);
        jobManager.loadJobsFromConfig();

        getServer().getPluginManager().registerEvents(new JobsListener(jobManager), this);
        getServer().getPluginManager().registerEvents(new JobsMenuListener(jobManager), this);

        // Crea el pico 3x3
        Pickaxe3x3Listener pico3x3 = new Pickaxe3x3Listener(this);
        getServer().getPluginManager().registerEvents(pico3x3,this);

        // Crea la pala 3x3
        Pala3x3Listener pala3x3 = new Pala3x3Listener(this);
        getServer().getPluginManager().registerEvents(pala3x3,this);


        // Comandos
        getCommand("sethome").setExecutor(new SetHomeCommand(homeDataBase));
        getCommand("home").setExecutor(new HomeCommand(homeDataBase));
        getCommand("homes").setExecutor(new HomesCommand(homeDataBase));
        getCommand("delhome").setExecutor(new DelHomeCommand(homeDataBase));
        getCommand("pico3x3").setExecutor(new DarPico3x3(pico3x3, this));
        getCommand("pala3x3").setExecutor(new DarPala3x3(pala3x3, this));
        getCommand("jobs").setExecutor(new JobsCommand(jobManager));
        getCommand("shopcapture").setExecutor(new ShopCaptureCommand());
        getCommand("estudioreload").setExecutor(new ReloadCommand(jobManager,shopManager));


        System.out.println("El plugin ha sido cargado correctamente.");
    }

    @Override
    public void onDisable() {

        // Desconecta la DB de Homes
        homeDataBase.disconnect();

        jobManager.saveAll();
        // Desconecta la DB de Jobs
        jobDataBase.disconnect();

        System.out.println("El plugin se ha deshabilitado.");
    }

    public static Estudio getInstance() { return instance;}
}