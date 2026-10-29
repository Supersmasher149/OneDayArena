package com.alteredarena.sim;

/** Per-tick intent for a combatant, filled by player input or a bot brain. */
public final class Controls {
    /** -1..1 */
    public float forward, strafe;
    public boolean jump;
    public boolean fire;
    public boolean reload;
    /** Weapon slot to select, or -1. */
    public int selectWeapon = -1;

    public void clear() {
        forward = strafe = 0f;
        jump = fire = reload = false;
        selectWeapon = -1;
    }
}
