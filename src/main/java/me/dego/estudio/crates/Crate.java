package me.dego.estudio.crates;

import me.dego.estudio.shop.ColorUtil;
import me.dego.estudio.shop.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Una caja: su llave, su precio, su aspecto y su tabla de premios. */
public class Crate {

    private final String id;
    private final String nombre;        // '<gradient:...><b>Caja Bonguri</b></gradient>'
    private final String nombrePlano;   // 'Caja Bonguri', para mensajes de consola
    private final String llave;         // '<gradient:...><b>Llave Bonguri</b></gradient>'
    private final int precio;
    private final String iconoBase64;
    private final Material cristal;
    private final String menuCompra;    // open_command del menú de /shop que vende la llave
    private final int anuncioDesde;     // valor a partir del cual se anuncia al servidor
    private final Sound sonidoGiro;
    private final Sound sonidoPremio;
    private final List<String> descripcion;

    private final List<CratePrize> premios = new ArrayList<>();
    private int pesoTotal = 0;

    private Crate(String id, String nombre, String nombrePlano, String llave, int precio,
                  String iconoBase64, Material cristal, String menuCompra, int anuncioDesde,
                  Sound sonidoGiro, Sound sonidoPremio, List<String> descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.nombrePlano = nombrePlano;
        this.llave = llave;
        this.precio = precio;
        this.iconoBase64 = iconoBase64;
        this.cristal = cristal;
        this.menuCompra = menuCompra;
        this.anuncioDesde = anuncioDesde;
        this.sonidoGiro = sonidoGiro;
        this.sonidoPremio = sonidoPremio;
        this.descripcion = descripcion;
    }

    public static Crate fromConfig(ConfigurationSection root) {
        String id = root.getString("id");
        if (id == null || id.isBlank()) return null;

        Crate caja = new Crate(
                id.toLowerCase(),
                root.getString("nombre", id),
                root.getString("nombre_plano", id),
                root.getString("llave", "Llave"),
                root.getInt("precio", 0),
                root.getString("icono"),
                material(root.getString("cristal", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE),
                root.getString("menu_compra", ""),
                root.getInt("anuncio_desde", Integer.MAX_VALUE),
                sonido(root.getString("sonido_giro"), Sound.BLOCK_NOTE_BLOCK_HAT),
                sonido(root.getString("sonido_premio"), Sound.ENTITY_PLAYER_LEVELUP),
                root.getStringList("descripcion")
        );

        ConfigurationSection lista = root.getConfigurationSection("premios");
        if (lista != null) {
            for (String clave : lista.getKeys(false)) {
                ConfigurationSection s = lista.getConfigurationSection(clave);
                if (s == null) continue;
                CratePrize p = CratePrize.fromConfig(s);
                caja.premios.add(p);
                caja.pesoTotal += p.getPeso();
            }
        }
        return caja.premios.isEmpty() ? null : caja;
    }

    private static Material material(String nombre, Material porDefecto) {
        Material m = nombre == null ? null : Material.matchMaterial(nombre.toUpperCase());
        return m != null ? m : porDefecto;
    }

    private static Sound sonido(String nombre, Sound porDefecto) {
        if (nombre == null) return porDefecto;
        try {
            return Sound.valueOf(nombre.toUpperCase().replace('.', '_'));
        } catch (IllegalArgumentException e) {
            return porDefecto;
        }
    }

    /**
     * Saca un premio al azar respetando los pesos de la tabla, ya resuelto: si
     * toca un Pokemon, la especie y el shiny vienen decididos de aqui.
     */
    public CratePrize sortear(Random random, PokemonPools pools) {
        int tirada = random.nextInt(pesoTotal);
        for (CratePrize p : premios) {
            tirada -= p.getPeso();
            if (tirada < 0) return p.resolver(random, pools);
        }
        return premios.get(premios.size() - 1).resolver(random, pools);
    }

    /** El ítem con el que se representa la caja en los menús. */
    public ItemStack icono() {
        ItemStack it = iconoBase64 != null ? ItemSerializer.deserialize(iconoBase64) : null;
        if (it == null) it = new ItemStack(Material.CHEST);
        return it.clone();
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getNombrePlano() { return nombrePlano; }
    public String getNombreColoreado() { return ColorUtil.colorize(nombre); }
    public String getLlave() { return llave; }
    public String getLlaveColoreada() { return ColorUtil.colorize(llave); }
    public int getPrecio() { return precio; }
    public Material getCristal() { return cristal; }
    public String getMenuCompra() { return menuCompra; }
    public int getAnuncioDesde() { return anuncioDesde; }
    public Sound getSonidoGiro() { return sonidoGiro; }
    public Sound getSonidoPremio() { return sonidoPremio; }
    public List<String> getDescripcion() { return descripcion; }
    public List<CratePrize> getPremios() { return premios; }
    public int getPesoTotal() { return pesoTotal; }

    /** Probabilidad de un premio en tanto por ciento, para la vista previa. */
    public double probabilidad(CratePrize p) {
        return pesoTotal == 0 ? 0 : (100.0 * p.getPeso()) / pesoTotal;
    }
}
