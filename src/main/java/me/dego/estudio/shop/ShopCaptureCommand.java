package me.dego.estudio.shop;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ShopCaptureCommand implements CommandExecutor{

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("No eres un usuario.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Tienes que tener el ítem en la mano principal para capturarlo.");
            return true;
        }

        String base64 = ItemSerializer.serialize(item);

        player.sendMessage(ChatColor.GREEN + "Ítem capturado. La cadena se ha impreso en la CONSOLA del servidor");
        player.sendMessage(ChatColor.GRAY + "(es demasiado larga para el chat). Cópiala en tu YAML bajo 'custom_item:'.");

        player.getServer().getConsoleSender().sendMessage(
                ChatColor.YELLOW + "=== Ítem capturado por " + player.getName() + " ===");
        player.getServer().getConsoleSender().sendMessage(base64);
        player.getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + "=== Fin de la captura ===");

        return true;
    }
}
