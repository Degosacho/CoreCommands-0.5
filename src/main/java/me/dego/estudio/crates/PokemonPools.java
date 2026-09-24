package me.dego.estudio.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Las listas de Pokémon que pueden salir en las cajas.
 *
 * Salen de crates/pokemon.yml, que se genera leyendo los propios .jar del
 * modpack, así que ahí solo hay especies que existen de verdad en el servidor.
 * Está aparte de las cajas porque las listas son largas y las comparten varias.
 *
 * Formato:
 *   listas:
 *     comunes:
 *       - 'pikachu|Pikachu'      <- identificador para el comando | nombre bonito
 *       - 'eevee|Eevee'
 */
public class PokemonPools {

    /** Una especie: cómo se llama para el comando y cómo se le enseña al jugador. */
    public record Especie(String id, String nombre) {}

    private final Map<String, List<Especie>> listas = new LinkedHashMap<>();

    public void cargar(File archivo, Logger log) {
        listas.clear();
        if (!archivo.exists()) {
            log.warning("No existe crates/pokemon.yml; las cajas de Pokémon no funcionarán.");
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection raiz = cfg.getConfigurationSection("listas");
        if (raiz == null) {
            log.warning("crates/pokemon.yml no tiene la sección 'listas'.");
            return;
        }
        int total = 0;
        for (String nombre : raiz.getKeys(false)) {
            List<Especie> especies = new ArrayList<>();
            for (String linea : raiz.getStringList(nombre)) {
                int barra = linea.indexOf('|');
                String id = (barra < 0 ? linea : linea.substring(0, barra)).trim();
                if (id.isEmpty()) continue;
                String bonito = barra < 0 ? id : linea.substring(barra + 1).trim();
                especies.add(new Especie(id, bonito));
            }
            if (especies.isEmpty()) {
                log.warning("La lista de Pokémon '" + nombre + "' está vacía.");
                continue;
            }
            listas.put(nombre.toLowerCase(), especies);
            total += especies.size();
        }
        log.info("Cargadas " + listas.size() + " listas de Pokémon (" + total + " especies) desde crates/pokemon.yml");
    }

    public List<Especie> get(String lista) {
        return lista == null ? null : listas.get(lista.toLowerCase());
    }

    /** Una especie al azar de esa lista, o null si la lista no existe. */
    public Especie aleatoria(String lista, Random random) {
        List<Especie> l = get(lista);
        if (l == null || l.isEmpty()) return null;
        return l.get(random.nextInt(l.size()));
    }

    public int tamano(String lista) {
        List<Especie> l = get(lista);
        return l == null ? 0 : l.size();
    }

    public Map<String, List<Especie>> getListas() { return listas; }
}
