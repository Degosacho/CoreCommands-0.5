package me.dego.estudio.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaManager {

    private final Map<UUID, Map<UUID, BukkitTask>> peticiones = new HashMap<>();
    private final JavaPlugin plugin;

    public TpaManager(JavaPlugin plugin){
        this.plugin = plugin;
    }

    public void crearPeticion(UUID envia,UUID recibe){
        // Comprueba si ya hay peticiones
        Map<UUID, BukkitTask> peticionesRecibidas = peticiones.get(recibe);

        // Comprueba si "envia" ya le envió una petición a "recibe"
        if(peticionesRecibidas != null && peticionesRecibidas.containsKey(envia)){
            return;
        }

        // Crea el timer para que caduque la petición
        BukkitTask timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<UUID,BukkitTask> actual = peticiones.get(recibe);
            if(actual != null){
                actual.remove(envia);
            }
        }, 30*20L);

        // Crea la petición
        peticiones.computeIfAbsent(recibe, k -> new HashMap<>()).put(envia, timer);
    }


}
