package me.dego.estudio.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderResolver {

    private static final Pattern MATH_PATTERN = Pattern.compile("%math_\\d+_(.+?)%");
    private static final Pattern CHECKITEM_PATTERN = Pattern.compile("%checkitem_amount_mat:([a-zA-Z_]+)%");
    private static final Pattern CHECKITEM_CUSTOM_PATTERN = Pattern.compile("%checkitem_amount_custom:([^%]+)%");
    private static final Pattern CURLY_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_:]*)}");

    /** Igual que resolve(), pero además resuelve %checkitem_amount_custom:KEY%
     * buscando el ítem 'KEY' dentro de los items de ESTE menú (para poder
     * contar cuántos tiene el jugador de un ítem de mod capturado con
     * /shop capture, igual que %checkitem_amount_mat:X% hace con vanilla). */
    public String resolveWithMenu(String input, Player player, java.util.Map<String, ShopItem> menuItems) {
        if (input == null) return null;
        String text = input;

        Matcher customMatcher = CHECKITEM_CUSTOM_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (customMatcher.find()) {
            String itemKey = customMatcher.group(1);
            int amount = countCustomItem(player, menuItems, itemKey);
            customMatcher.appendReplacement(sb, String.valueOf(amount));
        }
        customMatcher.appendTail(sb);
        text = sb.toString();

        return resolve(text, player, menuItems);
    }

    private int countCustomItem(Player player, java.util.Map<String, ShopItem> menuItems, String itemKey) {
        if (menuItems == null) return 0;
        ShopItem referenced = menuItems.get(itemKey);
        if (referenced == null || referenced.getCustomItemBase64() == null) return 0;

        ItemStack template = ItemSerializer.deserialize(referenced.getCustomItemBase64());
        if (template == null) return 0;

        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Resuelve todos los placeholders de texto de una cadena (para lore, display_name, mensajes...). */
    public String resolve(String input, Player player, Map<String, ShopItem> menuItems) {
        if (input == null) return null;
        String text = input;

        text = text.replace("%player_name%", player.getName());

        // {placeholder} sueltos, tipo {player_empty_slots}
        Matcher curlyMatcher = CURLY_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (curlyMatcher.find()) {
            String key = curlyMatcher.group(1);
            String value = resolveSimplePlaceholder(key, player, menuItems);
            curlyMatcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        curlyMatcher.appendTail(sb);
        text = sb.toString();

        // %checkitem_amount_mat:X%
        Matcher checkItemMatcher = CHECKITEM_PATTERN.matcher(text);
        sb = new StringBuilder();
        while (checkItemMatcher.find()) {
            int amount = countMaterial(player, checkItemMatcher.group(1));
            checkItemMatcher.appendReplacement(sb, String.valueOf(amount));
        }
        checkItemMatcher.appendTail(sb);
        text = sb.toString();

        // %math_N_FORMULA% -> se resuelve DESPUÉS de lo anterior, por si la fórmula
        // contiene a su vez otro placeholder como {player_empty_slots}
        Matcher mathMatcher = MATH_PATTERN.matcher(text);
        sb = new StringBuilder();
        while (mathMatcher.find()) {
            String formula = mathMatcher.group(1);
            // La fórmula puede seguir teniendo {placeholders} dentro (ej: {checkitem_amount_mat:apple})
            formula = resolveInnerPlaceholders(formula, player, menuItems);
            formula = resolveMaxCalls(formula);
            double result = evaluateArithmetic(formula);
            String resultStr = (result == Math.floor(result)) ? String.valueOf((long) result) : String.valueOf(result);
            mathMatcher.appendReplacement(sb, Matcher.quoteReplacement(resultStr));
        }
        mathMatcher.appendTail(sb);
        text = sb.toString();

        return text;
    }

    /** Igual que resolve(), pero devuelve directamente un número (para requisitos de cantidad/dinero). */
    public double resolveNumber(String input, Player player, Map<String, ShopItem> menuItems) {
        String resolved = resolve(input, player, menuItems);
        try {
            return Double.parseDouble(resolved.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ============================================================
    //  Placeholders simples reconocidos
    // ============================================================
    private String resolveSimplePlaceholder(String key, Player player, Map<String, ShopItem> menuItems) {
        return switch (key) {
            case "player_empty_slots" -> String.valueOf(countEmptySlots(player));
            case "player_name" -> player.getName();
            default -> {
                // Soporte para ítems vainilla dentro de fórmulas {checkitem_amount_mat:X}
                if (key.startsWith("checkitem_amount_mat:")) {
                    yield String.valueOf(countMaterial(player, key.substring("checkitem_amount_mat:".length())));
                }
                // Soporte para ítems custom de mods dentro de fórmulas {checkitem_amount_custom:KEY}
                if (key.startsWith("checkitem_amount_custom:")) {
                    String itemKey = key.substring("checkitem_amount_custom:".length());
                    yield String.valueOf(countCustomItem(player, menuItems, itemKey));
                }
                yield "0"; // placeholder desconocido
            }
        };
    }

    /** Resuelve {placeholders} que puedan quedar sueltos dentro de una fórmula math ya extraída. */
    private String resolveInnerPlaceholders(String formula, Player player, Map<String, ShopItem> menuItems) {
        Matcher curlyMatcher = CURLY_PATTERN.matcher(formula);
        StringBuilder sb = new StringBuilder();
        while (curlyMatcher.find()) {
            String value = resolveSimplePlaceholder(curlyMatcher.group(1), player, menuItems);
            curlyMatcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        curlyMatcher.appendTail(sb);
        return sb.toString();
    }

    private int countEmptySlots(Player player) {
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) empty++;
        }
        return empty;
    }

    private int countMaterial(Player player, String materialName) {
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) return 0;
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    // ============================================================
    //  Mini evaluador de fórmulas: + - * / y MAX(a,b), con paréntesis
    // ============================================================

    /**
     * Evalúa expresiones como "MAX(0,({x}-6))*64*20" ya con los placeholders
     * resueltos a números. Soporta +, -, *, /, paréntesis, y la función MAX(a,b).
     */
    private double evaluateFormula(String rawFormula, Player player, Map<String, ShopItem> menuItems ) {
        // Traduce MAX(a,b) a un token evaluable recursivamente ANTES del shunting-yard,
        // resolviendo primero los MAX más internos.
        String innerResolved = resolveInnerPlaceholders(rawFormula, player, menuItems);
        return evaluateArithmetic(innerResolved);
    }

    /**
     * Resuelve todas las llamadas MAX(a,b) del texto, soportando paréntesis
     * anidados dentro de los argumentos (ej. MAX(0,(36-6))*64), y llamadas
     * MAX anidadas entre sí. Usa un escáner manual de paréntesis balanceados
     * en vez de regex, porque las regex no pueden manejar anidamiento
     * arbitrario de forma fiable.
     */
    private String resolveMaxCalls(String expr) {
        int idx;
        while ((idx = expr.indexOf("MAX(")) != -1) {
            int openParen = idx + 3; // posición de la '(' que abre los argumentos
            int depth = 0;
            int closeParen = -1;

            for (int i = openParen; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') {
                    depth--;
                    if (depth == 0) { closeParen = i; break; }
                }
            }

            if (closeParen == -1) {
                throw new RuntimeException("MAX( sin cierre correspondiente en: " + expr);
            }

            String argsContent = expr.substring(openParen + 1, closeParen);
            // Resuelve primero cualquier MAX anidado DENTRO de los propios argumentos
            argsContent = resolveMaxCalls(argsContent);

            List<String> args = splitTopLevelCommas(argsContent);
            double max = Double.NEGATIVE_INFINITY;
            for (String arg : args) {
                max = Math.max(max, evaluateArithmetic(arg));
            }

            expr = expr.substring(0, idx) + max + expr.substring(closeParen + 1);
        }
        return expr;
    }

    /** Divide "0,(36-6)" en ["0", "(36-6)"] sin partir por comas que estén dentro de paréntesis. */
    private List<String> splitTopLevelCommas(String content) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int lastSplit = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(content.substring(lastSplit, i));
                lastSplit = i + 1;
            }
        }
        parts.add(content.substring(lastSplit));
        return parts;
    }

    /** Evaluador aritmético simple con soporte de paréntesis, +, -, *, /. */
    private double evaluateArithmetic(String rawExpr) {
        final String expr = rawExpr.replaceAll("\\s+", "");
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Fórmula inválida cerca de: " + expr.substring(Math.max(0, startPos - 5)));
                }
                return x;
            }
        }.parse();
    }
}
