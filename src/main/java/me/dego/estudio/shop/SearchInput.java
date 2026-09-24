package me.dego.estudio.shop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pide al jugador que escriba lo que busca.
 *
 * Hay dos formas, se elige en config.yml con 'busqueda.entrada':
 *
 *   cartel  (por defecto)  Abre el editor de carteles. Para que el cliente lo
 *                          acepte hace falta que exista un cartel de verdad,
 *                          así que se coloca uno muy por encima del jugador,
 *                          donde solo hay aire, y se devuelve el bloque a como
 *                          estaba en cuanto termina de escribir. Si algo sale
 *                          mal, una tarea de seguridad lo restaura igualmente.
 *
 *   yunque                 Abre un yunque y se lee el nombre que teclea. No
 *                          toca el mundo para nada; es la opción segura si el
 *                          servidor híbrido se atraganta con lo del cartel.
 */
public class SearchInput implements Listener {

    private final Plugin plugin;
    private final ShopManager shopManager;
    private final Map<UUID, Pendiente> pendientes = new HashMap<>();

    /** Un cartel temporal esperando a que el jugador escriba. */
    private record Pendiente(Location donde, BlockData original, BukkitTask seguridad) {}

    /** Marca el inventario del yunque como "búsqueda de la tienda". */
    public static class YunqueHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    public SearchInput(Plugin plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public void pedir(Player player) {
        player.closeInventory();   // el editor de carteles no se abre con un inventario delante
        String modo = plugin.getConfig().getString("busqueda.entrada", "cartel").toLowerCase();
        if (modo.startsWith("yunque") || modo.startsWith("anvil")) abrirYunque(player);
        else abrirCartel(player);
    }

    // ============================================================ cartel

    private void abrirCartel(Player player) {
        cancelar(player.getUniqueId());

        Location donde = huecoDeAire(player);
        if (donde == null) {            // no hay sitio: no dejamos al jugador sin buscar
            abrirYunque(player);
            return;
        }

        Block bloque = donde.getBlock();
        BlockData original = bloque.getBlockData();
        bloque.setType(Material.OAK_SIGN, false);

        if (!(bloque.getState() instanceof Sign cartel)) {   // el híbrido no lo aceptó
            bloque.setBlockData(original, false);
            abrirYunque(player);
            return;
        }
        cartel.setWaxed(false);
        cartel.getSide(Side.FRONT).setLine(1, "^^^^^^^^^^^^^^^");
        cartel.getSide(Side.FRONT).setLine(2, "Escribe arriba");
        cartel.getSide(Side.FRONT).setLine(3, "lo que buscas");
        cartel.update(true, false);

        // Si el evento no llega nunca (desconexión, error del cliente...), devolvemos el bloque.
        BukkitTask seguridad = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pendiente p = pendientes.remove(player.getUniqueId());
            if (p != null) restaurar(p);
        }, 20L * 60);

        pendientes.put(player.getUniqueId(), new Pendiente(donde, original, seguridad));
        player.openSign(cartel, Side.FRONT);
    }

    /** Busca un bloque de aire por encima del jugador donde plantar el cartel. */
    private Location huecoDeAire(Player player) {
        World mundo = player.getWorld();
        Location base = player.getLocation();
        int techo = mundo.getMaxHeight() - 2;
        for (int dy : new int[]{40, 30, 20, 10, 60, 5, 3}) {
            int y = Math.min(base.getBlockY() + dy, techo);
            Location l = new Location(mundo, base.getBlockX(), y, base.getBlockZ());
            if (l.getBlock().getType() == Material.AIR) return l;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void alEscribirCartel(SignChangeEvent event) {
        Pendiente p = pendientes.get(event.getPlayer().getUniqueId());
        if (p == null) return;
        if (!event.getBlock().getLocation().equals(p.donde())) return;

        event.setCancelled(true);
        pendientes.remove(event.getPlayer().getUniqueId());
        restaurar(p);

        String texto = event.getLine(0);
        Player player = event.getPlayer();
        // No se puede abrir un inventario dentro de este evento: lo dejamos para el tick siguiente.
        Bukkit.getScheduler().runTask(plugin, () -> shopManager.buscar(player, texto));
    }

    private void restaurar(Pendiente p) {
        Block b = p.donde().getBlock();
        b.setBlockData(p.original(), false);
        if (p.seguridad() != null) p.seguridad().cancel();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(p.donde().getWorld())) {
                online.sendBlockChange(p.donde(), p.original());
            }
        }
    }

    private void cancelar(UUID uuid) {
        Pendiente p = pendientes.remove(uuid);
        if (p != null) restaurar(p);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent event) {
        cancelar(event.getPlayer().getUniqueId());
    }

    // ============================================================ yunque

    private void abrirYunque(Player player) {
        YunqueHolder holder = new YunqueHolder();
        Inventory inv = Bukkit.createInventory(holder, InventoryType.ANVIL,
                ColorUtil.colorize("<gradient:#09AA00:#37C930><b>¿Qué estás buscando?</b></gradient>"));
        holder.inventory = inv;

        ItemStack papel = new ItemStack(Material.PAPER);
        ItemMeta meta = papel.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("<#9E9E9E>Escribe aquí"));
            meta.setLore(List.of(ColorUtil.colorize(
                    "<#FFB400>→</#FFB400> <gradient:#F5B845:#F5A102>Y pulsa el resultado.</gradient>")));
            papel.setItemMeta(meta);
        }
        inv.setItem(0, papel);
        player.openInventory(inv);
    }

    @EventHandler
    public void alPrepararYunque(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof YunqueHolder)) return;
        String texto = event.getView().getRenameText();
        ItemStack resultado = new ItemStack(Material.PAPER);
        ItemMeta meta = resultado.getItemMeta();
        if (meta != null) {
            boolean vacio = texto == null || texto.isBlank();
            meta.setDisplayName(ColorUtil.colorize(vacio
                    ? "<#9E9E9E>Escribe un nombre"
                    : "<gradient:#0BB82A:#1AE83F><b>Buscar</b></gradient> <#DBB30F>" + texto));
            resultado.setItemMeta(meta);
        }
        event.setResult(resultado);
        // el yunque cobraria niveles si no lo ponemos a cero
        event.getView().setRepairCost(0);
    }

    @EventHandler
    public void alClicarYunque(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof YunqueHolder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView() instanceof AnvilView vista)) return;

        String texto = vista.getRenameText();
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> shopManager.buscar(player, texto));
    }
}
