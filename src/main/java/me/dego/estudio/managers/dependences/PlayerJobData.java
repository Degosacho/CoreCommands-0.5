package me.dego.estudio.managers.dependences;

import java.util.UUID;

public class PlayerJobData {

    private final UUID uuid;
    private String jobID;   // null si el jugador no tiene trabajo asignado
    private int level;
    private double xp;

    public PlayerJobData(UUID uuid, String jobID, int level, double xp) {
        this.uuid = uuid;
        this.jobID = jobID;
        this.level = level;
        this.xp = xp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getJobID() {
        return jobID;
    }

    public void setJobID(String jobID) {
        this.jobID = jobID;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public boolean hasJob() {
        return jobID != null;
    }
}
