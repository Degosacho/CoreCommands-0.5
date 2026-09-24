package me.dego.estudio.crates;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /cajas — administración.
 *
 *   /cajas lista
 *   /cajas colocar <caja>        (mirando al cofre)
 *   /cajas quitar                (mirando al cofre)
 *   /cajas dar <jugador> <caja> <cantidad>
 *   /cajas poner <jugador> <caja> <cantidad>
 *   /cajas ver <jugador>
 *   /cajas recargar
 */
public class CajasCommand implements CommandExecutor, TabCompleter {

    private final CrateManager manager;

    public CajasCommand(CrateManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(CrateListener.PERMISO_ADMIN)) {
            sender.sendMessage(ColorUtil.colorize("<#FF3D3D>No puedes hacer eso!"));
            return true;
        }
        if (args.length == 0) { ayuda(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "lista" -> lista(sender);
            case "colocar" -> colocar(sender, args);
            case "quitar" -> quitar(sender);
            case "dar" -> llaves(sender, args, false);
            case "poner" -> llaves(sender, args, true);
            case "ver" -> ver(sender, args);
            case "probar" -> probar(sender, args);
            case "comando" -> {
                sender.sendMessage(ColorUtil.colorize("<#9E9E9E>Comando de Pokémon: <#DBB30F>"
                        + manager.getComandoPokemon()));
                sender.sendMessage(ColorUtil.colorize("<#9E9E9E>Sufijo shiny: <#DBB30F>'"
                        + manager.getSufijoShiny() + "'"));
            }
            case "recargar" -> {
                manager.cargarCajas();
                sender.sendMessage(ColorUtil.colorize("<#3DCC04>Recargadas <#DBB30F>"
                        + manager.getCajas().size() + "</#DBB30F> cajas."));
            }
            default -> ayuda(sender);
        }
        return true;
    }

