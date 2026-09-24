package me.dego.estudio.crates;

import me.dego.estudio.dependences.VaultEconomy;
import me.dego.estudio.shop.ColorUtil;
import me.dego.estudio.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Núcleo del sistema de cajas.
 *
 * - Carga todos los .yml de la carpeta crates/ al arrancar.
 * - Guarda quién tiene qué llaves (son virtuales, no ocupan inventario).
 * - Abre una caja: gasta la llave PRIMERO, sortea, anima y entrega.
 */
public class CrateManager {

    private final Plugin plugin;
    private final CrateDataBase datos;
    private final VaultEconomy economia;
    private final ShopManager tienda;
    private final Random random = new Random();

    private final Map<String, Crate> cajas = new LinkedHashMap<>();
    private final PokemonPools pokemon = new PokemonPools();

    // Como se le entrega un Pokemon al jugador. Sale de config.yml para que se
    // pueda cambiar sin recompilar si el hibrido usa otro comando.
    private String comandoPokemon = "givepokemonother %jugador% %pokemon% level=%nivel%%shiny%";
    private String sufijoShiny = " shiny=yes";

    // Quién está viendo una ruleta ahora mismo: evita abrir dos a la vez y
    // permite devolver la llave si se desconecta a mitad.
    private final Map<UUID, CrateAnimation> girando = new HashMap<>();

    public CrateManager(Plugin plugin, CrateDataBase datos, VaultEconomy economia, ShopManager tienda) {
        this.plugin = plugin;
        this.datos = datos;
        this.economia = economia;
        this.tienda = tienda;
    }

    public Plugin getPlugin() { return plugin; }
    public CrateDataBase getDatos() { return datos; }
    public ShopManager getTienda() { return tienda; }
    public VaultEconomy getEconomia() { return economia; }
    public Collection<Crate> getCajas() { return cajas.values(); }
    public PokemonPools getPokemon() { return pokemon; }
    public String getComandoPokemon() { return comandoPokemon; }
    public String getSufijoShiny() { return sufijoShiny; }

    /** Un premio al azar de esa caja, con la especie y el shiny ya decididos. */
    public CratePrize sortear(Crate caja) { return caja.sortear(random, pokemon); }
    public Crate getCaja(String id) { return id == null ? null : cajas.get(id.toLowerCase()); }
    public boolean estaGirando(UUID uuid) { return girando.containsKey(uuid); }
    public CrateAnimation giroDe(UUID uuid) { return girando.get(uuid); }
    void terminarGiro(UUID uuid) { girando.remove(uuid); }

    // ============================================================ carga

    /** Carga (o recarga) todos los YAML de la carpeta crates/. */
    public void cargarCajas() {
        cajas.clear();

        // Sin esto, editar config.yml no sirve de nada: getConfig() devuelve la
        // copia que Bukkit cargo al arrancar, no lo que hay en el disco.
        plugin.reloadConfig();

        if (!plugin.getConfig().isSet("cajas.comando_pokemon")) {
            plugin.getLogger().warning("config.yml no tiene 'cajas.comando_pokemon'.");
            plugin.getLogger().warning("Comprueba que has editado plugins/Estudio/config.yml (NO el del");
            plugin.getLogger().warning("proyecto) y que el bloque 'cajas:' esta bien indentado.");
        }
        comandoPokemon = plugin.getConfig().getString("cajas.comando_pokemon", comandoPokemon);
        sufijoShiny = plugin.getConfig().getString("cajas.sufijo_shiny", sufijoShiny);
        plugin.getLogger().info("Comando para dar Pokemon: " + comandoPokemon);
        plugin.getLogger().info("Sufijo shiny: '" + sufijoShiny + "'");

        File carpeta = new File(plugin.getDataFolder(), "crates");
        if (!carpeta.exists()) {
            carpeta.mkdirs();
            plugin.getLogger().warning("Carpeta crates/ creada vacía. Coloca ahí los .yml de las cajas.");
            return;
        }

        File[] archivos = carpeta.listFiles((dir, name) -> name.endsWith(".yml"));
        if (archivos == null) return;

        for (File f : archivos) {
            try {
                Crate caja = Crate.fromConfig(YamlConfiguration.loadConfiguration(f));
                if (caja == null) {
                    plugin.getLogger().warning("La caja " + f.getName() + " no tiene 'id' o no tiene premios, se ignora.");
                    continue;
                }
                cajas.put(caja.getId(), caja);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error cargando la caja " + f.getName(), e);
            }
        }
        pokemon.cargar(new File(carpeta, "pokemon.yml"), plugin.getLogger());
        avisarDeListasQueFaltan();
        plugin.getLogger().info("Cargadas " + cajas.size() + " cajas desde crates/");
    }

