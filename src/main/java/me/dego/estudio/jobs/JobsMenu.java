package me.dego.estudio.jobs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JobsMenu implements InventoryHolder{

    private static final int SIZE = 27; // 3 filas

    private final JobManager jobManager;
    private final Inventory inventory;

    public JobsMenu(JobManager jobManager, Player player) {
        this.jobManager = jobManager;
        this.inventory = Bukkit.createInventory(this, SIZE, "§8Selecciona tu trabajo");
        build(player);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Rellena el inventario con un ítem por cada trabajo disponible. */
    private void build(Player player) {
        PlayerJobData data = jobManager.getData(player.getUniqueId());
        String currentJobId = (data != null) ? data.getJobID() : null;

        int slot = 10; // empieza en la segunda fila, dejando margen
        for (Job job : jobManager.getAllJobs().values()) {
            boolean isCurrent = job.getId().equalsIgnoreCase(currentJobId);
            inventory.setItem(slot, buildJobIcon(job, isCurrent));
            slot++;
            if (slot == 17) slot = 19; // salta a la fila siguiente si se llena
        }

        // Ítem para dejar el trabajo actual, siempre en el mismo hueco
        inventory.setItem(22, buildLeaveIcon(currentJobId != null));
    }

    private ItemStack buildJobIcon(Job job, boolean isCurrent) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((isCurrent ? "§a✔ " : "§e") + job.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(isCurrent ? "§7Este es tu trabajo actual." : "§7Haz click para unirte a este trabajo.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildLeaveIcon(boolean hasJob) {
        ItemStack item = new ItemStack(hasJob ? Material.BARRIER : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(hasJob ? "§cDejar trabajo actual" : "§7No tienes ningún trabajo");
        item.setItemMeta(meta);
        return item;
    }
}
