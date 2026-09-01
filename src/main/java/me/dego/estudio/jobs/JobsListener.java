package me.dego.estudio.jobs;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class JobsListener implements Listener{
    private final JobManager jobManager;

    public JobsListener(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        jobManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        jobManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerJobData data = jobManager.getData(player.getUniqueId());
        if (data == null || !data.hasJob()) return;

        Job job = jobManager.getJob(data.getJobID());
        if (job == null) return;

        double[] reward = job.getBlockReward(event.getBlock().getType());
        if (reward == null) return; // este trabajo no da nada por este bloque

        jobManager.addReward(player, reward[0], reward[1]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return; // murió por otra causa (caída, fuego, otro mob...), no cuenta

        PlayerJobData data = jobManager.getData(killer.getUniqueId());
        if (data == null || !data.hasJob()) return;

        Job job = jobManager.getJob(data.getJobID());
        if (job == null) return;

        double[] reward = job.getMobReward(event.getEntityType());
        if (reward == null) return;

        jobManager.addReward(killer, reward[0], reward[1]);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // Solo nos interesa el momento en que se captura algo (no el lanzamiento de la caña)
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof org.bukkit.entity.Item caughtItem)) return;

        Player player = event.getPlayer();
        PlayerJobData data = jobManager.getData(player.getUniqueId());
        if (data == null || !data.hasJob()) return;

        Job job = jobManager.getJob(data.getJobID());
        if (job == null) return;

        ItemStack caught = caughtItem.getItemStack();
        double[] reward = job.getFishReward(caught.getType());
        if (reward == null) return;

        jobManager.addReward(player, reward[0], reward[1]);
    }
}
