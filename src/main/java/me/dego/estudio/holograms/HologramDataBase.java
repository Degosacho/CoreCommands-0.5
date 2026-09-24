package me.dego.estudio.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Guarda los hologramas en SQLite (holograms.db), con el mismo patron que
 * HomeDataBase y CrateDataBase: cada cambio toca la base de datos Y el mapa en
 * memoria a la vez.
 *
 * La base de datos es la unica fuente de verdad. Las entidades del mundo se
 * crean a partir de aqui en cada arranque y se tiran al apagar, asi que nunca
 * quedan hologramas huerfanos flotando por el mapa.
 */
public class HologramDataBase {

    private final Plugin plugin;
    private Connection connection;

    private final Map<String, Hologram> holograms = new LinkedHashMap<>();

    public HologramDataBase(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            File db = new File(plugin.getDataFolder(), "holograms.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS holograms (
                        id TEXT PRIMARY KEY,
                        mundo TEXT NOT NULL,
                        x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        lineas TEXT NOT NULL,
                        tamano REAL NOT NULL,
                        sombra INTEGER NOT NULL,
                        fondo TEXT,
                        rotacion REAL NOT NULL,
                        inclinacion REAL NOT NULL,
                        seguir TEXT NOT NULL,
                        luz_bloque INTEGER NOT NULL,
                        luz_cielo INTEGER NOT NULL,
                        opacidad INTEGER NOT NULL,
                        alineacion TEXT NOT NULL,
                        rango REAL NOT NULL,
                        traspasar INTEGER NOT NULL,
                        ancho INTEGER NOT NULL
                    )
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo conectar a holograms.db", e);
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cerrando holograms.db", e);
        }
    }

    /** Carga todos los hologramas a memoria. Llamar una vez tras connect(). */
    public void loadAll() {
        holograms.clear();
        if (connection == null) return;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM holograms")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String mundo = rs.getString("mundo");

                // Igual que con los cofres de las cajas: NO se descarta el
                // holograma porque su mundo no exista todavia. Los mundos que
                // carga otro plugin aparecen despues, y HologramListener los
                // pinta en cuanto el mundo se carga.
                World w = Bukkit.getWorld(mundo);
                Location loc = new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));

                Hologram h = new Hologram(id, loc);
                h.setLineas(Arrays.asList(rs.getString("lineas").split("\n", -1)));
                h.setTamano((float) rs.getDouble("tamano"));
                h.setSombra(rs.getInt("sombra") != 0);
                h.setFondo(rs.getString("fondo"));
                h.setRotacion((float) rs.getDouble("rotacion"));
                h.setInclinacion((float) rs.getDouble("inclinacion"));
                h.setSeguir(billboard(rs.getString("seguir")));
                int lb = rs.getInt("luz_bloque"), lc = rs.getInt("luz_cielo");
                h.setLuz(lb < 0 || lc < 0 ? null : new Display.Brightness(lb, lc));
                h.setOpacidad(rs.getInt("opacidad"));
                h.setAlineacion(alineacion(rs.getString("alineacion")));
                h.setRango((float) rs.getDouble("rango"));
                h.setTraspasar(rs.getInt("traspasar") != 0);
                h.setAncho(rs.getInt("ancho"));

                // El mundo guardado hace falta aunque no este cargado, para poder
                // volver a pintarlo cuando aparezca.
                h.getUbicacion().setWorld(w);
                mundosPendientes.put(id, mundo);
                holograms.put(id, h);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando holograms.db", e);
        }
    }

    /** id -> nombre del mundo, para los que todavia no estan cargados. */
    private final Map<String, String> mundosPendientes = new LinkedHashMap<>();

    public String mundoDe(String id) { return mundosPendientes.get(id); }

    private static Display.Billboard billboard(String s) {
        try { return Display.Billboard.valueOf(s); }
        catch (IllegalArgumentException e) { return Display.Billboard.CENTER; }
    }

    private static TextDisplay.TextAlignment alineacion(String s) {
        try { return TextDisplay.TextAlignment.valueOf(s); }
        catch (IllegalArgumentException e) { return TextDisplay.TextAlignment.CENTER; }
    }

    // ============================================================ escritura

    /** Inserta o actualiza. Toca la base de datos y el mapa en memoria. */
    public void guardar(Hologram h) {
        holograms.put(h.getId(), h);
        String mundo = h.getUbicacion().getWorld() != null
                ? h.getUbicacion().getWorld().getName()
                : mundosPendientes.getOrDefault(h.getId(), "world");
        mundosPendientes.put(h.getId(), mundo);

        if (connection == null) return;
        String sql = """
            INSERT INTO holograms (id, mundo, x, y, z, lineas, tamano, sombra, fondo,
                                   rotacion, inclinacion, seguir, luz_bloque, luz_cielo,
                                   opacidad, alineacion, rango, traspasar, ancho)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                mundo=excluded.mundo, x=excluded.x, y=excluded.y, z=excluded.z,
                lineas=excluded.lineas, tamano=excluded.tamano, sombra=excluded.sombra,
                fondo=excluded.fondo, rotacion=excluded.rotacion,
                inclinacion=excluded.inclinacion, seguir=excluded.seguir,
                luz_bloque=excluded.luz_bloque, luz_cielo=excluded.luz_cielo,
                opacidad=excluded.opacidad, alineacion=excluded.alineacion,
                rango=excluded.rango, traspasar=excluded.traspasar, ancho=excluded.ancho
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, h.getId());
            ps.setString(2, mundo);
            ps.setDouble(3, h.getUbicacion().getX());
            ps.setDouble(4, h.getUbicacion().getY());
            ps.setDouble(5, h.getUbicacion().getZ());
            ps.setString(6, String.join("\n", h.getLineas()));
            ps.setDouble(7, h.getTamano());
            ps.setInt(8, h.isSombra() ? 1 : 0);
            ps.setString(9, h.getFondo());
            ps.setDouble(10, h.getRotacion());
            ps.setDouble(11, h.getInclinacion());
            ps.setString(12, h.getSeguir().name());
            ps.setInt(13, h.getLuz() == null ? -1 : h.getLuz().getBlockLight());
            ps.setInt(14, h.getLuz() == null ? -1 : h.getLuz().getSkyLight());
            ps.setInt(15, h.getOpacidad());
            ps.setString(16, h.getAlineacion().name());
            ps.setDouble(17, h.getRango());
            ps.setInt(18, h.isTraspasar() ? 1 : 0);
            ps.setInt(19, h.getAncho());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando el holograma " + h.getId(), e);
        }
    }

    public boolean borrar(String id) {
        boolean habia = holograms.remove(id.toLowerCase()) != null;
        mundosPendientes.remove(id.toLowerCase());
        if (connection == null) return habia;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
            ps.setString(1, id.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error borrando el holograma " + id, e);
        }
        return habia;
    }

    public Hologram get(String id) { return id == null ? null : holograms.get(id.toLowerCase()); }
    public Map<String, Hologram> getTodos() { return holograms; }
    public List<Hologram> lista() { return new ArrayList<>(holograms.values()); }
}