    private void ayuda(CommandSender s) {
        s.sendMessage(ColorUtil.colorize("<gradient:#09AA00:#37C930><b>Cajas</b></gradient>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas lista"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas colocar <#DBB30F><caja></#DBB30F> <#4D4D4D>(mirando al cofre)"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas quitar <#4D4D4D>(mirando al cofre)"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas dar <#DBB30F><jugador> <caja> <cantidad>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas poner <#DBB30F><jugador> <caja> <cantidad>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas ver <#DBB30F><jugador>"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas probar <#DBB30F><caja> [veces]</#DBB30F> <#4D4D4D>(sin gastar llave)"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas comando <#4D4D4D>(ver el comando de Pokémon cargado)"));
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>/cajas recargar"));
    }

    private void lista(CommandSender s) {
        if (manager.getCajas().isEmpty()) {
            s.sendMessage(ColorUtil.colorize("<#FF3D3D>No hay ninguna caja cargada."));
            return;
        }
        for (Crate c : manager.getCajas()) {
            List<Location> cofres = manager.cofresDe(c.getId());
            s.sendMessage(ColorUtil.colorize("<#DBB30F>" + c.getId() + "</#DBB30F> <#9E9E9E>· ")
                    + c.getNombreColoreado()
                    + ColorUtil.colorize(" <#9E9E9E>· " + c.getPremios().size() + " premios · "
                    + CrateGui.conPuntos(c.getPrecio()) + " $ · " + cofres.size() + " cofre(s)"));
            for (Location l : cofres) {
                s.sendMessage(ColorUtil.colorize("   <#4D4D4D>" + l.getWorld().getName() + " "
                        + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
            }
        }
    }

    private void colocar(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Esto hay que hacerlo en el juego."); return; }
        if (args.length < 2) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>/cajas colocar <caja>")); return; }
        Crate caja = manager.getCaja(args[1]);
        if (caja == null) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>No existe la caja <#DBB30F>" + args[1])); return; }

        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) {
            s.sendMessage(ColorUtil.colorize("<#FF3D3D>Mira al bloque que quieras convertir en caja."));
            return;
        }
        manager.getDatos().registrarBloque(b.getLocation(), caja.getId());
        s.sendMessage(ColorUtil.colorize("<#3DCC04>Ese <#DBB30F>" + b.getType().name().toLowerCase()
                + "</#DBB30F> ya es ") + caja.getNombreColoreado());
    }

    private void quitar(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Esto hay que hacerlo en el juego."); return; }
        Block b = p.getTargetBlockExact(6);
        if (b == null) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>Mira al cofre que quieras quitar.")); return; }
        boolean habia = manager.getDatos().quitarBloque(b.getLocation());
        s.sendMessage(ColorUtil.colorize(habia
                ? "<#3DCC04>Quitado. Ese bloque ya no es una caja."
                : "<#FF3D3D>Ese bloque no era ninguna caja."));
    }

    private void llaves(CommandSender s, String[] args, boolean exacto) {
        if (args.length < 4) {
            s.sendMessage(ColorUtil.colorize("<#FF3D3D>/cajas " + args[0] + " <jugador> <caja> <cantidad>"));
            return;
        }
        OfflinePlayer objetivo = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (objetivo == null) {
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online == null) {
                s.sendMessage(ColorUtil.colorize("<#FF3D3D>No conozco a <#DBB30F>" + args[1]
                        + "</#DBB30F>. Tiene que haber entrado alguna vez."));
                return;
            }
            objetivo = online;
        }
        Crate caja = manager.getCaja(args[2]);
        if (caja == null) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>No existe la caja <#DBB30F>" + args[2])); return; }

        int cantidad;
        try {
            cantidad = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            s.sendMessage(ColorUtil.colorize("<#FF3D3D>«" + args[3] + "» no es un número."));
            return;
        }

        UUID uuid = objetivo.getUniqueId();
        if (exacto) {
            manager.getDatos().ponerLlaves(uuid, caja.getId(), cantidad);
        } else {
            manager.darLlaves(uuid, caja.getId(), cantidad);
        }
        s.sendMessage(ColorUtil.colorize("<#3DCC04>" + objetivo.getName() + " tiene ahora <#DBB30F>"
                + manager.getDatos().getLlaves(uuid, caja.getId()) + "x </#DBB30F>") + caja.getLlaveColoreada());
    }

    /**
     * Sortea y entrega un premio sin gastar llave y sin ruleta. Es para probar
     * la entrega: si un premio no llega, aqui se ve al momento y en el log queda
     * el comando exacto que se ha lanzado.
     */
    private void probar(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("Esto hay que hacerlo en el juego."); return; }
        if (args.length < 2) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>/cajas probar <caja> [veces]")); return; }
        Crate caja = manager.getCaja(args[1]);
        if (caja == null) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>No existe la caja <#DBB30F>" + args[1])); return; }

        int veces = 1;
        if (args.length > 2) {
            try { veces = Math.max(1, Math.min(20, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { }
        }

        s.sendMessage(ColorUtil.colorize("<#9E9E9E>Probando ") + caja.getNombreColoreado()
                + ColorUtil.colorize("<#9E9E9E> (" + veces + "), sin gastar llaves:"));
        for (int i = 0; i < veces; i++) {
            CratePrize premio = manager.sortear(caja);
            s.sendMessage(ColorUtil.colorize("  <#4D4D4D>›</#4D4D4D> ") + premio.getNombreColoreado());
            premio.entregar(p, manager);
        }
    }

    private void ver(CommandSender s, String[] args) {
        if (args.length < 2) { s.sendMessage(ColorUtil.colorize("<#FF3D3D>/cajas ver <jugador>")); return; }
        OfflinePlayer objetivo = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (objetivo == null) objetivo = Bukkit.getPlayerExact(args[1]);
        if (objetivo == null) {
            s.sendMessage(ColorUtil.colorize("<#FF3D3D>No conozco a <#DBB30F>" + args[1]));
            return;
        }
        s.sendMessage(ColorUtil.colorize("<#9E9E9E>Llaves de <#DBB30F>" + objetivo.getName()));
        for (Crate c : manager.getCajas()) {
            s.sendMessage(ColorUtil.colorize("  <#DBB30F>"
                    + manager.getDatos().getLlaves(objetivo.getUniqueId(), c.getId()) + "x </#DBB30F>")
                    + c.getLlaveColoreada());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"lista", "colocar", "quitar", "dar", "poner", "ver",
                    "probar", "comando", "recargar"}) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("colocar") || args[0].equalsIgnoreCase("probar"))) {
            for (Crate c : manager.getCajas()) out.add(c.getId());
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("dar") || args[0].equalsIgnoreCase("poner"))) {
            for (Crate c : manager.getCajas()) out.add(c.getId());
        }
        return out;
    }
}
