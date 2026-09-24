package me.dego.estudio.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menú de resultados de la búsqueda. Se construye en caliente, no sale de
 * ningún .yml, porque el contenido depende de lo que haya escrito el jugador.
 *
 * Usa el mismo reparto simétrico que el resto de la tienda: dos filas de siete
 * huecos, con los espacios repartidos a los lados.
 */
public class SearchMenu {

    private static final int TAMANO = 54;
    private static final int SLOT_ATRAS = 48, SLOT_CERRAR = 49, SLOT_ANTERIOR = 47, SLOT_SIGUIENTE = 51;
    private static final int POR_PAGINA = 14;
    private static final int[][] PATRON = {
            {}, {3}, {2,4}, {1,3,5}, {0,2,4,6}, {1,2,3,4,5}, {0,1,2,4,5,6}, {0,1,2,3,4,5,6}
    };

    /** Marca el inventario como "menú de resultados" y guarda con qué se hizo. */
    public static class Holder implements InventoryHolder {
        private final String consulta;
        private final List<SearchIndex.Entrada> resultados;
        private final int pagina;
        private final Map<Integer, String> destinos = new HashMap<>();
        private Inventory inventory;

        Holder(String consulta, List<SearchIndex.Entrada> resultados, int pagina) {
            this.consulta = consulta;
            this.resultados = resultados;
            this.pagina = pagina;
        }

        public String getConsulta() { return consulta; }
        public List<SearchIndex.Entrada> getResultados() { return resultados; }
        public int getPagina() { return pagina; }
        /** Qué menú abre cada slot. Null si ese slot no lleva a ningún sitio. */
        public String destinoDe(int slot) { return destinos.get(slot); }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private static int[] slotsSimetricos(int n) {
        int[] out = new int[n];
        int arriba = n <= 7 ? n : (n + 1) / 2;
        int abajo = n - arriba;
        int i = 0;
        for (int o : PATRON[arriba]) out[i++] = 19 + o;
        if (abajo > 0) for (int o : PATRON[abajo]) out[i++] = 28 + o;
        return out;
    }

    public static Inventory construir(Player player, ShopManager manager, String consulta,
                                      List<SearchIndex.Entrada> resultados, int pagina) {
        int paginas = Math.max(1, (resultados.size() + POR_PAGINA - 1) / POR_PAGINA);
        pagina = Math.max(0, Math.min(pagina, paginas - 1));

        int desde = pagina * POR_PAGINA;
        int hasta = Math.min(desde + POR_PAGINA, resultados.size());
        List<SearchIndex.Entrada> trozo = new ArrayList<>(resultados.subList(desde, hasta));

        Holder holder = new Holder(consulta, resultados, pagina);
        String titulo = ColorUtil.colorize("<b>»</b> <gradient:#09AA00:#37C930><b>Buscar ➔ "
                + consulta + "</b></gradient> <b>«</b>"
                + (paginas > 1 ? " <#9E9E9E>(" + (pagina + 1) + "/" + paginas + ")" : ""));
        Inventory inv = Bukkit.createInventory(holder, TAMANO, titulo);

        int[] huecos = slotsSimetricos(trozo.size());
        for (int i = 0; i < trozo.size(); i++) {
            SearchIndex.Entrada e = trozo.get(i);
            ItemStack icono = manager.construirIcono(e.icono(), player);
            ItemMeta meta = icono.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.colorize("<#1FC2C2><b>" + e.nombre() + "</b>"));
                meta.setLore(List.of(
                        ColorUtil.colorize("<#4D4D4D>◇</#4D4D4D> <#474747>Resultado</#474747>"),
                        " ",
                        ColorUtil.colorize("<#FFB400>→</#FFB400> <gradient:#F5B845:#F5A102>"
                                + "¡Haz clic para abrirlo!</gradient>")));
                for (org.bukkit.inventory.ItemFlag f : org.bukkit.inventory.ItemFlag.values()) meta.addItemFlags(f);
                icono.setItemMeta(meta);
            }
            inv.setItem(huecos[i], icono);
            holder.destinos.put(huecos[i], e.menu());
        }

        if (pagina > 0) {
            inv.setItem(SLOT_ANTERIOR, boton(Material.SPECTRAL_ARROW,
                    "<gradient:#616161:#C4C4C4><b>Página anterior</b></gradient>",
                    "<#008504>←</#008504> <gradient:#BDBDBD:#919191>Página <#DBB30F>"
                            + pagina + "</#DBB30F> de <#DBB30F>" + paginas + "</#DBB30F>.</gradient>"));
            holder.destinos.put(SLOT_ANTERIOR, "\0anterior");
        }
        if (pagina < paginas - 1) {
            inv.setItem(SLOT_SIGUIENTE, boton(Material.SPECTRAL_ARROW,
                    "<gradient:#616161:#C4C4C4><b>Página siguiente</b></gradient>",
                    "<#008504>→</#008504> <gradient:#BDBDBD:#919191>Página <#DBB30F>"
                            + (pagina + 2) + "</#DBB30F> de <#DBB30F>" + paginas + "</#DBB30F>.</gradient>"));
            holder.destinos.put(SLOT_SIGUIENTE, "\0siguiente");
        }

        inv.setItem(SLOT_ATRAS, boton(Material.ARROW,
                "<gradient:#616161:#C4C4C4>Buscar otra cosa</gradient>",
                "<#008504>→</#008504> <gradient:#BDBDBD:#919191>Clic para "
                        + "<gradient:#616161:#C4C4C4>escribir</gradient> otro nombre.</gradient>"));
        holder.destinos.put(SLOT_ATRAS, "\0otra");

        inv.setItem(SLOT_CERRAR, boton(Material.BARRIER,
                "<gradient:#FF3D3D:#A80000><b>Cerrar</b></gradient>",
                "<#BA0000>→</#BA0000> <gradient:#BDBDBD:#919191>Clic para "
                        + "<gradient:#FF3D3D:#A80000>cerrar</gradient> la tienda.</gradient>"));
        holder.destinos.put(SLOT_CERRAR, "\0cerrar");

        ItemStack cristal = boton(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int s = 0; s < TAMANO; s++) {
            if (inv.getItem(s) == null) inv.setItem(s, cristal);
        }

        holder.inventory = inv;
        return inv;
    }

    private static ItemStack boton(Material material, String nombre, String descripcion) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(nombre));
            if (descripcion != null) meta.setLore(List.of(ColorUtil.colorize(descripcion)));
            for (org.bukkit.inventory.ItemFlag f : org.bukkit.inventory.ItemFlag.values()) meta.addItemFlags(f);
            it.setItemMeta(meta);
        }
        return it;
    }
}
