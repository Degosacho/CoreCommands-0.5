package me.dego.estudio.crates;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persistencia de las cajas en SQLite + caché en memoria, con el mismo patrón
 * que HomeDataBase: cada escritura toca la base de datos Y el mapa en memoria,
 * para que una llave gastada se vea al instante sin releer nada.
 *
 * Tres tablas:
 *   llaves   cuántas llaves de cada caja tiene cada jugador (son virtuales)
 *   bloques  qué cofre del spawn es qué caja
 *   registro histórico de aperturas, por si hay que revisar una queja
 */
public class CrateDataBase {

    private final Plugin plugin;
    private Connection connection;

    private final Map<UUID, Map<String, Integer>> llaves = new HashMap<>();
    private final Map<String, String> bloques = new HashMap<>();   // "mundo:x:y:z" -> id de caja

    public CrateDataBase(Plugin plugin) {
        this.plugin = plugin;
    }

    public static String clave(Location l) {
        if (l == null || l.getWorld() == null) return "";
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    /** Abre la conexión y crea las tablas si no existen. Llamar en onEnable(). */
    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            File db = new File(plugin.getDataFolder(), "crates.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS llaves (
                        uuid TEXT NOT NULL,
                        caja TEXT NOT NULL,
                        cantidad INTEGER NOT NULL,
                        PRIMARY KEY (uuid, caja)
                    )
                """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS bloques (
                        mundo TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        caja TEXT NOT NULL,
                        PRIMARY KEY (mundo, x, y, z)
                    )
                """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS registro (
                        fecha INTEGER NOT NULL,
                        uuid TEXT NOT NULL,
                        jugador TEXT NOT NULL,
                        caja TEXT NOT NULL,
                        premio TEXT NOT NULL,
                        valor INTEGER NOT NULL
                    )
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo conectar a crates.db", e);
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cerrando crates.db", e);
        }
    }

    /** Carga llaves y cofres a memoria. Llamar UNA vez en onEnable(), tras connect(). */
    public void loadAll() {
        if (connection == null) return;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, caja, cantidad FROM llaves")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                llaves.computeIfAbsent(uuid, k -> new HashMap<>())
                        .put(rs.getString("caja"), rs.getInt("cantidad"));
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando las llaves", e);
        }

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT mundo, x, y, z, caja FROM bloques")) {
            while (rs.next()) {
                String mundo = rs.getString("mundo");
                // No se comprueba que el mundo exista. Los mundos que carga otro
                // plugin (lobby, minijuegos...) todavia no estan aqui en onEnable,
                // y si se descartaban esos cofres dejaban de funcionar hasta el
                // siguiente /cajas colocar. La cache va por texto y solo se
                // consulta desde un bloque real, asi que el cofre responde en
                // cuanto su mundo este cargado.
                bloques.put(mundo + ":" + rs.getInt("x") + ":" + rs.getInt("y") + ":" + rs.getInt("z"),
                        rs.getString("caja"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando los cofres de las cajas", e);
        }
    }

    // ============================================================ llaves

    public int getLlaves(UUID uuid, String caja) {
        Map<String, Integer> suyas = llaves.get(uuid);
        return suyas == null ? 0 : suyas.getOrDefault(caja, 0);
    }

    public Map<String, Integer> getLlaves(UUID uuid) {
        return llaves.getOrDefault(uuid, new HashMap<>());
    }

    /** Suma (o resta, si es negativo) llaves. Nunca baja de cero. Devuelve el total resultante. */
    public int darLlaves(UUID uuid, String caja, int cuantas) {
        int total = Math.max(0, getLlaves(uuid, caja) + cuantas);
        ponerLlaves(uuid, caja, total);
        return total;
    }

    /** Deja el contador en un valor exacto. */
    public void ponerLlaves(UUID uuid, String caja, int total) {
        total = Math.max(0, total);
        llaves.computeIfAbsent(uuid, k -> new HashMap<>()).put(caja, total);
        if (connection == null) return;
        String sql = """
            INSERT INTO llaves (uuid, caja, cantidad) VALUES (?, ?, ?)
            ON CONFLICT(uuid, caja) DO UPDATE SET cantidad = excluded.cantidad
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, caja);
            ps.setInt(3, total);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando las llaves de " + uuid, e);
        }
    }

    /**
     * Gasta una llave. Devuelve false si no había ninguna, y en ese caso no
     * cambia nada. Se llama SIEMPRE antes de entregar el premio.
     */
    public boolean gastarLlave(UUID uuid, String caja) {
        int actuales = getLlaves(uuid, caja);
        if (actuales <= 0) return false;
        ponerLlaves(uuid, caja, actuales - 1);
        return true;
    }

    // ============================================================ cofres

    public String cajaEn(Location l) {
        return bloques.get(clave(l));
    }

    public void registrarBloque(Location l, String caja) {
        if (l == null || l.getWorld() == null) return;
        bloques.put(clave(l), caja);
        if (connection == null) return;
        String sql = """
            INSERT INTO bloques (mundo, x, y, z, caja) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(mundo, x, y, z) DO UPDATE SET caja = excluded.caja
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, l.getWorld().getName());
            ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY());
            ps.setInt(4, l.getBlockZ());
            ps.setString(5, caja);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando el cofre de la caja " + caja, e);
        }
    }

    public boolean quitarBloque(Location l) {
        if (l == null || l.getWorld() == null) return false;
        boolean habia = bloques.remove(clave(l)) != null;
        if (connection == null) return habia;
        String sql = "DELETE FROM bloques WHERE mundo = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, l.getWorld().getName());
            ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY());
            ps.setInt(4, l.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error borrando un cofre de cajas", e);
        }
        return habia;
    }

    public Map<String, String> getBloques() { return bloques; }

    // ============================================================ registro

    public void registrarApertura(UUID uuid, String jugador, String caja, String premio, int valor) {
        if (connection == null) return;
        String sql = "INSERT INTO registro (fecha, uuid, jugador, caja, premio, valor) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, uuid.toString());
            ps.setString(3, jugador);
            ps.setString(4, caja);
            ps.setString(5, premio);
            ps.setInt(6, valor);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error registrando la apertura de " + jugador, e);
        }
    }
}
