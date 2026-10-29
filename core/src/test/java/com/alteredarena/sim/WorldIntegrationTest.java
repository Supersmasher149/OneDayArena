package com.alteredarena.sim;

import com.alteredarena.level.ArenaLayout;
import com.alteredarena.level.LevelData;
import com.alteredarena.rules.Modifiers;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponType;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Simulation tests against the real Bullet collision world (headless, no graphics). */
class WorldIntegrationTest {
    private static final LevelData LEVEL = ArenaLayout.build();
    private GameWorld world;

    private GameWorld world(int bots) {
        world = new GameWorld(LEVEL, 42, bots, false);
        for (Combatant c : world.combatants) c.protection = 0f;
        return world;
    }

    @AfterEach
    void tearDown() {
        if (world != null) world.dispose();
    }

    private static void place(Combatant c, float x, float z, float yaw) {
        c.body.teleport(new Vector3(x, 0f, z));
        c.body.grounded = true;
        c.yaw = yaw;
        c.pitch = 0f;
        c.controls.clear();
    }

    private void fireUntilEmpty(Combatant c, int shots) {
        for (int i = 0; i < shots; i++) {
            c.weapon().cancelReload();
            for (int t = 0; t < 30; t++) c.weapon().update(Tuning.FIXED_DT);
            world.fire(c);
        }
    }

    @Test
    void wallsBlockShots() {
        GameWorld w = world(1);
        Combatant shooter = w.combatants.get(0), victim = w.combatants.get(1);
        // East lane wall: x = 21, z in [-7.5, -1.5], 4 m tall.
        place(shooter, 17f, -4f, 90f);
        place(victim, 25f, -4f, 0f);
        fireUntilEmpty(shooter, 20);
        assertEquals(Tuning.MAX_HEALTH, victim.health.current(), "no damage through the wall");

        place(victim, 19.5f, -4f, 0f);
        fireUntilEmpty(shooter, 3);
        assertTrue(victim.health.current() < Tuning.MAX_HEALTH, "same line of fire without the wall hits");
    }

    @Test
    void scattergunThroughWallDoesNothing() {
        GameWorld w = world(1);
        Combatant shooter = w.combatants.get(0), victim = w.combatants.get(1);
        shooter.weaponIndex = 1;
        place(shooter, 20.0f, -4f, 90f);
        place(victim, 22.2f, -4f, 0f);
        fireUntilEmpty(shooter, 3);
        assertEquals(Tuning.MAX_HEALTH, victim.health.current());
    }

    @Test
    void acidPiercesOneExtraOpponentButNeverWalls() {
        GameWorld w = world(3);
        Combatant shooter = w.combatants.get(0);
        Combatant first = w.combatants.get(1), second = w.combatants.get(2), behindWall = w.combatants.get(3);
        place(shooter, 12f, -4f, 90f);
        place(first, 15f, -4f, 0f);
        place(second, 18f, -4f, 0f);
        place(behindWall, 25f, -4f, 0f);

        fireUntilEmpty(shooter, 1);
        assertTrue(first.health.current() < 100f);
        assertEquals(100f, second.health.current(), "without acid the first body stops the shot");

        first.health.reset();
        shooter.powerUp.apply(PowerUpType.ACID);
        fireUntilEmpty(shooter, 1);
        assertTrue(first.health.current() < 100f);
        assertTrue(second.health.current() < 100f, "acid passes through one opponent");

        first.body.teleport(new Vector3(0, 0, 25));
        second.health.reset();
        fireUntilEmpty(shooter, 10);
        assertTrue(second.health.current() < 100f);
        assertEquals(100f, behindWall.health.current(), "acid never passes walls");
    }

    @Test
    void scattergunKillAwardsOneKill() {
        GameWorld w = world(1);
        Combatant shooter = w.combatants.get(0), victim = w.combatants.get(1);
        shooter.weaponIndex = 1;
        place(shooter, 12f, -4f, 90f);
        place(victim, 13.5f, -4f, 0f);
        victim.health.takeDamage(95f, 1f);
        w.events.clear();
        fireUntilEmpty(shooter, 1);
        assertFalse(victim.alive);
        assertEquals(1, shooter.kills);
        assertEquals(1, w.events.stream().filter(e -> e.type == GameEvent.Type.KILL).count());
    }

