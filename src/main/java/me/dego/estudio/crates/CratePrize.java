package me.dego.estudio.crates;

import me.dego.estudio.dependences.VaultEconomy;
import me.dego.estudio.shop.ColorUtil;
import me.dego.estudio.shop.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Un premio de una caja. Hay tres tipos:
 *
 *   item     un ItemStack, guardado en base64 igual que en la tienda (el
 *            servidor híbrido no lee bien los ítems de mods por su material).
 *   dinero   se paga por Vault.
 *   pokemon  se elige una especie al azar de una lista de crates/pokemon.yml y
 *            se entrega ejecutando un comando en la consola.
 *
 * Los premios de tipo 'pokemon' pasan por resolver() ANTES de la ruleta: así la
 * especie y el shiny ya están decididos cuando empieza la animación, y lo que
 * se ve girando es de verdad lo que puede tocar.
 */
public class CratePrize {

    public enum Tipo { ITEM, DINERO, POKEMON }

    private final Tipo tipo;
    private final int peso;
    private final String base64;     // null si es dinero
    private final Material material; // alternativa a base64, por si se define a mano
    private final int cantidad;
    private final String nombre;     // en formato de la tienda (<gradient:...>, <#hex>...)
    private final int valor;         // lo que costaría en la tienda: balance, vista previa y anuncio

    // solo para tipo POKEMON
    private final String lista;
    private final int nivel;
    private final double shinyProb;              // 0-100
    private final PokemonPools.Especie especie;  // null mientras no esté resuelto
    private final boolean shiny;

    private CratePrize(Tipo tipo, int peso, String base64, Material material, int cantidad,
                       String nombre, int valor, String lista, int nivel, double shinyProb,
                       PokemonPools.Especie especie, boolean shiny) {
        this.tipo = tipo;
        this.peso = peso;
        this.base64 = base64;
        this.material = material;
        this.cantidad = cantidad;
        this.nombre = nombre;
        this.valor = valor;
        this.lista = lista;
        this.nivel = nivel;
        this.shinyProb = shinyProb;
        this.especie = especie;
        this.shiny = shiny;
    }

    public static CratePrize fromConfig(ConfigurationSection s) {
        String tipoRaw = s.getString("tipo", "item").toLowerCase();
        Tipo tipo = switch (tipoRaw) {
            case "dinero" -> Tipo.DINERO;
            case "pokemon" -> Tipo.POKEMON;
            default -> Tipo.ITEM;
        };
        int peso = Math.max(1, s.getInt("peso", 1));
        int cantidad = Math.max(1, s.getInt("cantidad", 1));
        String nombre = s.getString("nombre", "Premio");
        int valor = s.getInt("valor", 0);

        String b64 = s.getString("custom_item");
        Material mat = null;
        if (b64 == null) {
            String raw = s.getString("material");
            if (raw != null) mat = Material.matchMaterial(raw.toUpperCase());
        }
        return new CratePrize(tipo, peso, b64, mat, cantidad, nombre, valor,
                s.getString("lista"), s.getInt("nivel", 30), s.getDouble("shiny", 0.0),
                null, false);
    }

    /**
     * Decide lo que haya que decidir antes de enseñar el premio. Para un ítem o
     * dinero no hay nada que decidir y se devuelve el mismo objeto; para un
     * Pokémon se saca la especie de su lista y se tira el shiny.
     */
    public CratePrize resolver(Random random, PokemonPools pools) {
        if (tipo != Tipo.POKEMON) return this;
        PokemonPools.Especie e = pools == null ? null : pools.aleatoria(lista, random);
        if (e == null) return this;   // lista mal puesta: se avisa al entregar
        boolean esShiny = shinyProb > 0 && random.nextDouble() * 100.0 < shinyProb;
        return new CratePrize(tipo, peso, base64, material, cantidad, nombre, valor,
                lista, nivel, shinyProb, e, esShiny);
    }

    public Tipo getTipo() { return tipo; }
    public int getPeso() { return peso; }
    public int getCantidad() { return cantidad; }
    public int getValor() { return valor; }
    public String getLista() { return lista; }
    public int getNivel() { return nivel; }
    public double getShinyProb() { return shinyProb; }
    public boolean esShiny() { return shiny; }
    public PokemonPools.Especie getEspecie() { return especie; }

    /**
     * El nombre del premio. Si es un Pokémon ya resuelto, el de la especie que
     * ha tocado; si no, el que ponga el YAML (que es lo que se ve en la vista
     * previa: "Pokémon común", "Legendario shiny"...).
     */
    public String getNombre() {
        if (tipo != Tipo.POKEMON || especie == null) return nombre;
        return shiny
                ? "<gradient:#F5E08F:#DBB30F><b>✦ " + especie.nombre() + " shiny</b></gradient>"
                : "<#FFFFFF><b>" + especie.nombre() + "</b>";
    }

