package com.alteredarena.rules;

/** A character's single active power-up slot. */
public final class PowerUpState {
    public enum ApplyResult { NEW, REPLACED, REFRESHED }

    private PowerUpType active;
    private float remaining;

    /** Takes a power-up: same type refreshes the timer, a different type replaces the effect. */
    public ApplyResult apply(PowerUpType type) {
        ApplyResult result = active == null ? ApplyResult.NEW
                : active == type ? ApplyResult.REFRESHED : ApplyResult.REPLACED;
        active = type;
        remaining = type.duration;
        return result;
    }

    /** Advances the timer; returns the type that expired this update, or null. */
    public PowerUpType update(float dt) {
        if (active == null) return null;
        remaining -= dt;
        if (remaining > 0f) return null;
        PowerUpType expired = active;
        clear();
        return expired;
    }

    public void clear() {
        active = null;
        remaining = 0f;
    }

    /** Overwrites the slot (network replication); a null type clears it. */
    public void restore(PowerUpType type, float remaining) {
        active = type;
        this.remaining = type == null ? 0f : remaining;
    }

    public PowerUpType active() { return active; }
    public float remaining() { return remaining; }
    public boolean isActive() { return active != null; }

    /** Fraction of the duration left, 0..1. */
    public float fraction() {
        return active == null ? 0f : Math.max(0f, remaining / active.duration);
    }

    public Modifiers modifiers() {
        return active == null ? Modifiers.NONE : active.modifiers;
    }
}
