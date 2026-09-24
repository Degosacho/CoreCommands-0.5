package me.dego.estudio.crates;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Iterator;

/**
 * Todo lo que reacciona a las cajas:
 *
 *   - Clic derecho en el cofre: la abre (gastando una llave).
 *   - Clic izquierdo o agachado: enseña los premios sin gastar nada.
 *   - El cofre no se puede romper, ni volar, ni abrir como cofre normal.
 *   - En la ruleta no se puede tocar nada, y si se cierra antes de tiempo el
 *     premio se entrega igual.
 */
public class CrateListener implements Listener {

    public static final String PERMISO_ADMIN = "estudio.cajas.admin";

    private final CrateManager manager;

    public CrateListener(CrateManager manager) {
        this.manager = manager;
    }

    // ============================================================ el cofre

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alTocarCofre(PlayerInteractEvent event) {
        Block bloque = event.getClickedBlock();
        if (bloque == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        String id = manager.getDatos().cajaEn(bloque.getLocation());
        if (id == null) return;

        // Se cancela siempre: el cofre nunca debe abrirse como cofre normal.
        event.setCancelled(true);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Crate caja = manager.getCaja(id);
        if (caja == null) {
            player.sendMessage(ColorUtil.colorize("<#FF3D3D>Este cofre apunta a una caja que ya no existe: <#DBB30F>" + id));
            return;
        }

        boolean previa = event.getAction() == Action.LEFT_CLICK_BLOCK || player.isSneaking();
        if (previa) {
            CrateGui.abrirPrevia(manager, player, caja);
        } else {
            if (manager.llavesDe(player, caja.getId()) > 0) CrateAnimation.sonidoArranque(player);
            manager.abrir(player, caja);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alRomperCofre(BlockBreakEvent event) {
        if (manager.getDatos().cajaEn(event.getBlock().getLocation()) == null) return;
        if (event.getPlayer().hasPermission(PERMISO_ADMIN)) {
            event.getPlayer().sendMessage(ColorUtil.colorize(
                    "<#FFB400>Esto es un cofre de caja. Quítalo antes con <#DBB30F>/cajas quitar</#DBB30F>."));
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alExplotarEntidad(EntityExplodeEvent event) {
        quitarCofresDe(event.blockList().iterator());
    }

    @EventHandler(ignoreCancelled = true)
    public void alExplotarBloque(BlockExplodeEvent event) {
        quitarCofresDe(event.blockList().iterator());
    }

    private void quitarCofresDe(Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            Location l = b.getLocation();
            if (manager.getDatos().cajaEn(l) != null) it.remove();
        }
    }

    // ============================================================ los menús

    @EventHandler
    public void alClicar(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CrateAnimation.Holder) {
            event.setCancelled(true);   // durante la ruleta no se toca nada
            return;
        }
        if (!(event.getInventory().getHolder() instanceof CrateGui.Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        String destino = holder.destinoDe(event.getSlot());
        if (destino == null) return;

        switch (destino) {
            case "\0cerrar" -> player.closeInventory();
            case "\0atras" -> CrateGui.abrirLista(manager, player);
            case "\0comprar" -> irALaTienda(player, holder.getCaja());
            default -> {
                Crate caja = manager.getCaja(destino);
                if (caja == null) return;
                if (event.isRightClick()) CrateGui.abrirPrevia(manager, player, caja);
                else irALaTienda(player, caja);
            }
        }
    }

    private void irALaTienda(Player player, Crate caja) {
        if (caja == null) return;
        String menu = caja.getMenuCompra();
        if (menu == null || menu.isBlank() || manager.getTienda() == null) {
            player.sendMessage(ColorUtil.colorize("<#FF3D3D>Esta caja no tiene menú de compra configurado."));
            return;
        }
        manager.getTienda().open(player, menu);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CrateAnimation.Holder
                || event.getInventory().getHolder() instanceof CrateGui.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrateAnimation.Holder holder)) return;
        holder.getAnimacion().cerradaAntesDeTiempo();
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent event) {
        CrateAnimation animacion = manager.giroDe(event.getPlayer().getUniqueId());
        if (animacion != null) animacion.canceladaPorDesconexion();
    }
}
