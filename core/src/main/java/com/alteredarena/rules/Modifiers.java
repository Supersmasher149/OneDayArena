package com.alteredarena.rules;

/**
 * Immutable set of gameplay multipliers produced by the active power-up.
 * Characters never store modified stats; they read these every tick, so expiry cannot leave drift behind.
 */
public final class Modifiers {
    public static final Modifiers NONE = new Modifiers(1f, 0f, 0, 0, 1f, 1f, 1f, 1f, 1f);

    /** Multiplier applied to incoming damage (0.65 = 35% less). */
    public final float damageTaken;
    /** Fraction of damage dealt returned as healing. */
    public final float lifesteal;
    /** Extra opponents a shot may pass through (never walls). */
    public final int extraPierce;
    /** Extra jumps available while airborne. */
    public final int extraJumps;
    /** Multiplier on jump apex height. */
    public final float jumpHeight;
    /** Multiplier on maximum movement speed. */
    public final float speed;
    /** Multiplier on acceleration and deceleration. */
    public final float acceleration;
    /** Multiplier on fire rate (1.3 = 30% faster). */
    public final float fireRate;
    /** Multiplier on weapon spread (0.5 = half the spread). */
    public final float spread;

    private Modifiers(float damageTaken, float lifesteal, int extraPierce, int extraJumps, float jumpHeight,
                      float speed, float acceleration, float fireRate, float spread) {
        this.damageTaken = damageTaken;
        this.lifesteal = lifesteal;
        this.extraPierce = extraPierce;
        this.extraJumps = extraJumps;
        this.jumpHeight = jumpHeight;
        this.speed = speed;
        this.acceleration = acceleration;
        this.fireRate = fireRate;
        this.spread = spread;
    }

    public Modifiers damageTaken(float v) { return new Modifiers(v, lifesteal, extraPierce, extraJumps, jumpHeight, speed, acceleration, fireRate, spread); }
    public Modifiers lifesteal(float v) { return new Modifiers(damageTaken, v, extraPierce, extraJumps, jumpHeight, speed, acceleration, fireRate, spread); }
    public Modifiers extraPierce(int v) { return new Modifiers(damageTaken, lifesteal, v, extraJumps, jumpHeight, speed, acceleration, fireRate, spread); }
    public Modifiers extraJumps(int v) { return new Modifiers(damageTaken, lifesteal, extraPierce, v, jumpHeight, speed, acceleration, fireRate, spread); }
    public Modifiers jumpHeight(float v) { return new Modifiers(damageTaken, lifesteal, extraPierce, extraJumps, v, speed, acceleration, fireRate, spread); }
    public Modifiers speed(float v) { return new Modifiers(damageTaken, lifesteal, extraPierce, extraJumps, jumpHeight, v, acceleration, fireRate, spread); }
    public Modifiers acceleration(float v) { return new Modifiers(damageTaken, lifesteal, extraPierce, extraJumps, jumpHeight, speed, v, fireRate, spread); }
    public Modifiers fireRate(float v) { return new Modifiers(damageTaken, lifesteal, extraPierce, extraJumps, jumpHeight, speed, acceleration, v, spread); }
    public Modifiers spread(float v) { return new Modifiers(damageTaken, lifesteal, extraPierce, extraJumps, jumpHeight, speed, acceleration, fireRate, v); }
}
