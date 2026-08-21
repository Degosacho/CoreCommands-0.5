package me.dego.estudio.managers.dependences;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class JobDataBase {

    private final Plugin plugin;
    private Connection connection;

    public JobDataBase(Plugin plugin) {
        this.plugin = plugin;
    }

    // Abre la conexión y crea la tabla si no existe. Llamar en onEnable()
    public void connect() {
        try {
            File dbfile = new File(plugin.getDataFolder(), "jobs.db");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbfile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                            CREATE TABLE IF NOT EXIST player_jobs (
                                uuid TEXT PRIMARY KEY,
                                job_id TEXT,
                                level INTEGER NOT NULL DEFAULT 1,
                                xp REAL NOT NULL DEFAULT 0
                            )
                        """);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo conectar a jobs.db", e);
        }
    }

    // Cierra la conexión. Llamar en onDisable()
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cerrando jobs.db", e);
        }
    }

    // Carga los datos de un jugador o devuelve un registro nuevo si no tiene trabajo
    public PlayerJobData load(UUID uuid) {
        String sql = "SELECT job_id, level, xp FROM player_jobs WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerJobData(
                            uuid,
                            rs.getString("job_id"),
                            rs.getInt("level"),
                            rs.getDouble("xp")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cargando datos de " + uuid, e);
        }

        // No existia
        return new PlayerJobData(uuid, null, 1, 0);
    }

    // Guarda los datos del jugador
    public void save(PlayerJobData data) {
        String sql = """
                        INSERT INTO player_jobs (uuid, job_id, level, xp)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            job_id = excluded.job_id,
                            level = excluded.level,
                            xp = excluded.xp
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getJobID());
            ps.setInt(3, data.getLevel());
            ps.setDouble(4, data.getXp());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error guardando datos de " + data.getUuid(), e);
        }
    }
}