    @Test
    void deadCombatantRespawnsWithFreshState() {
        GameWorld w = world(1);
        Combatant v = w.combatants.get(1);
        v.powerUp.apply(PowerUpType.COKE);
        v.weapons[0].tryFire(1f);
        CombatRules.applyDamage(w.combatants.get(0), v, 500f, w.match);
        assertFalse(v.powerUp.isActive());
        for (int i = 0; i < (int) (Tuning.RESPAWN_DELAY / Tuning.FIXED_DT) + 2; i++) w.step(Tuning.FIXED_DT);
        assertTrue(v.alive);
        assertEquals(Tuning.MAX_HEALTH, v.health.current());
        assertEquals(WeaponType.PULSE_RIFLE.magazineSize, v.weapons[0].magazine());
        assertTrue(v.isProtected());
    }

    @Test
    void pickupsReplaceRefreshAndRespawn() {
        GameWorld w = world(0);
        Combatant p = w.player();
        Pickup weed = find(w, PowerUpType.WEED), coke = find(w, PowerUpType.COKE);
        assertTrue(w.tryCollect(p, coke));
        assertEquals(PowerUpType.COKE, p.powerUp.active());
        assertTrue(w.tryCollect(p, weed));
        assertEquals(PowerUpType.WEED, p.powerUp.active(), "replaced");
        assertFalse(weed.active);
        for (int i = 0; i < (int) (Tuning.POWERUP_RESPAWN / Tuning.FIXED_DT) + 2; i++) weed.update(Tuning.FIXED_DT);
        assertTrue(weed.active, "respawns after its cooldown");
        p.powerUp.update(5f);
        assertTrue(w.tryCollect(p, weed));
        assertEquals(PowerUpType.WEED.duration, p.powerUp.remaining(), 1e-4, "refreshed");
    }

    @Test
    void healthPickupIgnoredAtFullHealth() {
        GameWorld w = world(0);
        Combatant p = w.player();
        Pickup health = w.pickups.stream().filter(x -> x.kind == LevelData.PickupKind.HEALTH).findFirst().get();
        assertFalse(w.tryCollect(p, health));
        p.health.takeDamage(80f, 1f);
        assertTrue(w.tryCollect(p, health));
        assertEquals(70f, p.health.current(), 1e-4);
    }

    @Test
    void walkingOverAPickupCollectsIt() {
        GameWorld w = world(0);
        Combatant p = w.player();
        Pickup addy = find(w, PowerUpType.ADDY);
        p.body.teleport(new Vector3(addy.position).add(0f, 0.05f, 0f));
        w.step(Tuning.FIXED_DT);
        assertEquals(PowerUpType.ADDY, p.powerUp.active());
    }

    @Test
    void cokeSpeedAndMushroomJumpRestoreAfterExpiry() {
        GameWorld w = world(0);
        Combatant p = w.player();
        p.protection = 0f;
        place(p, -20f, 20f, 90f);
        w.step(Tuning.FIXED_DT);
        p.powerUp.apply(PowerUpType.COKE);
        runForward(w, p, 1.0f);
        assertEquals(Tuning.MOVE_SPEED * 1.4f, p.body.horizontalSpeed(), 0.2f);
        place(p, -20f, 20f, 90f);
        runForward(w, p, PowerUpType.COKE.duration + 0.5f);
        assertFalse(p.powerUp.isActive());
        place(p, -20f, 20f, 90f);
        runForward(w, p, 1.0f);
        assertEquals(Tuning.MOVE_SPEED, p.body.horizontalSpeed(), 0.2f, "baseline speed after expiry");

        assertEquals(1, PowerUpType.MUSHROOMS.modifiers.extraJumps);
        assertTrue(maxJumpHeight(w, p, PowerUpType.MUSHROOMS) > maxJumpHeight(w, p, null) + 1.0f, "double jump goes higher");
        float base = maxJumpHeight(w, p, null);
        assertEquals(Tuning.JUMP_HEIGHT, base, 0.1f);
    }

