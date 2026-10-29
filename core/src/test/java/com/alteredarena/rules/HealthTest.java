package com.alteredarena.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthTest {
    @Test
    void weedReducesIncomingDamageBy35Percent() {
        Health h = new Health(100f);
        float applied = h.takeDamage(40f, PowerUpType.WEED.modifiers.damageTaken);
        assertEquals(26f, applied, 1e-4);
        assertEquals(74f, h.current(), 1e-4);
    }

    @Test
    void damageNeverExceedsRemainingHealth() {
        Health h = new Health(100f);
        h.takeDamage(90f, 1f);
        assertEquals(10f, h.takeDamage(50f, 1f), 1e-4);
        assertTrue(h.isDepleted());
        assertEquals(0f, h.takeDamage(10f, 1f), "dead targets take no further damage");
    }

    @Test
    void healingIsCappedAtMaximum() {
        Health h = new Health(100f);
        h.takeDamage(10f, 1f);
        assertEquals(10f, h.heal(50f), 1e-4);
        assertEquals(100f, h.current(), 1e-4);
        assertEquals(0f, h.heal(5f), 1e-4);
    }

    @Test
    void deadCannotBeHealed() {
        Health h = new Health(100f);
        h.takeDamage(100f, 1f);
        assertEquals(0f, h.heal(25f));
    }
}
