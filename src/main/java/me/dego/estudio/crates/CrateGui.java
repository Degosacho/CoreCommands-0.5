package me.dego.estudio.crates;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los dos menús "de mirar" de las cajas:
 *
 *   lista(): el de /llaves, con las cuatro cajas y cuántas llaves llevas.
 *   previa(): los premios de una caja con su probabilidad, para que nadie
 *             tenga que fiarse de nada.
 *
 * Usa el mismo reparto simétrico y los mismos botones que la tienda.
 */
public class CrateGui {

    public enum Tipo { LISTA, PREVIA }

    /** Marca el inventario y guarda qué hace cada hueco. */
    public static class Holder implements InventoryHolder {
        private final Tipo tipo;
        private final Crate caja;                        // null en la lista
        private final Map<Integer, String> destinos = new HashMap<>();
        private Inventory inventory;

        Holder(Tipo tipo, Crate caja) { this.tipo = tipo; this.caja = caja; }

        public Tipo getTipo() { return tipo; }
        public Crate getCaja() { return caja; }
        /** Qué caja abre cada hueco, o un centinela "\0cerrar" / "\0atras". */
        public String destinoDe(int slot) { return destinos.get(slot); }

        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private static final int[][] PATRON = {
            {}, {3}, {2,4}, {1,3,5}, {0,2,4,6}, {1,2,3,4,5}, {0,1,2,4,5,6}, {0,1,2,3,4,5,6}
    };

    // ============================================================ lista de cajas

    public static void abrirLista(CrateManager manager, Player player) {
        List<Crate> cajas = new ArrayList<>(manager.getCajas());
        Holder holder = new Holder(Tipo.LISTA, null);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtil.colorize(
                "<b>»</b> <shadow:yellow:0.6>🔑</shadow> <gradient:#09AA00:#37C930><b>Tus llaves</b></gradient>"
                        + " <shadow:yellow:0.6>🔑</shadow> <b>«</b>"));
        holder.inventory = inv;

        int n = Math.min(cajas.size(), 7);
        int[] patron = PATRON[n];
        for (int i = 0; i < n; i++) {
            Crate caja = cajas.get(i);
            int slot = 10 + patron[i];
            int tengo = manager.llavesDe(player, caja.getId());

            List<String> lore = new ArrayList<>();
            lore.add("<#4D4D4D>◇</#4D4D4D> <#474747>Caja</#474747>");
            lore.add(" ");
            for (String l : caja.getDescripcion()) lore.add(l);
            if (!caja.getDescripcion().isEmpty()) lore.add(" ");
            lore.add("<#9E9E9E>Tus llaves: <#DBB30F>" + tengo);
            lore.add("<#9E9E9E>Precio de la llave: <#3DCC04>" + conPuntos(caja.getPrecio()) + " $");
            lore.add(" ");
            lore.add("<#FFB400>→</#FFB400> <gradient:#F5B845:#F5A102>Clic izquierdo para comprar llaves.</gradient>");
            lore.add("<#FFB400>→</#FFB400> <gradient:#F5B845:#F5A102>Clic derecho para ver los premios.</gradient>");

            ItemStack it = caja.icono();
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(caja.getNombreColoreado());
                List<String> pintado = new ArrayList<>();
                for (String l : lore) pintado.add(ColorUtil.colorize(l));
                meta.setLore(pintado);
                for (ItemFlag f : ItemFlag.values()) meta.addItemFlags(f);
                it.setItemMeta(meta);
            }
            inv.setItem(slot, it);
            holder.destinos.put(slot, caja.getId());
        }

        inv.setItem(22, boton(Material.BARRIER, "<gradient:#FF3D3D:#A80000><b>Cerrar</b></gradient>",
                List.of("<#BA0000>→</#BA0000> <gradient:#BDBDBD:#919191>Clic para "
                        + "<gradient:#FF3D3D:#A80000>cerrar</gradient>.</gradient>")));
        holder.destinos.put(22, "\0cerrar");

