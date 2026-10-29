package com.alteredarena.rules;

/**
 * Ammo, cadence and reload state for one weapon. Fire-rate modifiers are passed in per shot,
 * so nothing modified is ever stored.
 */
public final class WeaponState {
    public final WeaponType type;
    private int magazine;
    private int reserve;
    private float cooldown;
    private float reloadRemaining;

    public WeaponState(WeaponType type) {
        this.type = type;
        refill();
    }

    /** Restores the spawn loadout. */
    public void refill() {
        magazine = type.magazineSize;
        reserve = type.startingReserve;
        cooldown = 0f;
        reloadRemaining = 0f;
    }

    /** Advances cooldown and reload timers. Returns true when a reload completed during this update. */
    public boolean update(float dt) {
        // Cooldown may dip just below zero so cadence stays exact at any tick rate; it never banks extra shots.
        if (cooldown > 0f) cooldown -= dt;
        if (reloadRemaining > 0f) {
            reloadRemaining -= dt;
            if (reloadRemaining <= 0f) {
                reloadRemaining = 0f;
                int taken = Math.min(type.magazineSize - magazine, reserve);
                magazine += taken;
                reserve -= taken;
                return true;
            }
        }
        return false;
    }

    public boolean canFire() {
        return reloadRemaining <= 0f && cooldown <= 0f && magazine > 0;
    }

    /** Consumes one round if the weapon is ready. */
    public boolean tryFire(float fireRateMultiplier) {
        if (!canFire()) return false;
        magazine--;
        cooldown += type.fireInterval / fireRateMultiplier;
        return true;
    }

    /** @return true if a reload started */
    public boolean startReload() {
        if (isReloading() || magazine >= type.magazineSize || reserve <= 0) return false;
        reloadRemaining = type.reloadTime;
        return true;
    }

    public void cancelReload() { reloadRemaining = 0f; }

    /** @return rounds actually added */
    public int addReserve(int amount) {
        int added = Math.max(0, Math.min(amount, type.maxReserve - reserve));
        reserve += added;
        return added;
    }

    public boolean isReloading() { return reloadRemaining > 0f; }
    public float reloadProgress() { return isReloading() ? 1f - reloadRemaining / type.reloadTime : 0f; }
    public int magazine() { return magazine; }
    public int reserve() { return reserve; }
    public boolean isEmpty() { return magazine == 0 && reserve == 0; }
    public boolean ammoLow() { return magazine + reserve <= type.magazineSize; }
    public float cooldown() { return cooldown; }
}
