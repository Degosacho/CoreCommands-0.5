package me.dego.estudio.crates;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * La ruleta: un inventario de 27 huecos donde la fila del medio va pasando
 * premios cada vez más despacio hasta pararse en el que ha tocado.
 *
 * El premio NO se sortea aquí: viene ya decidido y con la llave ya gastada.
 * Esta clase solo lo enseña. Si el jugador cierra el inventario o se
 * desconecta a media animación, el premio se entrega igual.
 */
public class CrateAnimation {

    /** Marca el inventario como "ruleta" para que el listener bloquee los clics. */
    public static class Holder implements InventoryHolder {
        private final CrateAnimation animacion;
        private Inventory inventory;
        Holder(CrateAnimation animacion) { this.animacion = animacion; }
        public CrateAnimation getAnimacion() { return animacion; }
        public Crate getCaja() { return animacion.caja; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private static final int VENTANA = 9;         // la fila del medio, slots 9..17
    private static final int CENTRO = 4;          // el 5º hueco de la ventana es el slot 13
    private static final int[] PASOS = {          // ticks de espera entre paso y paso
            2,2,2,2,2,2,2,2,2,2,2,2,
            3,3,3,3,3,
            4,4,4,4,
            5,5,5,
            6,7,9,11,14,18
    };

    private final CrateManager manager;
    private final Player player;
    private final Crate caja;
    private final CratePrize premio;
    private final Random random = new Random();

    private final List<CratePrize> cinta = new ArrayList<>();
    private Holder holder;
    private Inventory inv;
    private int paso = 0;
    private boolean terminada = false;

    public CrateAnimation(CrateManager manager, Player player, Crate caja, CratePrize premio) {
        this.manager = manager;
        this.player = player;
        this.caja = caja;
        this.premio = premio;
    }

    public void empezar() {
        // La cinta se rellena al azar y luego se fuerza el premio en la posición
        // exacta que quedará en el centro cuando la ruleta se pare.
        int total = PASOS.length + VENTANA;
        for (int i = 0; i < total; i++) cinta.add(manager.sortear(caja));
        cinta.set(PASOS.length - 1 + CENTRO, premio);

        holder = new Holder(this);
        inv = Bukkit.createInventory(holder, 27, ColorUtil.colorize(caja.getNombre()));
        holder.inventory = inv;

        ItemStack marcaArriba = boton(Material.LIME_STAINED_GLASS_PANE, "<gradient:#0BB82A:#1AE83F><b>▼</b></gradient>");
        ItemStack marcaAbajo = boton(Material.LIME_STAINED_GLASS_PANE, "<gradient:#0BB82A:#1AE83F><b>▲</b></gradient>");
        ItemStack relleno = boton(caja.getCristal(), " ");
        for (int s = 0; s < 27; s++) {
            if (s >= 9 && s <= 17) continue;
            inv.setItem(s, relleno);
        }
        inv.setItem(4, marcaArriba);
        inv.setItem(22, marcaAbajo);

        player.openInventory(inv);
        pintar(0);
        siguiente();
    }

    private void siguiente() {
        if (paso >= PASOS.length) {
            terminar();
            return;
        }
        int espera = PASOS[paso];
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            paso++;
            if (paso <= PASOS.length - 1) {
                pintar(paso);
                if (player.isOnline()) {
                    // el tono sube conforme frena, que es lo que le da la gracia
                    float tono = 0.7f + (1.3f * paso) / PASOS.length;
                    player.playSound(player.getLocation(), caja.getSonidoGiro(), 0.6f, tono);
                }
                siguiente();
            } else {
                terminar();
            }
        }, espera);
    }

    /** Dibuja la ventana de la cinta que toca en este paso. */
    private void pintar(int desde) {
        for (int i = 0; i < VENTANA; i++) {
            CratePrize p = cinta.get(Math.min(desde + i, cinta.size() - 1));
            inv.setItem(9 + i, p.icono(List.of("<#404040>" + caja.getNombrePlano() + "</#404040>")));
        }
    }

    private void terminar() {
        if (terminada) return;
        terminada = true;

        pintar(PASOS.length - 1);
        // Se resalta el ganador y se apagan los vecinos para que no haya dudas.
        ItemStack apagado = boton(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < VENTANA; i++) {
            if (i != CENTRO) inv.setItem(9 + i, apagado);
        }
        inv.setItem(13, premio.icono(List.of(
                "<#404040>" + caja.getNombrePlano() + "</#404040>",
                " ",
                "<gradient:#0BB82A:#1AE83F><b>¡Es tuyo!</b></gradient>")));

        manager.entregarPremio(player, caja, premio);
        manager.terminarGiro(player.getUniqueId());

        // Se deja ver el premio un momento y se cierra solo.
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                player.closeInventory();
            }
        }, 60L);
    }

    /**
     * Si el jugador cierra la ruleta antes de tiempo, el premio se entrega igual:
     * la llave ya está gastada, así que no puede quedarse sin nada.
     */
    public void cerradaAntesDeTiempo() {
        if (terminada) return;
        terminada = true;
        // Un tick de margen: al desconectarse tambien se cierra el inventario, y
        // en ese instante el jugador todavia figura conectado. Esperando un tick
        // sabemos de verdad si sigue ahi (se le da el premio) o si se ha ido
        // (se le devuelve la llave, que no se le puede dar nada a quien no esta).
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            if (player.isOnline()) {
                manager.entregarPremio(player, caja, premio);
            } else {
                manager.getDatos().darLlaves(player.getUniqueId(), caja.getId(), 1);
            }
            manager.terminarGiro(player.getUniqueId());
        }, 1L);
    }

    /**
     * Si se desconecta a media ruleta no se le puede dar nada, así que se le
     * devuelve la llave. Es lo único justo: ni pierde el premio ni lo cobra dos veces.
     */
    public void canceladaPorDesconexion() {
        if (terminada) return;
        terminada = true;
        manager.getDatos().darLlaves(player.getUniqueId(), caja.getId(), 1);
        manager.terminarGiro(player.getUniqueId());
        manager.getPlugin().getLogger().info(player.getName()
                + " se desconecto abriendo " + caja.getNombrePlano() + "; se le devolvio la llave.");
    }

    public Holder getHolder() { return holder; }

    private static ItemStack boton(Material material, String nombre) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(nombre));
            for (ItemFlag f : ItemFlag.values()) meta.addItemFlags(f);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Sonido de arranque, por si algún día se quiere separar del resto. */
    public static void sonidoArranque(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }
}
