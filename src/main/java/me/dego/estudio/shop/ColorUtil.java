package me.dego.estudio.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_TAG = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern HEX_CLOSE_TAG = Pattern.compile("</#[A-Fa-f0-9]{6}>");
    private static final Pattern RAW_HEX = Pattern.compile("#([A-Fa-f0-9]{6})");
    // Solo captura gradientes SIN otro <gradient: anidado dentro (el más interno).
    // Se aplica en bucle para ir resolviendo de dentro hacia fuera.
    private static final Pattern INNERMOST_GRADIENT_TAG = Pattern.compile(
            "<gradient:((?:#[A-Fa-f0-9]{6}:?)+)>((?:(?!<gradient:).)*?)</gradient>", Pattern.DOTALL);
    private static final Pattern SHADOW_TAG = Pattern.compile("</?shadow(:[^>]*)?>");
    private static final Pattern BOLD_TAG = Pattern.compile("<b>(.*?)</b>", Pattern.DOTALL);
    private static final Pattern ITALIC_TAG = Pattern.compile("<i>(.*?)</i>", Pattern.DOTALL);

    /** Convierte una línea completa (display_name, lore, etc.) a legacy §-codes. */
    public static String colorize(String input) {
        if (input == null) return null;

        String text = input;

        // 1. Elimina <shadow:...>...</shadow> (efecto no replicable en legacy, se conserva el texto)
        text = SHADOW_TAG.matcher(text).replaceAll("");

        // 2. Cierres de hex tipo </#RRGGBB> -> simplemente resetea el formato (§r).
        //    Debe ir ANTES que el paso de hex sueltos, o el "#RRGGBB" de dentro
        //    del cierre se coloreaba solo y dejaba basura tipo "</>" en pantalla.
        text = HEX_CLOSE_TAG.matcher(text).replaceAll("§r");

        // 3. <b>texto</b> e <i>texto</i> -> §l/§o antes y §r después.
        //    IMPORTANTE: esto va ANTES de resolver gradientes. Si un <b> está
        //    anidado dentro de un <gradient>, y coloreamos primero el gradiente,
        //    el degradado trata los propios caracteres de "<b>" y "</b>" como
        //    texto a colorear, y para cuando le toca el turno a esta conversión
        //    esos tags ya no existen como cadena reconocible. Convirtiendo
        //    primero, el gradiente solo ve §l/§r (códigos de formato), que
        //    applyGradient() sabe dejar pasar sin contarlos como texto visible.
        text = BOLD_TAG.matcher(text).replaceAll("§l$1§r");
        text = ITALIC_TAG.matcher(text).replaceAll("§o$1§r");

        // 4. Resuelve gradientes (incluso anidados) ANTES que hex sueltos, porque contienen hex dentro
        text = resolveGradients(text);

        // 5. <#RRGGBB> -> §x§R§R§G§G§B§B
        Matcher hexTagMatcher = HEX_TAG.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (hexTagMatcher.find()) {
            hexTagMatcher.appendReplacement(sb, Matcher.quoteReplacement(hexToLegacy(hexTagMatcher.group(1))));
        }
        hexTagMatcher.appendTail(sb);
        text = sb.toString();

        // 6. #RRGGBB suelto (sin <>) -> §x§R§R§G§G§B§B
        Matcher rawHexMatcher = RAW_HEX.matcher(text);
        sb = new StringBuilder();
        while (rawHexMatcher.find()) {
            rawHexMatcher.appendReplacement(sb, Matcher.quoteReplacement(hexToLegacy(rawHexMatcher.group(1))));
        }
        rawHexMatcher.appendTail(sb);
        text = sb.toString();

        // 7. &codes clásicos -> §codes
        text = text.replace('&', '§');

        return text;
    }

    /** Convierte "RRGGBB" al formato legacy §x§R§R§G§G§B§B que Bukkit interpreta como color hex. */
    private static String hexToLegacy(String hex) {
        StringBuilder result = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            result.append('§').append(c);
        }
        return result.toString();
    }

    /**
     * Resuelve <gradient:#AAA:#BBB[:#CCC...]>texto</gradient> aplicando un color
     * hex distinto e interpolado a cada carácter del texto interior.
     */
    private static String resolveGradients(String text) {
        // Resuelve el/los gradiente(s) más internos primero, y repite hasta que
        // no quede ningún <gradient:...> sin procesar (así se soportan gradientes
        // anidados como <gradient>...texto <gradient>...</gradient>...</gradient>).
        String result = text;
        Matcher matcher = INNERMOST_GRADIENT_TAG.matcher(result);

        while (matcher.find()) {
            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;
            Matcher m = INNERMOST_GRADIENT_TAG.matcher(result);
            while (m.find()) {
                sb.append(result, lastEnd, m.start());
                String[] hexStops = m.group(1).replace("#", "").split(":");
                String content = m.group(2);
                sb.append(applyGradient(content, hexStops));
                lastEnd = m.end();
            }
            sb.append(result.substring(lastEnd));
            result = sb.toString();
            matcher = INNERMOST_GRADIENT_TAG.matcher(result);
        }

        return result;
    }

    private static String applyGradient(String content, String[] hexStops) {
        // Quita tags internos (como &l) del cálculo de posición, pero los deja pasar tal cual en el resultado
        int length = content.length();
        if (length == 0 || hexStops.length < 2) return content;

        StringBuilder result = new StringBuilder();
        int[][] colors = new int[hexStops.length][3];
        for (int i = 0; i < hexStops.length; i++) {
            colors[i] = hexToRgb(hexStops[i]);
        }

        int segments = hexStops.length - 1;

        // Primero separamos el contenido en "tokens": o bien un código de formato
        // ya existente (§ seguido de un carácter, ej. §l, §r, §o — como los que
        // insertamos para <b>/<i> nidados dentro del gradiente), que se copia
        // TAL CUAL sin contar como posición del degradado; o bien un carácter
        // visible normal, que sí cuenta y recibe su color interpolado.
        List<String> formatTokens = new ArrayList<>();
        List<Character> visibleChars = new ArrayList<>();

        int i = 0;
        while (i < length) {
            char c = content.charAt(i);
            if (c == '§' && i + 1 < length) {
                formatTokens.add(String.valueOf(c) + content.charAt(i + 1));
                visibleChars.add(null); // marcador: aquí va un token de formato, no un char coloreable
                i += 2;
            } else {
                formatTokens.add(null);
                visibleChars.add(c);
                i += 1;
            }
        }

        int visibleCount = (int) visibleChars.stream().filter(ch -> ch != null).count();
        int visibleIndex = 0;

        for (int idx = 0; idx < visibleChars.size(); idx++) {
            Character c = visibleChars.get(idx);

            if (c == null) {
                // Es un código de formato (§X): se copia sin tocar, sin gastar posición de gradiente
                result.append(formatTokens.get(idx));
                continue;
            }

            double progress = visibleCount <= 1 ? 0 : (double) visibleIndex / (visibleCount - 1);
            int segment = Math.min((int) (progress * segments), segments - 1);
            double localProgress = (progress * segments) - segment;

            int[] from = colors[segment];
            int[] to = colors[segment + 1];

            int r = (int) (from[0] + (to[0] - from[0]) * localProgress);
            int g = (int) (from[1] + (to[1] - from[1]) * localProgress);
            int b = (int) (from[2] + (to[2] - from[2]) * localProgress);

            result.append(hexToLegacy(String.format("%02X%02X%02X", r, g, b)));
            result.append((char) c);
            visibleIndex++;
        }

        return result.toString();
    }

    private static int[] hexToRgb(String hex) {
        return new int[]{
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }
}