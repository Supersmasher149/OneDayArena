package com.alteredarena.rules;

/** Central gameplay tuning. World units are meters; times are seconds. */
public final class Tuning {
    private Tuning() {}

    // Simulation
    public static final float FIXED_DT = 1f / 120f;
    /** Longest frame time we will simulate; anything beyond (window drags, focus changes) is dropped. */
    public static final float MAX_FRAME_TIME = 0.1f;
    public static final int MAX_STEPS_PER_FRAME = 12;

    // Match
    public static final int KILL_LIMIT = 15;
    public static final float MATCH_DURATION = 300f;
    public static final int BOT_COUNT = 4;

    // Character
    public static final float MAX_HEALTH = 100f;
    public static final float RADIUS = 0.4f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE_HEIGHT = 1.6f;
    public static final float HITBOX_RADIUS = 0.45f;
    public static final float RESPAWN_DELAY = 3f;
    public static final float SPAWN_PROTECTION = 2f;
    public static final float WEAPON_SWITCH_TIME = 0.35f;

    // Movement
    public static final float MOVE_SPEED = 7.5f;
    public static final float GROUND_ACCEL = 70f;
    public static final float GROUND_DECEL = 55f;
    public static final float AIR_ACCEL = 18f;
    public static final float GRAVITY = 24f;
    public static final float JUMP_HEIGHT = 1.25f;
    public static final float COYOTE_TIME = 0.1f;
    public static final float MAX_FALL_SPEED = 40f;
    /** Minimum surface normal Y that counts as walkable ground (about 46 degrees). */
    public static final float WALKABLE_NORMAL_Y = 0.69f;
    public static final float OUT_OF_BOUNDS_Y = -12f;

    // Pickups
    public static final float HEALTH_PICKUP_AMOUNT = 50f;
    public static final float HEALTH_RESPAWN = 20f;
    public static final float AMMO_RESPAWN = 15f;
    public static final float POWERUP_RESPAWN = 30f;
    public static final float PICKUP_RADIUS = 1.1f;
}
