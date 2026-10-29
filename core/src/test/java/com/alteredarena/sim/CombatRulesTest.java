package com.alteredarena.sim;

import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatRulesTest {
    @Test
    void manyHitsOnOneVictimAwardExactlyOneKill() {
        List<Combatant> cs = TestFighters.make(3);
        Match m = new Match(cs);
        Combatant a = cs.get(0), b = cs.get(1), v = cs.get(2);
        int kills = 0;
        for (int pellet = 0; pellet < 20; pellet++) {
            if (CombatRules.applyDamage(a, v, 9f, m).killed) kills++;
            if (CombatRules.applyDamage(b, v, 9f, m).killed) kills++; // second attacker in the same tick
        }
        assertEquals(1, kills);
        assertEquals(1, a.kills + b.kills);
        assertEquals(1, v.deaths);
        assertFalse(v.alive);
    }

    @Test
    void spawnProtectionBlocksDamage() {
        List<Combatant> cs = TestFighters.make(2);
        cs.get(1).protection = 1f;
        assertEquals(0f, CombatRules.applyDamage(cs.get(0), cs.get(1), 50f, new Match(cs)).applied);
        assertEquals(Tuning.MAX_HEALTH, cs.get(1).health.current());
    }

    @Test
    void mollyHealsQuarterOfDamageDealtCappedAtMax() {
        List<Combatant> cs = TestFighters.make(2);
        Combatant a = cs.get(0), v = cs.get(1);
        Match m = new Match(cs);
        a.powerUp.apply(PowerUpType.MOLLY);
        a.health.takeDamage(50f, 1f);
        assertEquals(10f, CombatRules.applyDamage(a, v, 40f, m).healed, 1e-4);
        assertEquals(60f, a.health.current(), 1e-4);
        a.health.heal(100f);
        assertEquals(0f, CombatRules.applyDamage(a, v, 40f, m).healed, 1e-4, "no healing above max");
    }

    @Test
    void mollyHealsFromActualDamageNotOverkill() {
        List<Combatant> cs = TestFighters.make(2);
        Combatant a = cs.get(0), v = cs.get(1);
        a.powerUp.apply(PowerUpType.MOLLY);
        a.health.takeDamage(60f, 1f);
        v.health.takeDamage(90f, 1f);
        CombatRules.DamageResult r = CombatRules.applyDamage(a, v, 72f, new Match(cs));
        assertEquals(10f, r.applied, 1e-4);
        assertEquals(2.5f, r.healed, 1e-4);
    }

    @Test
    void weedVictimTakesReducedDamage() {
        List<Combatant> cs = TestFighters.make(2);
        cs.get(1).powerUp.apply(PowerUpType.WEED);
        assertEquals(6.5f, CombatRules.applyDamage(cs.get(0), cs.get(1), 10f, new Match(cs)).applied, 1e-4);
    }

    @Test
    void deathClearsPowerUpAndStartsRespawnTimer() {
        List<Combatant> cs = TestFighters.make(2);
        Combatant v = cs.get(1);
        v.powerUp.apply(PowerUpType.ACID);
        assertTrue(CombatRules.applyDamage(cs.get(0), v, 500f, new Match(cs)).killed);
        assertFalse(v.powerUp.isActive());
        assertEquals(Tuning.RESPAWN_DELAY, v.respawnTimer);
    }

    @Test
    void noDamageAfterMatchEnds() {
        List<Combatant> cs = TestFighters.make(2);
        Match m = new Match(cs, 1, 300f);
        CombatRules.applyDamage(cs.get(0), cs.get(1), 500f, m);
        assertFalse(m.isRunning());
        cs.get(1).alive = true;
        cs.get(1).health.reset();
        assertEquals(0f, CombatRules.applyDamage(cs.get(0), cs.get(1), 10f, m).applied);
    }
}
