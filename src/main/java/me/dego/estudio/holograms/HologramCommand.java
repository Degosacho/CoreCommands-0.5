package me.dego.estudio.holograms;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /holo — crear y editar hologramas, todo por argumentos del comando.
 *
 * Los ajustes van como pares 'clave:valor' en cualquier orden, y se pueden
 * poner tanto al crear como al editar:
 *
 *   /holo crear tienda tamano:2 fondo:no seguir:si <gradient:#09AA00:#37C930><b>TIENDA</b></gradient>
 *   /holo editar tienda luz:15 sombra:no rotacion:90
 *
 * Para no confundir un ajuste con el texto, solo se trata como ajuste lo que
 * empieza por una de las claves conocidas seguida de dos puntos. Asi un texto
 * como '<gradient:#09AA00:#37C930>' nunca se confunde con un 'clave:valor',
 * aunque lleve dos puntos.
 */
public class HologramCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISO = "estudio.holo.admin";

    private static final List<String> CLAVES = List.of(
            "tamano", "sombra", "fondo", "rotacion", "inclinacion", "seguir",
            "luz", "opacidad", "alineacion", "rango", "traspasar", "ancho");

    private static final List<String> SUBCOMANDOS = List.of(
            "crear", "borrar", "editar", "texto", "linea", "anadir", "quitarlinea",
            "mover", "subir", "tp", "lista", "info", "copiar", "recargar", "ayuda");

    private final HologramManager manager;

    public HologramCommand(HologramManager manager) {
        this.manager = manager;
    }

    // ============================================================ entrada

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(PERMISO)) {
            sender.sendMessage(ColorUtil.colorize("<#FF3D3D>No puedes hacer eso!"));
            return true;
        }
        if (args.length == 0) { ayuda(sender); return true; }

        switch (norm(args[0])) {
            case "crear" -> crear(sender, args);
            case "borrar" -> borrar(sender, args);
            case "editar" -> editar(sender, args);
            case "texto" -> texto(sender, args);
            case "linea" -> linea(sender, args);
            case "anadir" -> anadir(sender, args);
            case "quitarlinea" -> quitarLinea(sender, args);
            case "mover" -> mover(sender, args);
            case "subir" -> subir(sender, args);
            case "tp" -> tp(sender, args);
            case "lista" -> lista(sender);
            case "info" -> info(sender, args);
            case "copiar" -> copiar(sender, args);
            case "recargar" -> {
                manager.getDatos().loadAll();
                manager.repintarTodos();
                ok(sender, "Recargados " + manager.lista().size() + " hologramas.");
            }
            default -> ayuda(sender);
        }
        return true;
    }

    // ============================================================ subcomandos

    private void crear(CommandSender s, String[] args) {
        Player p = jugador(s);
        if (p == null) return;
        if (args.length < 3) { error(s, "/holo crear <id> [ajustes] <texto>"); return; }

        String id = norm(args[1]);
        if (manager.get(id) != null) { error(s, "Ya existe un holograma que se llama «" + id + "»."); return; }

        // Los ajustes van pegados al principio; en cuanto aparece algo que no es
        // 'clave:valor' conocida, todo lo que queda es el texto.
        int i = 2;
        List<String> ajustes = new ArrayList<>();
        while (i < args.length && esAjuste(args[i])) ajustes.add(args[i++]);
        if (i >= args.length) { error(s, "Te falta el texto del holograma."); return; }

        Location donde = p.getEyeLocation();
        donde.setYaw(0f);
        donde.setPitch(0f);

        Hologram h = manager.crear(id, donde, partirTexto(unir(args, i)));
        for (String a : ajustes) aplicarAjuste(h, a, s);
        manager.refrescar(h);

        ok(s, "Holograma «" + id + "» creado. Editalo con /holo editar " + id + " <ajustes>");
    }

    private void borrar(CommandSender s, String[] args) {
        if (args.length < 2) { error(s, "/holo borrar <id>"); return; }
        if (!manager.borrar(norm(args[1]))) { error(s, "No existe «" + args[1] + "»."); return; }
        ok(s, "Holograma «" + norm(args[1]) + "» borrado.");
    }

    private void editar(CommandSender s, String[] args) {
        if (args.length < 3) { error(s, "/holo editar <id> <clave:valor> ..."); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;

        int cambiados = 0;
        for (int i = 2; i < args.length; i++) {
            if (aplicarAjuste(h, args[i], s)) cambiados++;
        }
        if (cambiados == 0) { error(s, "No has cambiado nada. Mira /holo ayuda."); return; }
        manager.refrescar(h);
        ok(s, "Cambiados " + cambiados + " ajustes de «" + h.getId() + "».");
    }

    private void texto(CommandSender s, String[] args) {
        if (args.length < 3) { error(s, "/holo texto <id> <texto>   (usa \\n para saltos de linea)"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        h.setLineas(partirTexto(unir(args, 2)));
        manager.refrescar(h);
        ok(s, "Texto cambiado (" + h.getLineas().size() + " lineas).");
    }

    private void linea(CommandSender s, String[] args) {
        if (args.length < 4) { error(s, "/holo linea <id> <numero> <texto>"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        int n = entero(args[2], -1);
        if (n < 1 || n > h.getLineas().size()) {
            error(s, "Ese holograma tiene " + h.getLineas().size() + " lineas; el numero va de 1 a " + h.getLineas().size() + ".");
            return;
        }
        List<String> nuevas = new ArrayList<>(h.getLineas());
        nuevas.set(n - 1, unir(args, 3));
        h.setLineas(nuevas);
        manager.refrescar(h);
        ok(s, "Linea " + n + " cambiada.");
    }

    private void anadir(CommandSender s, String[] args) {
        if (args.length < 3) { error(s, "/holo anadir <id> <texto>"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        List<String> nuevas = new ArrayList<>(h.getLineas());
        nuevas.addAll(partirTexto(unir(args, 2)));
        h.setLineas(nuevas);
        manager.refrescar(h);
        ok(s, "Anadida. Ahora tiene " + h.getLineas().size() + " lineas.");
    }

    private void quitarLinea(CommandSender s, String[] args) {
        if (args.length < 3) { error(s, "/holo quitarlinea <id> <numero>"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        int n = entero(args[2], -1);
        if (n < 1 || n > h.getLineas().size()) { error(s, "Numero de linea fuera de rango."); return; }
        List<String> nuevas = new ArrayList<>(h.getLineas());
        nuevas.remove(n - 1);
        h.setLineas(nuevas);
        manager.refrescar(h);
        ok(s, "Linea " + n + " quitada.");
    }

    private void mover(CommandSender s, String[] args) {
        Player p = jugador(s);
        if (p == null) return;
        if (args.length < 2) { error(s, "/holo mover <id>"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        Location donde = p.getEyeLocation();
        donde.setYaw(0f);
        donde.setPitch(0f);
        h.setUbicacion(donde);
        manager.refrescar(h);
        ok(s, "«" + h.getId() + "» movido a donde estas.");
    }

    private void subir(CommandSender s, String[] args) {
        if (args.length < 3) { error(s, "/holo subir <id> <bloques>   (admite negativos)"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        double d = decimal(args[2], Double.NaN);
        if (Double.isNaN(d)) { error(s, "«" + args[2] + "» no es un numero."); return; }
        h.getUbicacion().add(0, d, 0);
        manager.refrescar(h);
        ok(s, "Movido " + d + " bloques en vertical.");
    }

    private void tp(CommandSender s, String[] args) {
        Player p = jugador(s);
        if (p == null) return;
        if (args.length < 2) { error(s, "/holo tp <id>"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        if (h.getUbicacion().getWorld() == null) { error(s, "Su mundo no esta cargado."); return; }
        p.teleport(h.getUbicacion());
        ok(s, "Ahi lo tienes.");
    }

    private void lista(CommandSender s) {
        List<Hologram> todos = manager.lista();
        if (todos.isEmpty()) { error(s, "No hay ningun holograma. Crea uno con /holo crear."); return; }
        s.sendMessage(ColorUtil.colorize("<gradient:#09AA00:#37C930><b>Hologramas (" + todos.size() + ")</b></gradient>"));
        for (Hologram h : todos) {
            String mundo = manager.getDatos().mundoDe(h.getId());
            boolean visible = manager.estaPintado(h.getId());
            s.sendMessage(ColorUtil.colorize("<#DBB30F>" + h.getId() + "</#DBB30F> <#9E9E9E>· "
                    + mundo + " " + (int) h.getUbicacion().getX() + " " + (int) h.getUbicacion().getY()
                    + " " + (int) h.getUbicacion().getZ()
                    + " · " + h.getLineas().size() + " lineas · ")
                    + (visible ? ColorUtil.colorize("<#3DCC04>visible")
                    : ColorUtil.colorize("<#FF3D3D>sin pintar (mundo o chunk sin cargar)")));
        }
    }

    private void info(CommandSender s, String[] args) {
        if (args.length < 2) { error(s, "/holo info <id>"); return; }
        Hologram h = buscar(s, args[1]);
        if (h == null) return;
        s.sendMessage(ColorUtil.colorize("<gradient:#09AA00:#37C930><b>" + h.getId() + "</b></gradient>"));
        dato(s, "sitio", manager.getDatos().mundoDe(h.getId()) + " "
                + redondo(h.getUbicacion().getX()) + " " + redondo(h.getUbicacion().getY())
                + " " + redondo(h.getUbicacion().getZ()));
        dato(s, "tamano", String.valueOf(h.getTamano()));
        dato(s, "sombra", h.isSombra() ? "si" : "no");
        dato(s, "fondo", h.getFondo() == null ? "defecto" : h.getFondo());
        dato(s, "rotacion", h.getRotacion() + "   (inclinacion " + h.getInclinacion() + ")");
        dato(s, "seguir", nombreSeguir(h.getSeguir()));
        dato(s, "luz", h.getLuz() == null ? "auto"
                : h.getLuz().getBlockLight() + ":" + h.getLuz().getSkyLight());
        dato(s, "opacidad", h.getOpacidad() < 0 ? "defecto" : h.getOpacidad() + "%");
        dato(s, "alineacion", h.getAlineacion().name().toLowerCase(Locale.ROOT));
        dato(s, "rango", String.valueOf(h.getRango()));
        dato(s, "traspasar", h.isTraspasar() ? "si" : "no");
        dato(s, "ancho", String.valueOf(h.getAncho()));
        s.sendMessage(ColorUtil.colorize("<#4D4D4D>lineas:"));
        int n = 1;
        for (String l : h.getLineas()) {
            s.sendMessage(ColorUtil.colorize("  <#4D4D4D>" + (n++) + "</#4D4D4D> ") + ColorUtil.colorize(l));
        }
    }

    private void copiar(CommandSender s, String[] args) {
        Player p = jugador(s);
        if (p == null) return;
        if (args.length < 3) { error(s, "/holo copiar <id> <idNuevo>"); return; }
        Hologram origen = buscar(s, args[1]);
        if (origen == null) return;
        String nuevo = norm(args[2]);
        if (manager.get(nuevo) != null) { error(s, "Ya existe «" + nuevo + "»."); return; }

        Location donde = p.getEyeLocation();
        donde.setYaw(0f);
        donde.setPitch(0f);
        Hologram h = manager.crear(nuevo, donde, new ArrayList<>(origen.getLineas()));
        h.copiarAjustesDe(origen);
        manager.refrescar(h);
        ok(s, "Copiado a «" + nuevo + "», con los mismos ajustes, donde estas.");
    }

    // ============================================================ ajustes

    /** ¿Es este argumento un 'clave:valor' de los nuestros? */
    private boolean esAjuste(String token) {
        int dosPuntos = token.indexOf(':');
        if (dosPuntos <= 0) return false;
        return CLAVES.contains(norm(token.substring(0, dosPuntos)));
    }

    /** Aplica un 'clave:valor'. Devuelve false y avisa si no se entiende. */
    private boolean aplicarAjuste(Hologram h, String token, CommandSender s) {
        int dosPuntos = token.indexOf(':');
        if (dosPuntos <= 0) { error(s, "«" + token + "» no es un ajuste. Formato: clave:valor"); return false; }

        String clave = norm(token.substring(0, dosPuntos));
        String valor = token.substring(dosPuntos + 1);

        switch (clave) {
            case "tamano" -> {
                double v = decimal(valor, Double.NaN);
                if (Double.isNaN(v) || v <= 0) { error(s, "tamano tiene que ser un numero mayor que 0."); return false; }
                h.setTamano((float) v);
            }
            case "sombra" -> {
                Boolean b = booleano(valor);
                if (b == null) { error(s, "sombra solo acepta si o no."); return false; }
                h.setSombra(b);
            }
            case "fondo" -> {
                String v = norm(valor);
                if (v.equals("defecto") || v.equals("default")) h.setFondo(null);
                else if (v.equals("no") || v.equals("ninguno") || v.equals("transparente")) h.setFondo("no");
                else if (Hologram.colorDesdeTexto(valor) != null) h.setFondo(valor);
                else { error(s, "fondo acepta: no, defecto, #RRGGBB o #AARRGGBB (el AA es la transparencia)."); return false; }
            }
            case "rotacion" -> {
                double v = decimal(valor, Double.NaN);
                if (Double.isNaN(v)) { error(s, "rotacion tiene que ser un numero de grados."); return false; }
                h.setRotacion((float) v);
                if (h.getSeguir() != Display.Billboard.FIXED) {
                    aviso(s, "La rotacion solo se ve con seguir:no. Ahora mismo es el cliente quien decide hacia donde mira.");
                }
            }
            case "inclinacion" -> {
                double v = decimal(valor, Double.NaN);
                if (Double.isNaN(v)) { error(s, "inclinacion tiene que ser un numero de grados (-90 a 90)."); return false; }
                h.setInclinacion((float) v);
                if (h.getSeguir() != Display.Billboard.FIXED) {
                    aviso(s, "La inclinacion solo se ve con seguir:no.");
                }
            }
            case "seguir" -> {
                Display.Billboard b = seguirDesde(valor);
                if (b == null) { error(s, "seguir acepta: no, si, horizontal, vertical."); return false; }
                h.setSeguir(b);
            }
            case "luz" -> {
                String v = norm(valor);
                if (v.equals("auto") || v.equals("ambiente") || v.equals("no")) { h.setLuz(null); break; }
                String[] partes = v.split(":");
                int bloque = entero(partes[0], -1);
                int cielo = partes.length > 1 ? entero(partes[1], -1) : bloque;
                if (bloque < 0 || bloque > 15 || cielo < 0 || cielo > 15) {
                    error(s, "luz acepta: auto, un numero de 0 a 15, o bloque:cielo (por ejemplo 15:0).");
                    return false;
                }
                h.setLuz(new Display.Brightness(bloque, cielo));
            }
            case "opacidad" -> {
                if (norm(valor).equals("defecto")) { h.setOpacidad(-1); break; }
                int v = entero(valor, -999);
                if (v < 0 || v > 100) { error(s, "opacidad va de 0 a 100 (o 'defecto')."); return false; }
                h.setOpacidad(v);
            }
            case "alineacion" -> {
                TextDisplay.TextAlignment a = switch (norm(valor)) {
                    case "centro", "center", "medio" -> TextDisplay.TextAlignment.CENTER;
                    case "izquierda", "left", "izq" -> TextDisplay.TextAlignment.LEFT;
                    case "derecha", "right", "der" -> TextDisplay.TextAlignment.RIGHT;
                    default -> null;
                };
                if (a == null) { error(s, "alineacion acepta: centro, izquierda, derecha."); return false; }
                h.setAlineacion(a);
            }
            case "rango" -> {
                double v = decimal(valor, Double.NaN);
                if (Double.isNaN(v) || v <= 0) { error(s, "rango tiene que ser un numero mayor que 0 (1 = lo normal)."); return false; }
                h.setRango((float) v);
            }
            case "traspasar" -> {
                Boolean b = booleano(valor);
                if (b == null) { error(s, "traspasar solo acepta si o no."); return false; }
                h.setTraspasar(b);
            }
            case "ancho" -> {
                int v = entero(valor, -1);
                if (v < 1) { error(s, "ancho tiene que ser un numero de pixeles (200 es lo normal)."); return false; }
                h.setAncho(v);
            }
            default -> { error(s, "No conozco el ajuste «" + clave + "». Mira /holo ayuda."); return false; }
        }
        return true;
    }

    private static Display.Billboard seguirDesde(String valor) {
        return switch (norm(valor)) {
            case "no", "fijo", "nada" -> Display.Billboard.FIXED;
            case "si", "todo", "total", "completo" -> Display.Billboard.CENTER;
            // OJO con los nombres: en la API, VERTICAL quiere decir "gira sobre
            // el eje vertical", o sea que te sigue cuando le das la vuelta
            // andando, pero no se inclina. Que es lo que la gente llama
            // "seguir en horizontal".
            case "horizontal", "lados", "andando" -> Display.Billboard.VERTICAL;
            case "vertical", "arriba", "mirando" -> Display.Billboard.HORIZONTAL;
            default -> null;
        };
    }

    private static String nombreSeguir(Display.Billboard b) {
        return switch (b) {
            case FIXED -> "no (fijo, usa la rotacion)";
            case CENTER -> "si (siempre de frente)";
            case VERTICAL -> "horizontal (te sigue al rodearlo, no se inclina)";
            case HORIZONTAL -> "vertical (se inclina, no gira de lado)";
        };
    }

    // ============================================================ utilidades

    private Hologram buscar(CommandSender s, String id) {
        Hologram h = manager.get(id);
        if (h == null) error(s, "No existe ningun holograma que se llame «" + id + "».");
        return h;
    }

    private Player jugador(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage("Esto hay que hacerlo en el juego.");
        return null;
    }

    /** Une los argumentos desde 'desde' con espacios. */
    private static String unir(String[] args, int desde) {
        return String.join(" ", Arrays.copyOfRange(args, desde, args.length));
    }

    /** Parte el texto en lineas por la secuencia \n escrita a mano. */
    private static List<String> partirTexto(String texto) {
        return new ArrayList<>(Arrays.asList(texto.split("\\\\n", -1)));
    }

    /** Minusculas y sin tildes, para que 'tamaño' y 'tamano' valgan igual. */
    private static String norm(String s) {
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return t.replaceAll("\\p{M}", "");
    }

    private static Boolean booleano(String v) {
        return switch (norm(v)) {
            case "si", "sí", "true", "1", "yes" -> Boolean.TRUE;
            case "no", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static int entero(String v, int porDefecto) {
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return porDefecto; }
    }

    private static double decimal(String v, double porDefecto) {
        try { return Double.parseDouble(v.trim().replace(',', '.')); }
        catch (NumberFormatException e) { return porDefecto; }
    }

    private static String redondo(double d) { return String.valueOf(Math.round(d * 10) / 10.0); }

    private void ok(CommandSender s, String m) { s.sendMessage(ColorUtil.colorize("<#3DCC04>" + m)); }
    private void error(CommandSender s, String m) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>" + m)); }
    private void aviso(CommandSender s, String m) { s.sendMessage(ColorUtil.colorize("<#FFB400>" + m)); }
    private void dato(CommandSender s, String k, String v) {
        s.sendMessage(ColorUtil.colorize("  <#9E9E9E>" + k + ": <#DBB30F>" + v));
    }

    private void ayuda(CommandSender s) {
        s.sendMessage(ColorUtil.colorize("<gradient:#09AA00:#37C930><b>Hologramas</b></gradient>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/holo crear <#DBB30F><id> [ajustes] <texto>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/holo editar <#DBB30F><id> <clave:valor> ..."));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/holo texto <#DBB30F><id> <texto></#DBB30F> <#4D4D4D>(\\n = salto de linea)"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/holo linea <#DBB30F><id> <n> <texto></#DBB30F> <#4D4D4D>· anadir · quitarlinea"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/holo mover <#DBB30F><id></#DBB30F> <#4D4D4D>· subir <id> <n> · tp <id>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/holo lista <#4D4D4D>· info <id> · copiar <id> <nuevo> · borrar <id> · recargar"));
        s.sendMessage(" ");
        s.sendMessage(ColorUtil.colorize("<#4D4D4D>◇</#4D4D4D> <#474747>Ajustes</#474747>"));
        dato(s, "tamano", "2.5          escala del texto");
        dato(s, "sombra", "si / no      sombreado de las letras");
        dato(s, "fondo", "no / defecto / #RRGGBB / #AARRGGBB");
        dato(s, "rotacion", "90           grados, solo con seguir:no");
        dato(s, "inclinacion", "15        grados, solo con seguir:no");
        dato(s, "seguir", "no / si / horizontal / vertical");
        dato(s, "luz", "auto / 0-15 / bloque:cielo");
        dato(s, "opacidad", "0-100 / defecto");
        dato(s, "alineacion", "centro / izquierda / derecha");
        dato(s, "rango", "1            a mas numero, se ve desde mas lejos");
        dato(s, "traspasar", "si / no   verlo a traves de los bloques");
        dato(s, "ancho", "200          pixeles antes de partir la linea");
        s.sendMessage(" ");
        s.sendMessage(ColorUtil.colorize("<#4D4D4D>El texto admite los mismos colores que la tienda:"));
        // Sin tags literales aqui: ColorUtil se los comeria al pintar el mensaje.
        s.sendMessage("\u00a78  hex, gradient, negrita, cursiva y los codigos de color de siempre.");
        s.sendMessage("\u00a78  Ejemplo: /holo crear tienda tamano:2 fondo:no <gradient:#09AA00:#37C930><b>TIENDA</b></gradient>"
                .replace("<", "\u00a77<\u00a78"));
    }

    // ============================================================ tab

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(PERMISO)) return out;

        if (args.length == 1) {
            for (String sub : SUBCOMANDOS) if (sub.startsWith(norm(args[0]))) out.add(sub);
            return out;
        }

        String sub = norm(args[0]);
        if (args.length == 2) {
            if (sub.equals("crear")) { out.add("<id>"); return out; }
            for (Hologram h : manager.lista()) {
                if (h.getId().startsWith(norm(args[1]))) out.add(h.getId());
            }
            return out;
        }

        // A partir de aqui, sugerencias de ajustes para crear y editar
        if (sub.equals("editar") || sub.equals("crear")) {
            String ultimo = args[args.length - 1];
            int dosPuntos = ultimo.indexOf(':');
            if (dosPuntos > 0) {
                for (String v : valoresDe(norm(ultimo.substring(0, dosPuntos)))) {
                    out.add(ultimo.substring(0, dosPuntos + 1) + v);
                }
            } else {
                for (String c : CLAVES) if (c.startsWith(norm(ultimo))) out.add(c + ":");
            }
        }
        return out;
    }

    private static List<String> valoresDe(String clave) {
        return switch (clave) {
            case "tamano" -> List.of("0.5", "1", "1.5", "2", "3", "5");
            case "sombra", "traspasar" -> List.of("si", "no");
            case "fondo" -> List.of("no", "defecto", "#80000000", "#FF000000", "#8000AA00");
            case "rotacion" -> List.of("0", "45", "90", "135", "180", "270");
            case "inclinacion" -> List.of("-45", "-15", "0", "15", "45");
            case "seguir" -> List.of("no", "si", "horizontal", "vertical");
            case "luz" -> List.of("auto", "15", "15:15", "15:0", "7");
            case "opacidad" -> List.of("defecto", "25", "50", "75", "100");
            case "alineacion" -> List.of("centro", "izquierda", "derecha");
            case "rango" -> List.of("0.5", "1", "2", "5");
            case "ancho" -> List.of("100", "200", "400");
            default -> List.of();
        };
    }
}
