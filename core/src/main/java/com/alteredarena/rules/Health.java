package com.alteredarena.rules;

public final class Health {
    private final float max;
    private float current;

    public Health(float max) {
        this.max = max;
        this.current = max;
    }

    /**
     * Applies damage scaled by the victim's damage-taken multiplier.
     * @return damage actually removed (never more than the health that remained)
     */
    public float takeDamage(float raw, float damageTakenMultiplier) {
        if (raw <= 0f || current <= 0f) return 0f;
        float applied = Math.min(current, raw * damageTakenMultiplier);
        current -= applied;
        return applied;
    }

    /** @return health actually restored, capped at maximum */
    public float heal(float amount) {
        if (amount <= 0f || current <= 0f) return 0f;
        float applied = Math.min(max - current, amount);
        current += applied;
        return applied;
    }

    public void reset() { current = max; }
    /** Overwrites the current value (network replication). */
    public void set(float value) { current = Math.max(0f, Math.min(max, value)); }
    public float current() { return current; }
    public float max() { return max; }
    public boolean isDepleted() { return current <= 0f; }
}
