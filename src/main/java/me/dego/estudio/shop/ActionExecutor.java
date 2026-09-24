package me.dego.estudio.shop;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionExecutor {

    private static final Pattern TAG_PATTERN = Pattern.compile("^\\[(\\w+)]\\s*(.*)$");

    private final ShopManager shopManager;
    private final PlaceholderResolver resolver;

    public ActionExecutor(ShopManager shopManager, PlaceholderResolver resolver) {
        this.shopManager = shopManager;
        this.resolver = resolver;
    }

    /** Ejecuta una lista de acciones en orden para ese jugador, sin contexto de menú. */
    public void executeAll(List<String> actions, Player player) {
        executeAll(actions, player, null);
    }

    /** Igual que executeAll(), pero con acceso a los items del menú actual para %checkitem_amount_custom:KEY%. */
    public void executeAll(List<String> actions, Player player, Map<String, ShopItem> menuContext) {
        if (actions == null) return;
        for (String action : actions) {
            try {
                execute(action, player, menuContext);
            } catch (Exception e) {
                shopManager.getPlugin().getLogger().warning("[Shop] Error ejecutando acción '" + action + "': " + e.getMessage());
                // seguimos con la siguiente acción de la lista en vez de abortar todo el resto
            }
        }
    }

    public void execute(String rawAction, Player player) {
        execute(rawAction, player, null);
    }

    public void execute(String rawAction, Player player, Map<String, ShopItem> menuContext) {
        Matcher matcher = TAG_PATTERN.matcher(rawAction.trim());
        if (!matcher.matches()) {
            return; // línea sin tag reconocido, se ignora
        }

        String tag = matcher.group(1).toLowerCase();
        // <s:> en '[player] shop <s:>' representa el texto buscado en /shop <búsqueda>;
        // como no tenemos ese input aquí, lo dejamos vacío si aparece.
        String rawArgs = matcher.group(2).replace("<s:>", "");
        String args = (menuContext != null)
                ? resolver.resolveWithMenu(rawArgs, player, menuContext)
                : resolver.resolve(rawArgs, player, menuContext);

        switch (tag) {
            case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), args);
            case "player" -> Bukkit.dispatchCommand(player, args);
            case "message" -> player.sendMessage(ColorUtil.colorize(args));
            case "close" -> player.closeInventory();
            case "openguimenu" -> shopManager.open(player, args.trim());
            case "sound" -> playSound(player, args.trim());
            case "refresh" -> shopManager.refresh(player);
            case "search" -> shopManager.pedirBusqueda(player);
            case "givekey" -> darLlave(player, args.trim());
            default -> { /* tag desconocido, se ignora silenciosamente */ }
        }
    }

    /**
     * '[givekey] <caja> [cantidad]' — le da llaves virtuales de una caja.
     * Se usa desde los menus que venden llaves, igual que se usa [console] eco take
     * para cobrarlas.
     */
    private void darLlave(Player player, String args) {
        me.dego.estudio.crates.CrateManager cajas = me.dego.estudio.Estudio.getInstance().getCrateManager();
        if (cajas == null) {
            shopManager.getPlugin().getLogger().warning("[Shop] [givekey] pero el sistema de cajas no esta activo.");
            return;
        }
        String[] partes = args.split("\\s+");
        if (partes.length == 0 || partes[0].isBlank()) return;
        int cantidad = 1;
        if (partes.length > 1) {
            try { cantidad = Integer.parseInt(partes[1]); } catch (NumberFormatException ignored) { }
        }
        if (cajas.getCaja(partes[0]) == null) {
            shopManager.getPlugin().getLogger().warning("[Shop] [givekey] apunta a una caja que no existe: " + partes[0]);
            return;
        }
        cajas.darLlaves(player.getUniqueId(), partes[0], cantidad);
    }

    private void playSound(Player player, String soundKey) {
        try {
            // Acepta tanto "UI_BUTTON_CLICK" (enum) como "ui.button.click" (namespaced key)
            String normalized = soundKey.toUpperCase().replace('.', '_');
            Sound sound = Sound.valueOf(normalized);
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
            // sonido no reconocido, no rompemos la ejecución del resto de acciones por esto
        }
    }
}
