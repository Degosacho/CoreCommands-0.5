package me.dego.estudio.jobs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class JobsCommand implements CommandExecutor {

    private final JobManager jobManager;

    public JobsCommand(JobManager jobManager){
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            System.out.println("No eres un usuario!");
            return true;
        }

        JobsMenu menu = new JobsMenu(jobManager, player);
        player.openInventory(menu.getInventory());
        return true;
    }
}