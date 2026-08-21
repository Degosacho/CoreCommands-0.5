package me.dego.estudio.managers.dependences;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultEconomy {

    private Economy economy;

    // Devuelve true si se enganchó correctamente. Llamar en onEnable() antes del resto
    public boolean setup(Plugin plugin){
        if(plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault no está instalado, los trabajos no darán dinero.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if(rsp == null){
            plugin.getLogger().warning("No se encontró ningún plugin de economía enganchado a Vault.");
            return false;
        }

        economy = rsp.getProvider();
        return true;
    }

    // Dar dinero
    public void giveMoney(Player player, double amount){
        if (economy == null || amount <= 0) return;
        economy.depositPlayer(player, amount);
    }

    public boolean isEnabled(){
        return economy != null;
    }
}