        rellenar(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    // ============================================================ premios de una caja

    public static void abrirPrevia(CrateManager manager, Player player, Crate caja) {
        Holder holder = new Holder(Tipo.PREVIA, caja);
        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtil.colorize(
                "<b>»</b> <shadow:yellow:0.6>🎁</shadow> " + caja.getNombre()
                        + " <gradient:#09AA00:#37C930><b>➔ Premios</b></gradient> <b>«</b>"));
        holder.inventory = inv;

        List<CratePrize> premios = caja.getPremios();
        int[] huecos = repartir(premios.size());
        for (int i = 0; i < premios.size() && i < huecos.length; i++) {
            CratePrize p = premios.get(i);
            inv.setItem(huecos[i], p.icono(loreDelPremio(manager, caja, p)));
        }

        int tengo = manager.llavesDe(player, caja.getId());
        ItemStack info = caja.icono();
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(caja.getNombreColoreado());
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.colorize("<#4D4D4D>◇</#4D4D4D> <#474747>Info</#474747>"));
            lore.add(" ");
            for (String l : caja.getDescripcion()) lore.add(ColorUtil.colorize(l));
            if (!caja.getDescripcion().isEmpty()) lore.add(" ");
            lore.add(ColorUtil.colorize("<#9E9E9E>Premios distintos: <#DBB30F>" + premios.size()));
            lore.add(ColorUtil.colorize("<#9E9E9E>Tus llaves: <#DBB30F>" + tengo));
            lore.add(ColorUtil.colorize("<#9E9E9E>Precio de la llave: <#3DCC04>"
                    + conPuntos(caja.getPrecio()) + " $"));
            meta.setLore(lore);
            for (ItemFlag f : ItemFlag.values()) meta.addItemFlags(f);
            info.setItemMeta(meta);
        }
        inv.setItem(4, info);

        inv.setItem(48, boton(Material.ARROW, "<gradient:#616161:#C4C4C4>Atrás</gradient>",
                List.of("<#008504>→</#008504> <gradient:#BDBDBD:#919191>Clic para "
                        + "<gradient:#616161:#C4C4C4>volver</gradient> a tus llaves.</gradient>")));
        holder.destinos.put(48, "\0atras");

        inv.setItem(49, boton(Material.BARRIER, "<gradient:#FF3D3D:#A80000><b>Cerrar</b></gradient>",
                List.of("<#BA0000>→</#BA0000> <gradient:#BDBDBD:#919191>Clic para "
                        + "<gradient:#FF3D3D:#A80000>cerrar</gradient>.</gradient>")));
        holder.destinos.put(49, "\0cerrar");

        inv.setItem(50, boton(Material.TRIPWIRE_HOOK, "<gradient:#0BB82A:#1AE83F><b>Comprar llaves</b></gradient>",
                List.of("<#FFB400>→</#FFB400> <gradient:#F5B845:#F5A102>Clic para ir a la tienda.</gradient>")));
        holder.destinos.put(50, "\0comprar");

        rellenar(inv, caja.getCristal());
        player.openInventory(inv);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    // ============================================================ utilidades

    /**
     * La descripción de un premio en la vista previa. Un Pokémon no tiene precio
     * en la tienda, así que en vez del valor se enseña de dónde sale, a qué nivel
     * viene y si es shiny, que es lo que de verdad quiere saber el jugador.
     */
    private static List<String> loreDelPremio(CrateManager manager, Crate caja, CratePrize p) {
        List<String> lore = new ArrayList<>();
        lore.add("<#404040>" + caja.getNombrePlano() + "</#404040>");
        lore.add(" ");

        if (p.getTipo() == CratePrize.Tipo.POKEMON) {
            int cuantos = manager.getPokemon().tamano(p.getLista());
            lore.add("<#9E9E9E>Sale de: <#DBB30F>" + cuantos + "</#DBB30F> especies distintas");
            lore.add("<#9E9E9E>Nivel: <#DBB30F>" + p.getNivel());
            lore.add(p.getShinyProb() >= 100
                    ? "<#9E9E9E>Shiny: <gradient:#F5E08F:#DBB30F><b>siempre</b></gradient>"
                    : "<#9E9E9E>Shiny: <#DBB30F>no");
            lore.add("<#9E9E9E>IVs: <#DBB30F>aleatorios");
        } else {
            lore.add("<#9E9E9E>Cantidad: x<#DBB30F>" + p.getCantidad());
            lore.add("<#9E9E9E>Valor en la tienda: <#3DCC04>" + conPuntos(p.getValor()) + " $");
        }

        lore.add(" ");
        lore.add("<#9E9E9E>Probabilidad: <#DBB30F>"
                + String.format("%.2f", caja.probabilidad(p)) + " %");
        return lore;
    }

    /**
     * Reparte n premios en filas de 7 lo más igualadas posible y centra cada
     * fila, igual que hacen los menús de la tienda.
     */
    private static int[] repartir(int n) {
        int filas = Math.max(1, Math.min(4, (n + 6) / 7));
        int[] bases = switch (filas) {
            case 1 -> new int[]{19};
            case 2 -> new int[]{19, 28};
            case 3 -> new int[]{10, 19, 28};
            default -> new int[]{10, 19, 28, 37};
        };
        int[] out = new int[Math.min(n, filas * 7)];
        int puestos = 0;
        for (int f = 0; f < filas && puestos < out.length; f++) {
            int quedan = out.length - puestos;
            int filasQuedan = filas - f;
            int enEsta = Math.min(7, (quedan + filasQuedan - 1) / filasQuedan);
            for (int o : PATRON[enEsta]) out[puestos++] = bases[f] + o;
        }
        return out;
    }

    private static void rellenar(Inventory inv, Material cristal) {
        ItemStack relleno = boton(cristal, " ", null);
        for (int s = 0; s < inv.getSize(); s++) {
            if (inv.getItem(s) == null) inv.setItem(s, relleno);
        }
    }

    private static ItemStack boton(Material material, String nombre, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(nombre));
            if (lore != null) {
                List<String> pintado = new ArrayList<>();
                for (String l : lore) pintado.add(ColorUtil.colorize(l));
                meta.setLore(pintado);
            }
            for (ItemFlag f : ItemFlag.values()) meta.addItemFlags(f);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** 15000 -> "15.000" */
    public static String conPuntos(long n) {
        return String.format("%,d", n).replace(',', '.');
    }
}
