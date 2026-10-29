package com.alteredarena.sim;

import com.alteredarena.rules.Tuning;

/** Damage, healing and kill attribution. Independent of physics so it can be unit tested. */
public final class CombatRules {
    private CombatRules() {}

    public static final class DamageResult {
        public static final DamageResult NONE = new DamageResult(0f, 0f, false);
        public final float applied;
        public final float healed;
        public final boolean killed;

        DamageResult(float applied, float healed, boolean killed) {
            this.applied = applied;
            this.healed = healed;
            this.killed = killed;
        }
    }

    /**
     * Applies one attacker's damage to a victim. A dead victim takes nothing, so pellets, piercing
     * and several attackers in the same tick can only ever produce one kill.
     */
    public static DamageResult applyDamage(Combatant attacker, Combatant victim, float rawDamage, Match match) {
        if (!victim.alive || victim.isProtected() || rawDamage <= 0f || !match.isRunning()) return DamageResult.NONE;
        if (attacker == victim) return DamageResult.NONE;
        float applied = victim.health.takeDamage(rawDamage, victim.modifiers().damageTaken);
        if (applied <= 0f) return DamageResult.NONE;
        victim.timeSinceDamaged = 0f;
        float healed = 0f;
        if (attacker != null) {
            victim.lastAttackerId = attacker.id;
            if (attacker.alive) healed = attacker.health.heal(applied * attacker.modifiers().lifesteal);
        }
        boolean killed = false;
        if (victim.health.isDepleted()) {
            killed = true;
            kill(victim);
            match.recordKill(attacker, victim);
        }
        return new DamageResult(applied, healed, killed);
    }

    /** Marks a combatant dead and clears per-life effects. */
    public static void kill(Combatant victim) {
        victim.alive = false;
        victim.powerUp.clear();
        victim.respawnTimer = Tuning.RESPAWN_DELAY;
        victim.body.velocity.setZero();
        for (var w : victim.weapons) w.cancelReload();
    }
}
