package me.dego.estudio.commands;

import me.dego.estudio.shop.ColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * /itemname <texto>
 *
 * Renombra el ítem que el jugador tiene en la mano principal, coloreando
 * el texto con ColorUtil (los mismos tags que usa el /shop: <#RRGGBB>,
 * <gradient:...>, <b>, &códigos...).
 *
 * Ejemplo: /itemname <gradient:#FF0000:#FFD700><b>Espada Legendaria</b></gradient>
 *
 * Requiere el permiso 'estudio.itemname' (dale a quien quieras que pueda
 * nombrear ítems con color: staff, VIP, todos si quitas la comprobación...).
 */
public class ItemNameCommand implements CommandExecutor {

    private static final String PERMISSION = "estudio.itemname";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("No eres un usuario.");
            return true;
        }

        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Uso: /itemname <texto>");
            player.sendMessage(ChatColor.GRAY + "Ejemplo: /itemname <#FF0000>Mi Espada Roja");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Tienes que tener un ítem en la mano principal.");
            return true;
        }

        String rawText = String.join(" ", args);
        String coloredText = ColorUtil.colorize(rawText);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(ChatColor.RED + "Este ítem no admite nombre personalizado.");
            return true;
        }

        meta.setDisplayName(coloredText);
        item.setItemMeta(meta);

        player.sendMessage(ChatColor.GREEN + "Nombre del ítem actualizado.");
        return true;
    }
}