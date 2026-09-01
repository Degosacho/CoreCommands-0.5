package me.dego.estudio.jobs;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class JobsMenuListener implements Listener {

    private final JobManager jobManager;

    public JobsMenuListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof JobsMenu)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Click en "dejar trabajo"
        if (clicked.getType() == Material.BARRIER) {
            jobManager.leaveJob(player);
            player.sendMessage("§cHas abandonado tu trabajo.");
            player.closeInventory();
            return;
        }

        if (clicked.getType() != Material.BOOK) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        // Limpiamos los colores (§e, §a, ✔, etc.) para comparar únicamente el texto plano
        String rawClickedName = ChatColor.stripColor(meta.getDisplayName())
                .replace("✔", "")
                .trim();

        for (Job job : jobManager.getAllJobs().values()) {
            String rawJobName = ChatColor.stripColor(job.getDisplayName()).trim();

            if (rawClickedName.equalsIgnoreCase(rawJobName)) {
                jobManager.joinJob(player, job.getId());
                player.sendMessage("§a¡Te has unido al trabajo " + job.getDisplayName() + "!");
                player.closeInventory();
                return;
            }
        }
    }
}
