package me.dego.estudio.holograms;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Un holograma.
 *
 * Por dentro es una entidad TextDisplay, que es la forma moderna de hacer esto
 * desde 1.19.4: una sola entidad para todas las lineas, con escala de verdad,
 * fondo configurable y sin necesidad de packets ni de NMS.
 *
 * Esta clase es solo los datos + como se aplican a la entidad. De crearla,
 * borrarla y guardarla se encarga HologramManager.
 */
public class Hologram {

    /** El texto tal y como lo escribio el admin, con los tags de color de la tienda. */
    private final List<String> lineas = new ArrayList<>();

    private final String id;
    private Location ubicacion;

    private float tamano = 1.0f;                                  // escala
    private boolean sombra = true;                                // sombreado del texto
    private String fondo = null;                                  // null = el de por defecto
    private float rotacion = 0f;                                  // yaw,   solo si seguir = NO
    private float inclinacion = 0f;                               // pitch, solo si seguir = NO
    private Display.Billboard seguir = Display.Billboard.CENTER;  // si mira al jugador
    private Display.Brightness luz = null;                        // null = luz del sitio
    private int opacidad = -1;                                    // -1 = por defecto, si no 1..100
    private TextDisplay.TextAlignment alineacion = TextDisplay.TextAlignment.CENTER;
    private float rango = 1.0f;                                   // distancia a la que se ve
    private boolean traspasar = false;                            // se ve a traves de bloques
    private int ancho = 200;                                      // ancho maximo de linea

    public Hologram(String id, Location ubicacion) {
        this.id = id.toLowerCase();
        this.ubicacion = ubicacion;
    }

    // ============================================================ aplicar

    /** Vuelca todos los ajustes sobre la entidad. Se llama al crearla y en cada cambio. */
    public void aplicar(TextDisplay d) {
        d.setText(textoColoreado());
        d.setBillboard(seguir);
        d.setShadowed(sombra);
        d.setSeeThrough(traspasar);
        d.setAlignment(alineacion);
        d.setLineWidth(ancho);
        d.setViewRange(rango);
        d.setBrightness(luz);            // null = que use la luz del entorno

        aplicarFondo(d);
        aplicarOpacidad(d);

        // La escala va dentro de la Transformation. Sin rotaciones: la rotacion
        // del holograma se hace con setRotation(), que es mas facil de entender
        // que un cuaternion y ademas es lo que el cliente respeta cuando el
        // billboard esta en FIXED.
        d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(tamano, tamano, tamano),
                new Quaternionf()));

        // OJO: esto solo se nota con 'seguir: no'. Con cualquier otro valor es
        // el cliente quien decide hacia donde mira, y machaca la rotacion.
        d.setRotation(rotacion, inclinacion);
    }

    private void aplicarFondo(TextDisplay d) {
        if (fondo == null) {                       // el gris translucido de siempre
            d.setDefaultBackground(true);
            return;
        }
        d.setDefaultBackground(false);
        if (fondo.equalsIgnoreCase("no")) {        // sin fondo, solo las letras
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            return;
        }
        Color c = colorDesdeTexto(fondo);
        d.setBackgroundColor(c != null ? c : Color.fromARGB(0, 0, 0, 0));
    }

    private void aplicarOpacidad(TextDisplay d) {
        if (opacidad < 0) {
            d.setTextOpacity((byte) -1);           // -1 es "la de por defecto"
            return;
        }
        // De 0-100 a 0-255. Por debajo de 26 el cliente lo trata como invisible,
        // asi que cualquier valor visible se sube a ese minimo.
        int v = Math.round(opacidad * 255f / 100f);
        if (opacidad > 0 && v < 26) v = 26;
        d.setTextOpacity((byte) Math.min(255, Math.max(0, v)));
    }

    /** Acepta #RRGGBB y #AARRGGBB. Devuelve null si no se entiende. */
    public static Color colorDesdeTexto(String texto) {
        if (texto == null) return null;
        String h = texto.startsWith("#") ? texto.substring(1) : texto;
        try {
            if (h.length() == 6) {
                int rgb = Integer.parseInt(h, 16);
                return Color.fromARGB(255, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
            if (h.length() == 8) {
                long argb = Long.parseLong(h, 16);
                return Color.fromARGB((int) ((argb >> 24) & 0xFF), (int) ((argb >> 16) & 0xFF),
                        (int) ((argb >> 8) & 0xFF), (int) (argb & 0xFF));
            }
        } catch (NumberFormatException ignored) { }
        return null;
    }

    /** Las lineas ya pasadas por los colores de la tienda y unidas con saltos de linea. */
    public String textoColoreado() {
        List<String> pintadas = new ArrayList<>();
        for (String l : lineas) pintadas.add(ColorUtil.colorize(l));
        return String.join("\n", pintadas);
    }

    // ============================================================ datos

    public String getId() { return id; }
    public Location getUbicacion() { return ubicacion; }
    public void setUbicacion(Location l) { this.ubicacion = l; }

    public List<String> getLineas() { return lineas; }
    public void setLineas(List<String> nuevas) {
        lineas.clear();
        lineas.addAll(nuevas);
        if (lineas.isEmpty()) lineas.add(" ");
    }

    public float getTamano() { return tamano; }
    public void setTamano(float v) { this.tamano = Math.max(0.05f, Math.min(64f, v)); }

    public boolean isSombra() { return sombra; }
    public void setSombra(boolean v) { this.sombra = v; }

    public String getFondo() { return fondo; }
    public void setFondo(String v) { this.fondo = v; }

    public float getRotacion() { return rotacion; }
    public void setRotacion(float v) { this.rotacion = v; }

    public float getInclinacion() { return inclinacion; }
    public void setInclinacion(float v) { this.inclinacion = Math.max(-90f, Math.min(90f, v)); }

    public Display.Billboard getSeguir() { return seguir; }
    public void setSeguir(Display.Billboard v) { this.seguir = v; }

    public Display.Brightness getLuz() { return luz; }
    public void setLuz(Display.Brightness v) { this.luz = v; }

    public int getOpacidad() { return opacidad; }
    public void setOpacidad(int v) { this.opacidad = v < 0 ? -1 : Math.min(100, v); }

    public TextDisplay.TextAlignment getAlineacion() { return alineacion; }
    public void setAlineacion(TextDisplay.TextAlignment v) { this.alineacion = v; }

    public float getRango() { return rango; }
    public void setRango(float v) { this.rango = Math.max(0.1f, Math.min(20f, v)); }

    public boolean isTraspasar() { return traspasar; }
    public void setTraspasar(boolean v) { this.traspasar = v; }

    public int getAncho() { return ancho; }
    public void setAncho(int v) { this.ancho = Math.max(1, Math.min(1000, v)); }

    /** Copia todos los ajustes (menos id, sitio y texto) de otro holograma. */
    public void copiarAjustesDe(Hologram o) {
        this.tamano = o.tamano;
        this.sombra = o.sombra;
        this.fondo = o.fondo;
        this.rotacion = o.rotacion;
        this.inclinacion = o.inclinacion;
        this.seguir = o.seguir;
        this.luz = o.luz;
        this.opacidad = o.opacidad;
        this.alineacion = o.alineacion;
        this.rango = o.rango;
        this.traspasar = o.traspasar;
        this.ancho = o.ancho;
    }
}
