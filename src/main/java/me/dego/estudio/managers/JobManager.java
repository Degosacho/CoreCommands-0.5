package me.dego.estudio.managers;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class JobManager {

    private final String id;    // Identificador
    private final String displayName; // Nombre

    // Recompensas por romper cada tipo de bloque: Material -> [xp, dinero]
    private final Map<Material, double[]> blockRewards = new HashMap<>();

    // Recompensas por matar cada tipo de mob: EntityType -> [xp, dinero]
    private final Map<EntityType, double[]> mobRewards = new HashMap<>();

    // Recompensas por pescar cada tipo de item: Material -> [xp, dinero]
    private final Map<Material, double[]> fishRewards = new HashMap<>();

    public JobManager(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }


    // Añade {xp, dinero} para el bloque, o null si no lo recompensa
    public void addBlockReward(Material material, double xp, double money) {
        blockRewards.put(material, new double[]{xp, money});
    }

    public void addMobReward(EntityType type, double xp, double money) {
        mobRewards.put(type, new double[]{xp, money});
    }

    public void addFishReward(Material material, double xp, double money) {
        fishRewards.put(material, new double[]{xp, money});
    }

    // Devuelve {xp, dinero} para ese bloque, o null si este trabajo no lo recompensa

    public double[] getBlockReward(Material material) {
        return blockRewards.get(material);
    }

    public double[] getModReward(EntityType type) {
        return mobRewards.get(type);
    }

    public double[] getFishReward(Material material) {
        return fishRewards.get(material);
    }
}
