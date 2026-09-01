package me.dego.estudio.shop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

 /**
  * Núcleo del sistema de tiendas.
  *
  * - Carga todos los .yml de la carpeta shops/ al arrancar.
  * - Registra dinámicamente un comando de Bukkit por cada 'open_command'
  *   (así no hay que declarar cada tienda a mano en plugin.yml).
  * - Recuerda qué menú tiene abierto cada jugador (para /shop y para refresh).
  * - Gestiona las tareas de auto-refresco (update_interval).
  */

public class ShopManager {

    private final Plugin plugin;
    private final PlaceholderResolver resolver = new PlaceholderResolver();
    private final ActionExecutor actionExecutor;

    private final Map<String, ShopMenu> menusByCommand = new HashMap<>();

    // Recuerda el último menú abierto por cada jugador, para poder hacer [refresh]
    private final Map<UUID, String> lastOpenedMenu = new HashMap<>();

    // Tareas de auto-refresco activas por jugador (para cancelarlas al cerrar el inventario)
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();

    public ShopManager(Plugin plugin) {
        this.plugin = plugin;
        this.actionExecutor = new ActionExecutor(this, resolver);
    }

    public PlaceholderResolver getResolver() { return resolver; }
    public ActionExecutor getActionExecutor() { return actionExecutor; }

    // ============================================================
    //  Carga de menús desde shops/*.yml
    // ============================================================

    /** Carga (o recarga) todos los YAML de la carpeta shops/. Llamar en onEnable() y en /shop reload. */
    public void loadAllMenus() {
        menusByCommand.clear();

        File shopsFolder = new File(plugin.getDataFolder(), "shops");
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
            plugin.getLogger().warning("Carpeta shops/ creada vacía. Coloca ahí tus archivos .yml de tiendas.");
            return;
        }

        File[] files = shopsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                ShopMenu menu = ShopMenu.fromConfig(config, plugin);

                if (menu.getOpenCommand() == null || menu.getOpenCommand().isBlank()) {
                    plugin.getLogger().warning("Archivo " + file.getName() + " no tiene 'open_command', se ignora.");
                    continue;
                }

                menusByCommand.put(menu.getOpenCommand().toLowerCase(), menu);
                registerDynamicCommand(menu.getOpenCommand());

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando " + file.getName(), e);
            }
        }

        plugin.getLogger().info("Cargados " + menusByCommand.size() + " menús de tienda desde shops/");
    }

    public ShopMenu getMenu(String openCommand) {
        return menusByCommand.get(openCommand.toLowerCase());
    }

    // ============================================================
    //  Registro dinámico de comandos (uno por cada open_command del YAML)
    // ============================================================

    /**
     * Registra "/openCommand" como comando de Bukkit en caliente, sin necesitar
     * declararlo en plugin.yml. Usa el CommandMap interno del servidor vía
     * reflection, técnica estándar para plugins tipo DeluxeMenus.
     */
    private void registerDynamicCommand(String commandName) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Command command = new Command(commandName) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("No eres un usuario.");
                        return true;
                    }
                    open(player, commandName);
                    return true;
                }
            };

            commandMap.register(plugin.getName().toLowerCase(), command);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo registrar el comando dinámico /" + commandName, e);
        }
    }

    // ============================================================
    //  Abrir / refrescar menús
    // ============================================================

    public void open(Player player, String openCommand) {
        ShopMenu menu = getMenu(openCommand);
        if (menu == null) {
            player.sendMessage("§cEsa tienda no existe.");
            return;
        }

        if (menu.getOpenPermission() != null && !player.hasPermission(menu.getOpenPermission())) {
            actionExecutor.executeAll(menu.getOpenDenyCommands(), player);
            return;
        }

        ShopMenu.ShopMenuInstance instance = menu.build(player, resolver, menu.getItems());
        player.openInventory(instance.getInventory());

        lastOpenedMenu.put(player.getUniqueId(), openCommand);
        setupAutoRefresh(player, menu);
    }

    /** Reconstruye el inventario que el jugador tiene abierto ahora mismo, con datos frescos. */
    public void refresh(Player player) {
        String current = lastOpenedMenu.get(player.getUniqueId());
        if (current == null) return;

        ShopMenu menu = getMenu(current);
        if (menu == null) return;

        ShopMenu.ShopMenuInstance instance = menu.build(player, resolver, menu.getItems());
        player.openInventory(instance.getInventory()); // reabrir con el mismo título reconstruye el contenido
    }

    private void setupAutoRefresh(Player player, ShopMenu menu) {
        cancelRefreshTask(player.getUniqueId());

        if (menu.getUpdateIntervalTicks() <= 0) return;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelRefreshTask(player.getUniqueId());
                return;
            }
            refresh(player);
        }, menu.getUpdateIntervalTicks(), menu.getUpdateIntervalTicks());

        refreshTasks.put(player.getUniqueId(), task);
    }

    public void cancelRefreshTask(UUID uuid) {
        BukkitTask task = refreshTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    public void onPlayerCloseInventory(UUID uuid) {
        cancelRefreshTask(uuid);
        lastOpenedMenu.remove(uuid);
    }

     public Plugin getPlugin() { return plugin; }
}