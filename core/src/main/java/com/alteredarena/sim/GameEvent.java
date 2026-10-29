package com.alteredarena.sim;

import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.WeaponType;
import com.badlogic.gdx.math.Vector3;

/** Something that happened in the simulation, consumed by audio, effects and the HUD. */
public final class GameEvent {
    public enum Type {
        SHOT, TRACER, IMPACT, HIT, KILL, HEAL, PICKUP, PICKUP_RESPAWN, POWERUP_EXPIRED,
        RESPAWN, JUMP, LAND, RELOAD_START, RELOAD_DONE, DRY_FIRE, WEAPON_SWITCH, OUT_OF_BOUNDS, MATCH_END
    }

    public final Type type;
    /** Combatant who caused it (shooter, collector, killer), or -1. */
    public int actor = -1;
    /** Combatant it happened to (victim), or -1. */
    public int target = -1;
    public final Vector3 position = new Vector3();
    public final Vector3 position2 = new Vector3();
    public float value;
    public boolean flag;
    public WeaponType weapon;
    public PowerUpType powerUp;
    public Pickup pickup;

    public GameEvent(Type type) {
        this.type = type;
    }
}
