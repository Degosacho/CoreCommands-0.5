package me.dego.estudio.commands;

import me.dego.estudio.Pickaxe3x3Listener;
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

public class DarPico3x3 implements CommandExecutor{

    private final Pickaxe3x3Listener pickaxe3x3Listener;
    private final Plugin plugin;

    public DarPico3x3(Pickaxe3x3Listener pickaxe3x3Listener, Plugin plugin){
        this.pickaxe3x3Listener = pickaxe3x3Listener;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(!(sender instanceof Player player)){
            System.out.println("No eres un usuario!");
            return true;
        }

        ItemStack pico = new ItemStack(Material.NETHERITE_PICKAXE);

        ItemMeta meta = pico.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 5, false);
        meta.addEnchant(Enchantment.UNBREAKING,3,false);
        meta.addEnchant(Enchantment.MENDING,1,false);
        meta.addEnchant(Enchantment.FORTUNE,3,false);
        pico.setItemMeta(meta);
        Pickaxe3x3Listener.markAsSpecialPickaxe(pico, plugin);

        player.getInventory().addItem(pico);



        return true;
    }

}