    // ============================================================ llaves

    public int llavesDe(Player p, String caja) { return datos.getLlaves(p.getUniqueId(), caja); }

    /** Da llaves y avisa al jugador si está conectado. */
    public void darLlaves(UUID uuid, String cajaId, int cuantas) {
        Crate caja = getCaja(cajaId);
        if (caja == null || cuantas == 0) return;
        int total = datos.darLlaves(uuid, caja.getId(), cuantas);

        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        if (cuantas > 0) {
            p.sendMessage(ColorUtil.colorize("<#3DCC04>Has recibido <#DBB30F>" + cuantas + "x </#DBB30F>")
                    + caja.getLlaveColoreada() + ColorUtil.colorize("<#3DCC04>. Ahora tienes <#DBB30F>"
                    + total + "</#DBB30F>."));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
        }
    }

    // ============================================================ abrir

    /**
     * Intenta abrir una caja. Devuelve false (y explica por qué) si no se puede.
     *
     * El orden importa: se gasta la llave ANTES de sortear y de abrir la
     * animación, para que ni una desconexión ni un doble clic puedan dar dos
     * premios por una sola llave.
     */
    public boolean abrir(Player player, Crate caja) {
        UUID uuid = player.getUniqueId();

        if (girando.containsKey(uuid)) {
            player.sendMessage(ColorUtil.colorize("<#FF3D3D>Espera a que termine la caja que ya tienes abierta."));
            return false;
        }

        if (!datos.gastarLlave(uuid, caja.getId())) {
            player.sendMessage(ColorUtil.colorize("<#FF3D3D>No tienes ninguna ") + caja.getLlaveColoreada()
                    + ColorUtil.colorize("<#FF3D3D>."));
            player.sendMessage(ColorUtil.colorize("<#FFB400>→</#FFB400> <gradient:#F5B845:#F5A102>"
                    + "Cómprala en la tienda con <#DBB30F>/llaves</#DBB30F>.</gradient>"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        CratePrize premio = sortear(caja);
        CrateAnimation animacion = new CrateAnimation(this, player, caja, premio);
        girando.put(uuid, animacion);
        animacion.empezar();
        return true;
    }

    /** Avisa al arrancar si alguna caja pide una lista de Pokemon que no existe. */
    private void avisarDeListasQueFaltan() {
        for (Crate c : cajas.values()) {
            for (CratePrize p : c.getPremios()) {
                if (p.getTipo() != CratePrize.Tipo.POKEMON) continue;
                if (pokemon.get(p.getLista()) == null) {
                    plugin.getLogger().warning("La caja '" + c.getId() + "' usa la lista de Pokemon '"
                            + p.getLista() + "', que no esta en crates/pokemon.yml");
                }
            }
        }
    }

    /** Lo que pasa cuando la ruleta se para: entrega, registro y anuncio. */
    void entregarPremio(Player player, Crate caja, CratePrize premio) {
        premio.entregar(player, this);

        player.sendMessage(" ");
        player.sendMessage(ColorUtil.colorize("<#4D4D4D>◇</#4D4D4D> ") + caja.getNombreColoreado());
        player.sendMessage(ColorUtil.colorize("<#9E9E9E>Has ganado: ") + premio.getNombreColoreado());
        player.sendMessage(" ");
        player.playSound(player.getLocation(), caja.getSonidoPremio(), 1f, 1f);

        datos.registrarApertura(player.getUniqueId(), player.getName(), caja.getId(),
                premio.getNombre(), premio.getValor());

        if (premio.getValor() >= caja.getAnuncioDesde()) {
            String linea = ColorUtil.colorize("<#DBB30F>★</#DBB30F> <#FFFFFF>" + player.getName()
                    + "</#FFFFFF> <#9E9E9E>ha sacado </#9E9E9E>") + premio.getNombreColoreado()
                    + ColorUtil.colorize("<#9E9E9E> de la </#9E9E9E>") + caja.getNombreColoreado();
            for (Player online : Bukkit.getOnlinePlayers()) online.sendMessage(linea);
        }
    }

    /** Los cofres del spawn que son esta caja, para /cajas lista. */
    public List<Location> cofresDe(String cajaId) {
        List<Location> out = new ArrayList<>();
        for (Map.Entry<String, String> e : datos.getBloques().entrySet()) {
            if (!e.getValue().equalsIgnoreCase(cajaId)) continue;
            String[] p = e.getKey().split(":");
            if (p.length != 4) continue;
            org.bukkit.World w = Bukkit.getWorld(p[0]);
            if (w == null) continue;
            out.add(new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
        }
        return out;
    }
}
