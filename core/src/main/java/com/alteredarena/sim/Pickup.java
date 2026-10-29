package com.alteredarena.sim;

import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.badlogic.gdx.math.Vector3;

public final class Pickup {
    public final int id;
    public final PickupKind kind;
    public final PowerUpType powerUp;
    public final Vector3 position;
    public final float respawnTime;
    public boolean active = true;
    public float timer;

    public Pickup(int id, PickupKind kind, PowerUpType powerUp, Vector3 position, float respawnTime) {
        this.id = id;
        this.kind = kind;
        this.powerUp = powerUp;
        this.position = new Vector3(position);
        this.respawnTime = respawnTime;
    }

    public static float defaultRespawn(PickupKind kind) {
        switch (kind) {
            case HEALTH: return Tuning.HEALTH_RESPAWN;
            case AMMO: return Tuning.AMMO_RESPAWN;
            default: return Tuning.POWERUP_RESPAWN;
        }
    }

    public String label() {
        switch (kind) {
            case HEALTH: return "HEALTH";
            case AMMO: return "AMMO";
            default: return powerUp.label;
        }
    }

    /** @return true when the pickup reappeared this update */
    public boolean update(float dt) {
        if (active) return false;
        timer -= dt;
        if (timer > 0f) return false;
        active = true;
        return true;
    }

    public void consume() {
        active = false;
        timer = respawnTime;
    }
}
