package me.dego.estudio.commands;

import me.dego.estudio.Pala3x3Listener;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class DarPala3x3 implements CommandExecutor{

    private final Pala3x3Listener pala3x3Listener;
    private final Plugin plugin;

    public DarPala3x3(Pala3x3Listener pala3x3Listener, Plugin plugin){
        this.pala3x3Listener = pala3x3Listener;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(!(sender instanceof Player player)){
            System.out.println("No eres un usuario!");
            return true;
        }

        ItemStack pala = new ItemStack(Material.NETHERITE_SHOVEL);
        ItemMeta meta = pala.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 5, false);
        meta.addEnchant(Enchantment.UNBREAKING,3,false);
        meta.addEnchant(Enchantment.MENDING,1,false);
        meta.addEnchant(Enchantment.FORTUNE,3,false);
        pala.setItemMeta(meta);
        Pala3x3Listener.markAsSpecialPickaxe(pala, plugin);

        player.getInventory().addItem(pala);

        return true;
    }

}