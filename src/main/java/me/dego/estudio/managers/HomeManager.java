package me.dego.estudio.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    // HashMap
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();

    // Metodo para guardar los homes
    public void guardarHome(UUID ID, String nombre, Location localizacion) {

        Map<String, Location> homesDelJugador = homes.computeIfAbsent(ID, k -> new HashMap<>());
        homesDelJugador.put(nombre, localizacion);

    }

    // Metodo para acceder a un home especifico
    public Location getHome(UUID ID, String nombre) {

        Map<String, Location> homesDelJugador = homes.get(ID);

        if (homesDelJugador == null) {
            return null; // No tiene ningun home
        }
        return homesDelJugador.get(nombre); // Devolverá la ubicacion correcta, o null en caso de no tener homes
    }

    // Metodo para acceder a todos los homes de un Jugador
    public Map<String, Location> getHomes(UUID ID) {
        Map<String, Location> homesDelJugador = homes.get(ID);

        if (homesDelJugador == null) {
            return new HashMap<>();
        }
        return homesDelJugador;
    }

    // Metodo para borrar un home
    public boolean delHome(UUID ID, String nombre) {
        Map<String, Location> homesDelJugador = homes.get(ID);

        if (homesDelJugador == null) {
            return false;
        }
        Location borrado = homesDelJugador.remove(nombre);

        return borrado != null;
    }

    // Metodo para guardar los homes en un .yml
    public void guardarEnArchivo(File archivo) {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Map<String, Location>> entradaJugador : homes.entrySet()) {
            UUID ID = entradaJugador.getKey();
            Map<String, Location> homesDelJugador = entradaJugador.getValue();

            for (Map.Entry<String, Location> entradaHome : homesDelJugador.entrySet()) {
                String nombre = entradaHome.getKey();
                Location localizacion = entradaHome.getValue();

                String ruta = "players." + ID + "." + nombre;

                config.set(ruta + ".world", localizacion.getWorld().getName());
                config.set(ruta + ".x", localizacion.getX());
                config.set(ruta + ".y", localizacion.getY());
                config.set(ruta + ".z", localizacion.getZ());
                config.set(ruta + ".yaw", localizacion.getYaw());
                config.set(ruta + ".pitch", localizacion.getPitch());
            }
        }
        try {
            config.save(archivo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Metodo para cargar los homes de un .yml
    public void cargarDesdeArchivo(File archivo) {
        if (!archivo.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection playersSection = config.getConfigurationSection("players");

        if (playersSection == null) {
            return;
        }

        for (String uuidTexto : playersSection.getKeys(false)) {
            UUID ID = UUID.fromString(uuidTexto);
            ConfigurationSection homesSection = playersSection.getConfigurationSection(uuidTexto);

            if (homesSection == null) {
                continue;
            }

            for (String nombre : homesSection.getKeys(false)) {
                String ruta = uuidTexto + "." + nombre;

                String world = playersSection.getString(ruta + ".world");
                double x = playersSection.getDouble(ruta + ".x");
                double y = playersSection.getDouble(ruta + ".y");
                double z = playersSection.getDouble(ruta + ".z");
                float yaw = (float) playersSection.getDouble(ruta + ".yaw");
                float pitch = (float) playersSection.getDouble(ruta + ".pitch");

                Location localizacion = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);

                Map<String, Location> homesDelJugador = homes.computeIfAbsent(ID, k -> new HashMap<>());
                homesDelJugador.put(nombre, localizacion);
            }
        }
    }
}
