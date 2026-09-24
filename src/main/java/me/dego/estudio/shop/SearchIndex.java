package me.dego.estudio.shop;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Índice de búsqueda de la tienda.
 *
 * Se construye leyendo los menús ya cargados, así que no hay ningún archivo
 * extra que mantener sincronizado: si añades un menú nuevo a shops/, aparece
 * en la búsqueda en cuanto hagas /estudioreload.
 *
 * Qué se indexa:
 *   - Los menús de ítem (los que acaban en '_menu'): se coge el nombre del
 *     ítem que está en el centro, el del slot 13.
 *   - Las páginas de familia: se coge el trozo del título que va detrás de
 *     la flecha '➔', para que "escaleras" o "bayas" también encuentren algo.
 */
public class SearchIndex {

    /** Una entrada del índice: cómo se llama, qué menú abre y con qué ítem se dibuja. */
    public record Entrada(String nombre, String nombreNormalizado, String menu, ShopItem icono) {}

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");        // <#FFFFFF>, <b>, <gradient:...>
    private static final Pattern LEGACY = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}");
    private static final int SLOT_CENTRAL = 13;

    private final List<Entrada> entradas = new ArrayList<>();

    /** Deja el texto listo para comparar: sin colores, sin tildes y en minúsculas. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String t = TAGS.matcher(texto).replaceAll("");
        t = LEGACY.matcher(t).replaceAll("");
        t = Normalizer.normalize(t, Normalizer.Form.NFD);
        t = ACENTOS.matcher(t).replaceAll("");
        return t.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    /** Quita solo el formato, conservando tildes y mayúsculas (para enseñarlo al jugador). */
    public static String limpiar(String texto) {
        if (texto == null) return "";
        return LEGACY.matcher(TAGS.matcher(texto).replaceAll("")).replaceAll("").trim();
    }

    public void reconstruir(Map<String, ShopMenu> menus) {
        entradas.clear();
        for (Map.Entry<String, ShopMenu> e : menus.entrySet()) {
            String comando = e.getKey();
            ShopMenu menu = e.getValue();

            if (comando.endsWith("_menu")) {
                ShopItem central = itemEnSlot(menu, SLOT_CENTRAL);
                if (central == null) continue;
                String nombre = limpiar(central.getDisplayName());
                if (nombre.isEmpty()) continue;
                anadir(nombre, comando, central);

            } else if (!comando.startsWith("shop") && !comando.endsWith("_compra")) {
                // De una familia paginada solo se indexa la primera pagina: si
                // no, "escaleras" devolveria tres veces lo mismo.
                if (esPaginaSecundaria(comando, menus)) continue;
                // página de familia: "» 💰 Minería ➔ Fósiles 💰 «"  ->  "Fósiles"
                String titulo = limpiar(menu.getTitle());
                int flecha = titulo.lastIndexOf('➔');
                if (flecha >= 0) titulo = titulo.substring(flecha + 1);
                titulo = titulo.replace("»", "").replace("«", "").replace("💰", "").trim();
                titulo = titulo.replaceAll("\\(\\d+/\\d+\\)$", "").trim();   // quita el "(2/3)"
                if (titulo.isEmpty()) continue;
                anadir(titulo, comando, itemEnSlot(menu, 19));
            }
        }
        entradas.sort(Comparator.comparing(Entrada::nombre));
    }

    private void anadir(String nombre, String menu, ShopItem icono) {
        entradas.add(new Entrada(nombre, normalizar(nombre), menu, icono));
    }

    /** 'bl_escaleras3' es pagina secundaria si existe 'bl_escaleras'. */
    private boolean esPaginaSecundaria(String comando, Map<String, ShopMenu> menus) {
        int i = comando.length();
        while (i > 0 && Character.isDigit(comando.charAt(i - 1))) i--;
        return i < comando.length() && menus.containsKey(comando.substring(0, i));
    }

    private ShopItem itemEnSlot(ShopMenu menu, int slot) {
        for (ShopItem it : menu.getItems().values()) {
            if (it.getSlots().contains(slot)) return it;
        }
        return null;
    }

    /**
     * Busca por texto. Ordena por lo bien que encaja: primero lo que es
     * exactamente lo que has escrito, luego lo que empieza igual, y por
     * último lo que lo contiene en algún sitio.
     */
    public List<Entrada> buscar(String texto) {
        String q = normalizar(texto);
        List<Entrada> out = new ArrayList<>();
        if (q.isEmpty()) return out;

        for (Entrada e : entradas) {
            if (puntuar(e.nombreNormalizado(), q) > 0) out.add(e);
        }
        out.sort(Comparator
                .comparingInt((Entrada e) -> -puntuar(e.nombreNormalizado(), q))
                .thenComparing(Entrada::nombre));
        return out;
    }

    private static int puntuar(String nombre, String consulta) {
        if (nombre.equals(consulta)) return 3;
        if (nombre.startsWith(consulta)) return 2;
        if (nombre.contains(consulta)) return 1;
        return 0;
    }

    public int tamano() { return entradas.size(); }
}
