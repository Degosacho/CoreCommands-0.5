package me.dego.estudio.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persistencia de homes en SQLite + caché en memoria para lecturas rápidas.
 *
 * IMPORTANTE: guardarHome() y delHome() actualizan la base de datos y el
 * mapa en memoria en el mismo momento, para que un jugador pueda hacer
 * /sethome seguido de /home sin necesitar releer la base de datos.
 */
public class HomeDataBase {

    private final Plugin plugin;
    private Connection connection;

    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();

    public HomeDataBase(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Abre la conexión y crea la tabla si no existe. Llamar en onEnable(). */
    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "homes.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS homes (
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL,
                        PRIMARY KEY (uuid, name)
                    )
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo conectar a homes.db", e);
        }
    }

    /** Cierra la conexión. Llamar en onDisable(). */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cerrando homes.db", e);
        }
    }

    /**
     * Inserta o actualiza un home concreto. Se llama en cada /sethome.
     * Escribe en la BD Y actualiza el caché en memoria en el mismo momento.
     */
    public void guardarHome(UUID uuid, String name, Location location) {
        if (location.getWorld() == null) return;

        String sql = """
            INSERT INTO homes (uuid, name, world, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid, name) DO UPDATE SET
                world = excluded.world,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                yaw = excluded.yaw,
                pitch = excluded.pitch
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, location.getWorld().getName());
            ps.setDouble(4, location.getX());
            ps.setDouble(5, location.getY());
            ps.setDouble(6, location.getZ());
            ps.setFloat(7, location.getYaw());
            ps.setFloat(8, location.getPitch());
            ps.executeUpdate();

            // Actualiza también el caché en memoria, sin esto getHome() no vería el cambio
            homes.computeIfAbsent(uuid, k -> new HashMap<>()).put(name, location);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando home '" + name + "' de " + uuid, e);
        }
    }

    /**
     * Borra un home concreto. Se llama en cada /delhome.
     * Borra de la BD Y del caché en memoria en el mismo momento.
     */
    public boolean delHome(UUID uuid, String name) {
        String sql = "DELETE FROM homes WHERE uuid = ? AND name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();

            Map<String, Location> homesDelJugador = homes.get(uuid);
            boolean existiaEnCache = homesDelJugador != null && homesDelJugador.remove(name) != null;
            return existiaEnCache;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error borrando home '" + name + "' de " + uuid, e);
            return false;
        }
    }

    /** Accede a un home específico, SIEMPRE desde el caché en memoria (rápido, sin tocar la BD). */
    public Location getHome(UUID uuid, String nombre) {
        Map<String, Location> homesDelJugador = homes.get(uuid);
        if (homesDelJugador == null) return null;
        return homesDelJugador.get(nombre);
    }

    /** Accede a todos los homes de un jugador, desde el caché en memoria. */
    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> homesDelJugador = homes.get(uuid);
        if (homesDelJugador == null) return new HashMap<>();
        return homesDelJugador;
    }

    /**
     * Carga TODOS los homes de TODOS los jugadores desde la BD y los mete
     * directamente en el caché en memoria. Llamar UNA vez en onEnable(),
     * después de connect(). No hace falta usar el valor de retorno para
     * nada (aunque se devuelve por si algún día se necesita inspeccionar).
     */
    public Map<UUID, Map<String, Location>> loadAll() {
        String sql = "SELECT uuid, name, world, x, y, z, yaw, pitch FROM homes";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    plugin.getLogger().warning("Home '" + name + "' de " + uuid
                            + " referencia un mundo inexistente: " + worldName + " (se omite)");
                    continue;
                }

                Location location = new Location(
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                );

                // Se mete directamente en el caché interno 'homes' de esta clase
                homes.computeIfAbsent(uuid, k -> new HashMap<>()).put(name, location);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando homes.db", e);
        }

        return homes;
    }
}