package me.dego.estudio.commands;

import me.dego.estudio.jobs.JobManager;
import me.dego.estudio.shop.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final JobManager jobManager;
    private final ShopManager shopManager;

    public ReloadCommand(JobManager jobManager, ShopManager shopManager) {
        this.jobManager = jobManager;
        this.shopManager = shopManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        long start = System.currentTimeMillis();

        try {
            jobManager.loadJobsFromConfig();
            shopManager.loadAllMenus();
            // Las cajas tambien salen de un .yml, asi que se recargan aqui mismo.
            me.dego.estudio.crates.CrateManager cajas = me.dego.estudio.Estudio.getInstance().getCrateManager();
            if (cajas != null) cajas.cargarCajas();
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al recargar: " + e.getMessage());
            e.printStackTrace();
            return true;
        }

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(ChatColor.GREEN + "Configuración recargada correctamente (" + elapsed + "ms).");
        sender.sendMessage(ChatColor.GRAY + "Recuerda: esto NO recarga cambios de código Java, solo los YAML.");
        return true;
    }
}