    /** El nombre ya convertido a códigos §, listo para chat o para un display_name. */
    public String getNombreColoreado() { return ColorUtil.colorize(getNombre()); }

    /**
     * El ItemStack "de verdad" del premio, con la cantidad pedida y sin tocar el
     * nombre. Devuelve null si el ítem no se pudo reconstruir (mod desinstalado).
     */
    private ItemStack plantilla() {
        if (tipo == Tipo.DINERO && base64 == null && material == null) return new ItemStack(Material.GOLD_NUGGET);
        if (base64 != null) return ItemSerializer.deserialize(base64);
        if (material != null) return new ItemStack(material);
        return null;
    }

    /**
     * Cómo se dibuja el premio dentro de un menú (la ruleta y la vista previa).
     * Aquí sí se le pone nombre y descripción, así que NUNCA se entrega este
     * ItemStack al jugador: para eso está entregar().
     */
    public ItemStack icono(List<String> loreExtra) {
        ItemStack it = plantilla();
        if (it == null) {
            it = new ItemStack(Material.BARRIER);
        } else {
            it = it.clone();
        }
        int aEnsenar = tipo == Tipo.POKEMON ? 1 : cantidad;
        it.setAmount(Math.max(1, Math.min(aEnsenar, it.getMaxStackSize() > 1 ? 64 : 1)));

        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getNombreColoreado());
            List<String> lore = new ArrayList<>();
            if (loreExtra != null) for (String l : loreExtra) lore.add(ColorUtil.colorize(l));
            meta.setLore(lore);
            for (ItemFlag f : ItemFlag.values()) meta.addItemFlags(f);
            it.setItemMeta(meta);
        }
        return it;
    }

    public ItemStack icono() { return icono(null); }

    /**
     * Le da el premio al jugador. Lo que no quepa en el inventario cae al suelo,
     * para que nunca se pierda una llave gastada.
     */
    public void entregar(Player player, CrateManager manager) {
        switch (tipo) {
            case DINERO -> {
                VaultEconomy eco = manager.getEconomia();
                if (eco != null && eco.isEnabled()) eco.giveMoney(player, cantidad);
                else manager.getPlugin().getLogger().warning(
                        "Premio de dinero para " + player.getName() + " pero Vault no está enganchado.");
            }
            case POKEMON -> entregarPokemon(player, manager);
            case ITEM -> entregarItem(player, manager);
        }
    }

    private void entregarPokemon(Player player, CrateManager manager) {
        if (especie == null) {
            player.sendMessage("§cEse premio está mal configurado. Avisa a un administrador.");
            manager.getPlugin().getLogger().warning("Premio de Pokémon sin especie: la lista '"
                    + lista + "' no existe en crates/pokemon.yml");
            return;
        }
        // Se repite el comando si el premio son varios Pokémon (cantidad > 1).
        for (int i = 0; i < Math.max(1, cantidad); i++) {
            String comando = manager.getComandoPokemon()
                    .replace("%jugador%", player.getName())
                    .replace("%pokemon%", especie.id())
                    .replace("%nivel%", String.valueOf(nivel))
                    .replace("%shiny%", shiny ? manager.getSufijoShiny() : "");

            // Se deja constancia del comando EXACTO. Si algo falla, en la consola
            // esta la linea que se ejecuto, lista para copiar y pegar y probarla.
            manager.getPlugin().getLogger().info("[Cajas] " + comando);

            boolean encontrado = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando);
            if (!encontrado) {
                manager.getPlugin().getLogger().warning("[Cajas] El servidor no conoce ese comando. "
                        + "Cambia 'cajas.comando_pokemon' en config.yml y haz /estudioreload.");
                player.sendMessage("§cNo se te ha podido dar el Pokémon. Avisa a un administrador.");
            }
        }
    }

    private void entregarItem(Player player, CrateManager manager) {
        ItemStack base = plantilla();
        if (base == null) {
            player.sendMessage("§cEl premio ya no existe en el servidor. Avisa a un administrador.");
            return;
        }
        // Se reparte en montones legales: un premio de 4 élitros son 4 montones de 1,
        // y uno de 96 lingotes son dos montones de 64 y 32.
        int restante = cantidad;
        int porMonton = Math.max(1, base.getMaxStackSize());
        while (restante > 0) {
            int n = Math.min(restante, porMonton);
            restante -= n;
            ItemStack monton = base.clone();
            monton.setAmount(n);
            Map<Integer, ItemStack> sobra = player.getInventory().addItem(monton);
            for (ItemStack s : sobra.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), s);
            }
        }
    }
}
