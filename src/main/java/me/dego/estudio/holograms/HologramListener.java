package me.dego.estudio.holograms;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Mantiene vivos los hologramas segun va cargando y descargando el mundo.
 *
 * Las entidades no son persistentes, asi que desaparecen al descargarse el
 * chunk. Aqui se vuelven a crear cuando ese trozo de mundo vuelve, leyendo
 * siempre de la base de datos.
 */
public class HologramListener implements Listener {

    private final HologramManager manager;

    public HologramListener(HologramManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void alCargarChunk(ChunkLoadEvent event) {
        for (Hologram h : manager.enChunk(event.getChunk())) {
            if (!manager.estaPintado(h.getId())) manager.pintar(h);
        }
    }

    @EventHandler
    public void alDescargarChunk(ChunkUnloadEvent event) {
        // La entidad se va con el chunk; aqui solo soltamos la referencia para
        // que al volver a cargarse se cree una nueva y no se quede una muerta.
        for (Hologram h : manager.enChunk(event.getChunk())) {
            manager.olvidarEntidad(h.getId());
        }
    }

    /**
     * Los mundos que carga otro plugin (lobby, minijuegos...) llegan despues de
     * nuestro onEnable. En cuanto aparecen, se pintan los suyos.
     */
    @EventHandler
    public void alCargarMundo(WorldLoadEvent event) {
        manager.barrerHuerfanos(event.getWorld());
        int n = 0;
        for (Hologram h : manager.enMundo(event.getWorld().getName())) {
            if (manager.pintar(h)) n++;
        }
        if (n > 0) {
            manager.getPlugin().getLogger().info("Pintados " + n + " hologramas en el mundo "
                    + event.getWorld().getName() + ".");
        }
    }
}
