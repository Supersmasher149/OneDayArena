package com.alteredarena.sim;

import com.alteredarena.rules.Health;
import com.alteredarena.rules.Modifiers;
import com.alteredarena.rules.PowerUpState;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponState;
import com.alteredarena.rules.WeaponType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** One participant in the match: the player or a bot. */
public final class Combatant {
    public final int id;
    public final String name;
    public final boolean bot;
    public final int rgba;

    public final Health health = new Health(Tuning.MAX_HEALTH);
    public final WeaponState[] weapons = {new WeaponState(WeaponType.PULSE_RIFLE), new WeaponState(WeaponType.SCATTERGUN)};
    public final PowerUpState powerUp = new PowerUpState();
    public final Body body = new Body();
    public final Controls controls = new Controls();

    public int weaponIndex;
    public float switchTimer;
    /** Degrees; yaw 0 faces -Z, 90 faces +X. Pitch positive looks up. */
    public float yaw, pitch;
    public int kills, deaths;
    public boolean alive;
    public float respawnTimer;
    public float protection;
    public int lastAttackerId = -1;
    public float timeSinceDamaged = 999f;
    public float timeSinceFired = 999f;

    public Combatant(int id, String name, boolean bot, int rgba) {
        this.id = id;
        this.name = name;
        this.bot = bot;
        this.rgba = rgba;
    }

    public WeaponState weapon() { return weapons[weaponIndex]; }
    public Modifiers modifiers() { return powerUp.modifiers(); }
    public boolean isProtected() { return protection > 0f; }

    public Vector3 eye(Vector3 out) {
        return out.set(body.position).add(0f, Tuning.EYE_HEIGHT, 0f);
    }

    public Vector3 chest(Vector3 out) {
        return out.set(body.position).add(0f, Tuning.HEIGHT * 0.6f, 0f);
    }

    public Vector3 aimDirection(Vector3 out) {
        return direction(yaw, pitch, out);
    }

    public static Vector3 direction(float yawDeg, float pitchDeg, Vector3 out) {
        float cp = MathUtils.cosDeg(pitchDeg);
        return out.set(MathUtils.sinDeg(yawDeg) * cp, MathUtils.sinDeg(pitchDeg), -MathUtils.cosDeg(yawDeg) * cp);
    }

    /** Recoil climbs the aim slightly; the player pulls it back down. */
    public void applyRecoil(WeaponType type) {
        pitch = Math.min(85f, pitch + type.recoilDegrees * 0.35f);
    }

    /** Resets per-life state when spawning. */
    public void resetForSpawn(Vector3 feet, float spawnYaw) {
        health.reset();
        for (WeaponState w : weapons) w.refill();
        powerUp.clear();
        weaponIndex = 0;
        switchTimer = 0f;
        body.teleport(feet);
        yaw = spawnYaw;
        pitch = 0f;
        alive = true;
        protection = Tuning.SPAWN_PROTECTION;
        lastAttackerId = -1;
        timeSinceDamaged = 999f;
        controls.clear();
    }
}
