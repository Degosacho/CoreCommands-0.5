package me.dego.estudio.jobs;

import me.dego.estudio.dependences.VaultEconomy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    private final Plugin plugin;
    private final JobDataBase database;
    private final VaultEconomy vaultEconomy;


    // Definición de trabajos disponibles
    private final Map<String, Job> jobs = new LinkedHashMap<>();

    // Progreso en memoria de jugadores conectados: uuid -> PlayerJobData
    private final Map<UUID, PlayerJobData> cache = new LinkedHashMap<>();

    public JobManager(Plugin plugin, JobDataBase database, VaultEconomy vaultEconomy){
        this.plugin = plugin;
        this.database = database;
        this.vaultEconomy = vaultEconomy;
    }

    // Carga de configuración (jobs.yml)
    public void loadJobsFromConfig(){
        jobs.clear();
        File file = new File(plugin.getDataFolder(), "jobs.yml");

        if(!file.exists()){
            // Copia el jobs.yml de ejemplo incuído
            plugin.saveResource("jobs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection jobsSection = config.getConfigurationSection("jobs");

        if(jobsSection == null){
            plugin.getLogger().warning("jobs.yml no tiene ninguna sección 'jobs:' definida.");
            return;
        }

        for (String jobId : jobsSection.getKeys(false)){
            ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobId);
            if(jobSection == null) continue;

            String displayName = jobSection.getString("display-name", jobId);
            Job job = new Job(jobId, displayName);

            // Recompensas por romper bloques
            ConfigurationSection blocksSection = jobSection.getConfigurationSection("blocks");
            if(blocksSection != null){
                for (String matName : blocksSection.getKeys(false)) {
                    Material material = Material.matchMaterial(matName);
                    if(material == null){
                        plugin.getLogger().warning("Material inválido en jobs.yml: " + matName);
                        continue;
                    }
                    double xp = blocksSection.getDouble(matName + ".xp");
                    double money = blocksSection.getDouble(matName + ".money");
                    job.addBlockReward(material, xp, money);
                }
            }

            // Recompensas por matar mobs
            ConfigurationSection mobsSection = jobSection.getConfigurationSection("mobs");
            if(mobsSection != null){
                for (String entityName : mobsSection.getKeys(false)){
                    EntityType type;
                    try{
                        type = EntityType.valueOf(entityName.toUpperCase());
                    } catch (IllegalArgumentException e){
                        plugin.getLogger().warning("Entidad inválida en jobs.yml: " + entityName);
                        continue;
                    }
                    double xp = mobsSection.getDouble(entityName + ".xp");
                    double money = mobsSection.getDouble(entityName + ".money");
                    job.addMobReward(type, xp, money);
                }
            }

            // Recompensas por pescar
            ConfigurationSection fishSection = jobSection.getConfigurationSection("fish");
            if(fishSection != null){
                for(String matName : fishSection.getKeys(false)){
                    Material material = Material.matchMaterial(matName);
                    if(material == null){
                        plugin.getLogger().warning("Material de pesca inbválido en jobs.yml: " + matName);
                        continue;
                    }
                    double xp = fishSection.getDouble(matName + ".xp");
                    double money = fishSection.getDouble(matName + ".money");
                    job.addFishReward(material, xp, money);
                }
            }

            jobs.put(jobId.toLowerCase(), job);
        }

        plugin.getLogger().info("Cargados " + jobs.size() + " trabajos desde jobs.yml");
    }

    public Job getJob(String id){
        return jobs.get(id.toLowerCase());
    }

    public Map<String, Job> getAllJobs(){
        return jobs;
    }

    // =================================
    //  Ciclo de vida del jugador
    // =================================

    // Llamar desde PlayerJoinEvent
    public void loadPlayer(UUID uuid){
        PlayerJobData data = database.load(uuid);
        cache.put(uuid, data);
    }

    // Llamar desde PlayerQuitEvent
    public void unloadPlayer(UUID uuid){
        PlayerJobData data = cache.remove(uuid);
        if (data != null) {
            database.save(data);
        }
    }

    // Guarda a todos los jugadores en memoria sin descargarlos. En onDisable
    public void saveAll(){
         for(PlayerJobData data : cache.values()){
             database.save(data);
         }
    }

    public PlayerJobData getData(UUID uuid){
        return cache.get(uuid);
    }

    //  ========================
    //  Unirse/Dejar un trabajo
    // =========================

    public boolean joinJob(Player player, String jobId){
        Job job = getJob(jobId);
        if(job == null) return false;

        PlayerJobData data = cache.get(player.getUniqueId());
        if (data == null) return false;

        data.setJobID(job.getId());
        data.setLevel(1);
        data.setXp(0);
        database.save(data);  // guardado inmediato, no esperamos al quit

        player.sendMessage("§aAhora eres §e" + job.getDisplayName() + "§a.");
        return true;
    }

    public void leaveJob(Player player){
        PlayerJobData data = cache.get(player.getUniqueId());
        if(data == null || !data.hasJob()) return;

        data.setJobID(null);
        database.save(data);
        player.sendMessage("§cHas dejado tu trabajo.");
    }

    //  ============================
    //  XP y subida de nivel
    //  ============================

    // XP necesaria para pasar del nivel atual al siguiente.
    private double xpForNextLevel(int currentLevel){
        return 100.0 * currentLevel;
    }

    // Añade xp y dinero al jugador por una acción
    public void addReward(Player player, double xp, double money){
        PlayerJobData data = cache.get(player.getUniqueId());
        if(data == null || !data.hasJob()) return;

        data.setXp(data.getXp() + xp);

        //  Comprueba subidas de nivel en bucle por si la XP alcanza para varios niveles de golpe
        while(data.getXp() >= xpForNextLevel(data.getLevel())){
            data.setXp(data.getXp() - xpForNextLevel(data.getLevel()));
            data.setLevel(data.getLevel() + 1);
            player.sendMessage("§6§l¡Has subido a nivel " + data.getLevel() + " en tu trabajo!");
        }

        if(vaultEconomy.isEnabled() && money > 0){
            vaultEconomy.giveMoney(player, money);
        }
    }
}