    private void runForward(GameWorld w, Combatant p, float seconds) {
        for (int i = 0; i < seconds / Tuning.FIXED_DT; i++) {
            p.controls.clear();
            p.controls.forward = 1f;
            w.step(Tuning.FIXED_DT);
            if (Math.abs(p.body.position.x) > 26f) place(p, -20f, 20f, 90f);
        }
    }

    /** Jumps (and double-jumps at the apex) and returns the peak height gained. */
    private float maxJumpHeight(GameWorld w, Combatant p, PowerUpType power) {
        place(p, -24f, 21f, 90f);
        p.powerUp.clear();
        if (power != null) p.powerUp.apply(power);
        for (int i = 0; i < 30; i++) w.step(Tuning.FIXED_DT);
        float start = p.body.position.y, peak = start;
        boolean second = false;
        for (int i = 0; i < 300; i++) {
            p.controls.clear();
            if (i == 0) p.controls.jump = true;
            if (!second && i > 5 && p.body.velocity.y <= 0f) {
                p.controls.jump = true;
                second = true;
            }
            w.step(Tuning.FIXED_DT);
            peak = Math.max(peak, p.body.position.y);
        }
        return peak - start;
    }

    @Test
    void outOfBoundsIsRecovered() {
        GameWorld w = world(0);
        Combatant p = w.player();
        p.body.teleport(new Vector3(0f, -30f, 0f));
        w.step(Tuning.FIXED_DT);
        assertTrue(p.body.position.y >= -0.1f);
        assertTrue(p.alive);
    }

    @Test
    void largeMovesDoNotTunnelThroughWalls() {
        GameWorld w = world(0);
        CharacterMotor motor = new CharacterMotor(w.physics);
        Body b = new Body();
        b.teleport(new Vector3(17f, 0.02f, -4f));
        // 50 m/s toward the lane wall, a quarter second per step: far beyond normal speeds.
        for (int i = 0; i < 20; i++) {
            b.velocity.set(50f, 0f, 0f);
            motor.step(b, 1f, 0f, false, Modifiers.NONE.speed(7f).acceleration(100f), 0.25f);
        }
        assertTrue(b.position.x < 21f - Tuning.RADIUS + 0.05f, "stayed in front of the wall: " + b.position);
    }

    @Test
    void fullBotMatchCompletesWithScoringAndPowerUps() {
        world = new GameWorld(LEVEL, 7, 4, true);
        int powerUps = 0, kills = 0;
        java.util.Set<Integer> killers = new java.util.HashSet<>();
        int steps = 0;
        while (world.match.isRunning() && steps < 360 * 120) {
            world.step(Tuning.FIXED_DT);
            steps++;
            for (GameEvent e : world.events) {
                if (e.type == GameEvent.Type.PICKUP && e.powerUp != null) powerUps++;
                if (e.type == GameEvent.Type.KILL) {
                    kills++;
                    killers.add(e.actor);
                }
            }
            world.events.clear();
        }
        assertFalse(world.match.isRunning(), "match ends by kill limit or timer");
        Match.Results r = world.match.results();
        int totalKills = world.combatants.stream().mapToInt(c -> c.kills).sum();
        int totalDeaths = world.combatants.stream().mapToInt(c -> c.deaths).sum();
        assertEquals(kills, totalKills, "every kill event is scored exactly once");
        assertEquals(kills, totalDeaths);
        assertTrue(killers.size() >= 3, "several bots fight: " + killers);
        assertTrue(powerUps >= 3, "bots collect power-ups: " + powerUps);
        assertFalse(r.winners.isEmpty());
        if (r.reason == Match.EndReason.KILL_LIMIT) assertEquals(Tuning.KILL_LIMIT, r.standings.get(0).combatant.kills);
    }

    @Test
    void restartingDoesNotLeakPhysicsWorlds() {
        int before = Physics.liveCount();
        for (int i = 0; i < 3; i++) {
            GameWorld w = new GameWorld(LEVEL, i, 4, false);
            w.step(Tuning.FIXED_DT);
            w.dispose();
        }
        assertEquals(before, Physics.liveCount());
    }

    private static Pickup find(GameWorld w, PowerUpType type) {
        return w.pickups.stream().filter(p -> p.powerUp == type).findFirst().orElseThrow();
    }
}
