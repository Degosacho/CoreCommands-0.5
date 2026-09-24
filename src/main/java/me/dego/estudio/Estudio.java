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
import me.dego.estudio.crates.CajasCommand;
import me.dego.estudio.crates.CrateDataBase;
import me.dego.estudio.crates.CrateListener;
import me.dego.estudio.crates.CrateManager;
import me.dego.estudio.crates.LlavesCommand;
import me.dego.estudio.holograms.HologramCommand;
import me.dego.estudio.holograms.HologramDataBase;
import me.dego.estudio.holograms.HologramListener;
import me.dego.estudio.holograms.HologramManager;
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
    private CrateDataBase crateDataBase;
    private CrateManager crateManager;
    private HologramDataBase hologramDataBase;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;

        // Sistema de tienda (/shop)
        saveDefaultConfig();
        shopManager = new ShopManager(this);
        getServer().getPluginManager().registerEvents(new ShopListener(shopManager), this);
        getServer().getPluginManager().registerEvents(shopManager.getEntradaBusqueda(), this);
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

        // Sistema de cajas (/llaves, /cajas). Va despues de vaultEconomy porque
        // los premios de dinero se pagan por Vault.
        crateDataBase = new CrateDataBase(this);
        crateDataBase.connect();
        crateDataBase.loadAll();

        crateManager = new CrateManager(this, crateDataBase, vaultEconomy, shopManager);
        crateManager.cargarCajas();
        getServer().getPluginManager().registerEvents(new CrateListener(crateManager), this);

        // Hologramas (/holo). Son entidades TextDisplay no persistentes: la
        // unica copia de verdad esta en holograms.db y se pintan desde ahi.
        hologramDataBase = new HologramDataBase(this);
        hologramDataBase.connect();
        hologramDataBase.loadAll();

        hologramManager = new HologramManager(this, hologramDataBase);
        getServer().getPluginManager().registerEvents(new HologramListener(hologramManager), this);
        hologramManager.arrancar();

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
        getCommand("itemname").setExecutor(new ItemNameCommand());
        getCommand("llaves").setExecutor(new LlavesCommand(crateManager));
        CajasCommand cajas = new CajasCommand(crateManager);
        getCommand("cajas").setExecutor(cajas);
        getCommand("cajas").setTabCompleter(cajas);
        HologramCommand holo = new HologramCommand(hologramManager);
        getCommand("holo").setExecutor(holo);
        getCommand("holo").setTabCompleter(holo);
        getCommand("buscar").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player p)) {
                sender.sendMessage("No eres un usuario.");
                return true;
            }
            if (args.length == 0) shopManager.pedirBusqueda(p);
            else shopManager.buscar(p, String.join(" ", args));
            return true;
        });


        System.out.println("El plugin ha sido cargado correctamente.");
    }

    @Override
    public void onDisable() {

        // Desconecta la DB de Homes
        homeDataBase.disconnect();

        // Desconecta la DB de Cajas
        if (crateDataBase != null) crateDataBase.disconnect();

        // Los hologramas se quitan del mundo; los datos siguen en holograms.db
        if (hologramManager != null) hologramManager.apagar();
        if (hologramDataBase != null) hologramDataBase.disconnect();

        jobManager.saveAll();
        // Desconecta la DB de Jobs
        jobDataBase.disconnect();

        System.out.println("El plugin se ha deshabilitado.");
    }

    public static Estudio getInstance() { return instance;}

    public CrateManager getCrateManager() { return crateManager; }

    public HologramManager getHologramManager() { return hologramManager; }
}