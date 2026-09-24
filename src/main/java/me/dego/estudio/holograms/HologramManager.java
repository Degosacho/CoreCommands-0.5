package me.dego.estudio.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crea, borra y mantiene vivas las entidades de los hologramas.
 *
 * Como funciona la persistencia, que es lo que suele salir mal en los plugins
 * de hologramas: las entidades se crean con setPersistent(false), asi que NO se
 * guardan en el chunk. La unica fuente de verdad es holograms.db, y la entidad
 * se vuelve a crear cuando hace falta (al arrancar, al cargar el chunk, al
 * cargar el mundo). De esa forma es imposible acabar con hologramas duplicados
 * o con huerfanos que no se pueden borrar.
 *
 * Ademas, cada entidad lleva una marca en su PersistentDataContainer con el id
 * del holograma, para poder barrer restos de un arranque anterior.
 */
public class HologramManager {

    private final Plugin plugin;
    private final HologramDataBase datos;
    private final NamespacedKey marca;

    /** id del holograma -> uuid de la entidad que lo esta pintando ahora mismo. */
    private final Map<String, UUID> entidades = new HashMap<>();

    public HologramManager(Plugin plugin, HologramDataBase datos) {
        this.plugin = plugin;
        this.datos = datos;
        this.marca = new NamespacedKey(plugin, "holograma");
    }

    public HologramDataBase getDatos() { return datos; }
    public Plugin getPlugin() { return plugin; }

    // ============================================================ arranque

    /** Barre restos anteriores y pinta todo lo que se pueda pintar ya. */
    public void arrancar() {
        int barridos = 0;
        for (World w : Bukkit.getWorlds()) barridos += barrerHuerfanos(w);
        if (barridos > 0) {
            plugin.getLogger().info("Limpiados " + barridos + " hologramas sueltos de un arranque anterior.");
        }
        int pintados = 0;
        for (Hologram h : datos.lista()) {
            if (pintar(h)) pintados++;
        }
        plugin.getLogger().info("Hologramas: " + datos.lista().size() + " guardados, " + pintados + " visibles.");
    }

    /** Quita las entidades marcadas como nuestras que hayan sobrevivido. */
    public int barrerHuerfanos(World w) {
        int n = 0;
        for (TextDisplay d : w.getEntitiesByClass(TextDisplay.class)) {
            if (esNuestro(d)) { d.remove(); n++; }
        }
        return n;
    }

    private boolean esNuestro(TextDisplay d) {
        return d.getPersistentDataContainer().has(marca, PersistentDataType.STRING);
    }

    /** Tira todas las entidades. Llamar en onDisable(); los datos se quedan en la BD. */
    public void apagar() {
        for (Hologram h : datos.lista()) despintar(h);
    }

    // ============================================================ pintar

    /**
     * Crea la entidad del holograma si su mundo esta cargado. Devuelve false si
     * el mundo todavia no existe (ya se pintara cuando se cargue).
     */
    public boolean pintar(Hologram h) {
        Location loc = ubicacionUsable(h);
        if (loc == null) return false;

        despintar(h);   // por si acaso, nunca dos entidades para el mismo holograma

        // El Consumer se ejecuta ANTES de que la entidad entre al mundo, asi que
        // el jugador nunca llega a ver el holograma sin formato.
        TextDisplay d = loc.getWorld().spawn(loc, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(marca, PersistentDataType.STRING, h.getId());
            h.aplicar(e);
        });
        entidades.put(h.getId(), d.getUniqueId());
        return true;
    }

    /** Quita la entidad, pero el holograma sigue guardado. */
    public void despintar(Hologram h) {
        UUID uuid = entidades.remove(h.getId());
        if (uuid == null) return;
        org.bukkit.entity.Entity e = Bukkit.getEntity(uuid);
        if (e != null) e.remove();
    }

    /** Aplica los cambios en caliente. Si no habia entidad, la crea. */
    public void refrescar(Hologram h) {
        datos.guardar(h);
        UUID uuid = entidades.get(h.getId());
        org.bukkit.entity.Entity e = uuid == null ? null : Bukkit.getEntity(uuid);
        if (e instanceof TextDisplay d && d.isValid()) {
            // Si lo han movido de sitio hay que teletransportar la entidad.
            Location loc = ubicacionUsable(h);
            if (loc != null && !mismaPosicion(d.getLocation(), loc)) d.teleport(loc);
            h.aplicar(d);
        } else {
            pintar(h);
        }
    }

    private static boolean mismaPosicion(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && Math.abs(a.getX() - b.getX()) < 0.001
                && Math.abs(a.getY() - b.getY()) < 0.001
                && Math.abs(a.getZ() - b.getZ()) < 0.001;
    }

    /** La ubicacion con el mundo ya resuelto, o null si ese mundo no esta cargado. */
    private Location ubicacionUsable(Hologram h) {
        Location loc = h.getUbicacion();
        if (loc.getWorld() != null) return loc;
        String nombre = datos.mundoDe(h.getId());
        World w = nombre == null ? null : Bukkit.getWorld(nombre);
        if (w == null) return null;
        loc.setWorld(w);
        return loc;
    }

    // ============================================================ alta y baja

    public Hologram crear(String id, Location donde, List<String> lineas) {
        Hologram h = new Hologram(id, donde.clone());
        h.setLineas(lineas);
        datos.guardar(h);
        pintar(h);
        return h;
    }

    public boolean borrar(String id) {
        Hologram h = datos.get(id);
        if (h == null) return false;
        despintar(h);
        datos.borrar(id);
        return true;
    }

    public Hologram get(String id) { return datos.get(id); }
    public List<Hologram> lista() { return datos.lista(); }

    /** Vuelve a pintarlos todos. Para /holo recargar. */
    public void repintarTodos() {
        for (World w : Bukkit.getWorlds()) barrerHuerfanos(w);
        entidades.clear();
        for (Hologram h : datos.lista()) pintar(h);
    }

    // ============================================================ eventos

    /** Los hologramas de ese chunk, para volver a pintarlos cuando se carga. */
    public List<Hologram> enChunk(Chunk c) {
        List<Hologram> out = new ArrayList<>();
        // Se recorre el mapa directamente: esto salta en cada carga de chunk y
        // no conviene ir copiando la lista entera cada vez.
        if (datos.getTodos().isEmpty()) return out;
        for (Hologram h : datos.getTodos().values()) {
            String mundo = datos.mundoDe(h.getId());
            if (mundo == null || !mundo.equals(c.getWorld().getName())) continue;
            if (h.getUbicacion().getBlockX() >> 4 != c.getX()) continue;
            if (h.getUbicacion().getBlockZ() >> 4 != c.getZ()) continue;
            out.add(h);
        }
        return out;
    }

    /** Los de un mundo concreto, para cuando ese mundo acaba de cargarse. */
    public List<Hologram> enMundo(String mundo) {
        List<Hologram> out = new ArrayList<>();
        for (Hologram h : datos.getTodos().values()) {
            if (mundo.equals(datos.mundoDe(h.getId()))) out.add(h);
        }
        return out;
    }

    /** Olvida la entidad de un holograma (su chunk se ha descargado y ya no existe). */
    public void olvidarEntidad(String id) { entidades.remove(id); }

    public boolean estaPintado(String id) {
        UUID uuid = entidades.get(id);
        return uuid != null && Bukkit.getEntity(uuid) != null;
    }
}
