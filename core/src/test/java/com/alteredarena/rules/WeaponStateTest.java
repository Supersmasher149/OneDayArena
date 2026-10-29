package com.alteredarena.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponStateTest {
    private static int holdTrigger(WeaponState w, float seconds, float fireRate) {
        int shots = 0;
        int ticks = Math.round(seconds / Tuning.FIXED_DT);
        for (int i = 0; i < ticks; i++) {
            w.update(Tuning.FIXED_DT);
            if (w.tryFire(fireRate)) shots++;
        }
        return shots;
    }

    @Test
    void rifleCadenceMatchesFireInterval() {
        WeaponState w = new WeaponState(WeaponType.PULSE_RIFLE);
        int shots = holdTrigger(w, 1.0f, 1f);
        assertEquals(1 + (int) (1.0f / WeaponType.PULSE_RIFLE.fireInterval), shots, 1);
        assertEquals(WeaponType.PULSE_RIFLE.magazineSize - shots, w.magazine());
    }

    @Test
    void addyFireRateAppliesOnlyWhilePassedAndLeavesNoDrift() {
        WeaponState boosted = new WeaponState(WeaponType.SCATTERGUN);
        int fast = holdTrigger(boosted, 4.0f, 1.3f);
        WeaponState base = new WeaponState(WeaponType.SCATTERGUN);
        int normal = holdTrigger(base, 4.0f, 1f);
        assertTrue(fast > normal, fast + " vs " + normal);

        // After the effect ends, the same weapon fires at baseline cadence again.
        boosted.refill();
        WeaponState fresh = new WeaponState(WeaponType.SCATTERGUN);
        assertEquals(holdTrigger(fresh, 3.0f, 1f), holdTrigger(boosted, 3.0f, 1f));
    }

    @Test
    void cannotFireEmptyOrWhileReloading() {
        WeaponState w = new WeaponState(WeaponType.SCATTERGUN);
        int shots = holdTrigger(w, 10f, 1f);
        assertEquals(WeaponType.SCATTERGUN.magazineSize, shots, "stops at an empty magazine");
        assertEquals(0, w.magazine());
        assertTrue(w.startReload());
        assertFalse(w.tryFire(1f));
        assertFalse(w.startReload(), "reload cannot restart while running");
    }

    @Test
    void reloadMovesRoundsFromReserve() {
        WeaponState w = new WeaponState(WeaponType.PULSE_RIFLE);
        holdTrigger(w, 1.0f, 1f);
        int mag = w.magazine(), reserve = w.reserve();
        assertTrue(w.startReload());
        boolean done = false;
        for (float t = 0; t < WeaponType.PULSE_RIFLE.reloadTime + 0.05f; t += Tuning.FIXED_DT) done |= w.update(Tuning.FIXED_DT);
        assertTrue(done);
        assertEquals(WeaponType.PULSE_RIFLE.magazineSize, w.magazine());
        assertEquals(reserve - (WeaponType.PULSE_RIFLE.magazineSize - mag), w.reserve());
    }

    @Test
    void reloadRequiresMissingRoundsAndReserve() {
        WeaponState w = new WeaponState(WeaponType.PULSE_RIFLE);
        assertFalse(w.startReload(), "full magazine");
        WeaponState empty = new WeaponState(WeaponType.SCATTERGUN);
        while (empty.reserve() > 0 || empty.magazine() > 0) {
            holdTrigger(empty, 6f, 1f);
            empty.startReload();
            for (int i = 0; i < 400; i++) empty.update(Tuning.FIXED_DT);
        }
        assertTrue(empty.isEmpty());
        assertFalse(empty.startReload());
    }

    @Test
    void reserveIsCapped() {
        WeaponState w = new WeaponState(WeaponType.SCATTERGUN);
        int added = w.addReserve(1000);
        assertEquals(WeaponType.SCATTERGUN.maxReserve - WeaponType.SCATTERGUN.startingReserve, added);
        assertEquals(0, w.addReserve(5));
    }

    @Test
    void damageFalloffIsBounded() {
        WeaponType s = WeaponType.SCATTERGUN;
        assertEquals(s.damage, s.damageAt(1f));
        assertEquals(s.damage * s.minDamageMultiplier, s.damageAt(100f), 1e-5);
        assertTrue(s.damageAt(15f) < s.damage && s.damageAt(15f) > s.damage * s.minDamageMultiplier);
        assertTrue(WeaponType.PULSE_RIFLE.damageAt(50f) > s.damageAt(50f) / s.pellets, "rifle owns long range");
    }
}
