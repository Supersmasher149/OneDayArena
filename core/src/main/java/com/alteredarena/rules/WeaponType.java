package com.alteredarena.rules;

/** Weapon tuning table. Damage values are per pellet; falloff scales linearly between start and end. */
public enum WeaponType {
    PULSE_RIFLE("PULSE RIFLE", 8f, 1, 0.095f, 30, 90, 180, 1.5f, 0.6f, 1.4f, 120f, 30f, 70f, 0.6f, 0.45f, 60),
    SCATTERGUN("SCATTERGUN", 8f, 9, 0.80f, 6, 18, 36, 1.9f, 5.5f, 6.5f, 45f, 7f, 22f, 0.25f, 2.6f, 12);

    public final String label;
    public final float damage;
    public final int pellets;
    /** Seconds between shots at baseline fire rate. */
    public final float fireInterval;
    public final int magazineSize;
    public final int startingReserve;
    public final int maxReserve;
    public final float reloadTime;
    /** Cone half-angle in degrees while standing still. */
    public final float spreadDegrees;
    /** Cone half-angle in degrees while moving or airborne. */
    public final float movingSpreadDegrees;
    public final float range;
    public final float falloffStart;
    public final float falloffEnd;
    public final float minDamageMultiplier;
    /** View kick in degrees per shot. */
    public final float recoilDegrees;
    /** Reserve ammo granted by an ammo pickup. */
    public final int ammoPickupAmount;

    WeaponType(String label, float damage, int pellets, float fireInterval, int magazineSize, int startingReserve,
               int maxReserve, float reloadTime, float spreadDegrees, float movingSpreadDegrees, float range,
               float falloffStart, float falloffEnd, float minDamageMultiplier, float recoilDegrees, int ammoPickupAmount) {
        this.label = label;
        this.damage = damage;
        this.pellets = pellets;
        this.fireInterval = fireInterval;
        this.magazineSize = magazineSize;
        this.startingReserve = startingReserve;
        this.maxReserve = maxReserve;
        this.reloadTime = reloadTime;
        this.spreadDegrees = spreadDegrees;
        this.movingSpreadDegrees = movingSpreadDegrees;
        this.range = range;
        this.falloffStart = falloffStart;
        this.falloffEnd = falloffEnd;
        this.minDamageMultiplier = minDamageMultiplier;
        this.recoilDegrees = recoilDegrees;
        this.ammoPickupAmount = ammoPickupAmount;
    }

    /** Per-pellet damage at the given distance. */
    public float damageAt(float distance) {
        if (distance <= falloffStart) return damage;
        if (distance >= falloffEnd) return damage * minDamageMultiplier;
        float t = (distance - falloffStart) / (falloffEnd - falloffStart);
        return damage * (1f - t * (1f - minDamageMultiplier));
    }
}
