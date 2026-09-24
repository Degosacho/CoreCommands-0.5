package me.dego.estudio.crates;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /llaves — enseña tus llaves y desde ahí se compran o se ven los premios. */
public class LlavesCommand implements CommandExecutor {

    private final CrateManager manager;

    public LlavesCommand(CrateManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("No eres un usuario.");
            return true;
        }
        if (manager.getCajas().isEmpty()) {
            player.sendMessage(ColorUtil.colorize("<#FF3D3D>Todavía no hay ninguna caja configurada."));
            return true;
        }
        if (args.length > 0) {
            Crate caja = manager.getCaja(args[0]);
            if (caja == null) {
                player.sendMessage(ColorUtil.colorize("<#FF3D3D>No existe la caja <#DBB30F>" + args[0]));
                return true;
            }
            CrateGui.abrirPrevia(manager, player, caja);
            return true;
        }
        CrateGui.abrirLista(manager, player);
        return true;
    }
}
