package com.alteredarena.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerUpStateTest {
    @Test
    void takingADifferentPowerUpReplacesTheEffect() {
        PowerUpState s = new PowerUpState();
        assertEquals(PowerUpState.ApplyResult.NEW, s.apply(PowerUpType.COKE));
        assertEquals(PowerUpState.ApplyResult.REPLACED, s.apply(PowerUpType.WEED));
        assertEquals(PowerUpType.WEED, s.active());
        assertEquals(1f, s.modifiers().speed, "coke speed must not linger after replacement");
        assertEquals(0.65f, s.modifiers().damageTaken, 1e-6);
        assertEquals(PowerUpType.WEED.duration, s.remaining(), 1e-6);
    }

    @Test
    void takingTheSameOneRefreshesDurationWithoutStacking() {
        PowerUpState s = new PowerUpState();
        s.apply(PowerUpType.ADDY);
        s.update(8f);
        assertEquals(PowerUpState.ApplyResult.REFRESHED, s.apply(PowerUpType.ADDY));
        assertEquals(PowerUpType.ADDY.duration, s.remaining(), 1e-6);
        assertEquals(1.3f, s.modifiers().fireRate, 1e-6);
        assertEquals(0.5f, s.modifiers().spread, 1e-6);
    }

    @Test
    void everyPowerUpExpiresAndRestoresBaseline() {
        for (PowerUpType type : PowerUpType.values()) {
            PowerUpState s = new PowerUpState();
            s.apply(type);
            float t = 0f;
            PowerUpType expired = null;
            while (expired == null && t < 60f) {
                expired = s.update(Tuning.FIXED_DT);
                t += Tuning.FIXED_DT;
            }
            assertEquals(type, expired);
            assertEquals(type.duration, t, 0.02f, type + " duration");
            assertSame(Modifiers.NONE, s.modifiers());
            assertFalse(s.isActive());
        }
    }

    @Test
    void clearRemovesEffect() {
        PowerUpState s = new PowerUpState();
        s.apply(PowerUpType.MUSHROOMS);
        s.clear();
        assertNull(s.active());
        assertSame(Modifiers.NONE, s.modifiers());
    }

    @Test
    void configuredEffectsMatchDesign() {
        assertEquals(15f, PowerUpType.WEED.duration);
        assertEquals(0.25f, PowerUpType.MOLLY.modifiers.lifesteal, 1e-6);
        assertEquals(1, PowerUpType.ACID.modifiers.extraPierce);
        assertEquals(1, PowerUpType.MUSHROOMS.modifiers.extraJumps);
        assertEquals(1.25f, PowerUpType.MUSHROOMS.modifiers.jumpHeight, 1e-6);
        assertEquals(1.4f, PowerUpType.COKE.modifiers.speed, 1e-6);
        assertEquals(10f, PowerUpType.COKE.duration);
    }
}
