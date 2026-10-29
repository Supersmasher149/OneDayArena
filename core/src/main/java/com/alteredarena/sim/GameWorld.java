package com.alteredarena.sim;

import com.alteredarena.ai.BotBrain;
import com.alteredarena.level.LevelData;
import com.alteredarena.level.NavGraph;
import com.alteredarena.rules.Modifiers;
import com.alteredarena.rules.PowerUpState;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponState;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The whole match simulation, independent of rendering, audio and input. Advance it with {@link #step(float)}
 * at {@link Tuning#FIXED_DT}; read {@link #events} afterwards.
 */
public final class GameWorld implements Disposable {
    public static final String[] BOT_NAMES = {"Glitch", "Nyx", "Rook", "Sable"};
    public static final int[] COLORS = {0x22F0FFFF, 0xFF3B5CFF, 0xFFC23BFF, 0x5B7BFFFF, 0xF2F2F2FF};
    private static final int MAX_EVENTS = 4096;

    public final LevelData level;
    public final Physics physics;
    public final NavGraph nav;
    public final List<Combatant> combatants = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final Match match;
    public final List<GameEvent> events = new ArrayList<>();
    public final Random random;

    private final CharacterMotor motor;
    private final BotBrain[] brains;
    private final Physics.RayHit rayHit = new Physics.RayHit();
    private final List<ShotResolver.Hit> shotHits = ShotResolver.newList();
    private final Vector3 eye = new Vector3(), aim = new Vector3(), dir = new Vector3(), end = new Vector3(),
            u = new Vector3(), w = new Vector3(), tmp = new Vector3();
    private float time;
    private boolean matchEndAnnounced;

    /**
     * @param autopilot when true the player (id 0) is also driven by a bot brain (used for smoke tests)
     */
    public GameWorld(LevelData level, long seed, int botCount, boolean autopilot) {
        this(level, seed, botCount, autopilot, Tuning.KILL_LIMIT, Tuning.MATCH_DURATION);
    }

    public GameWorld(LevelData level, long seed, int botCount, boolean autopilot, int killLimit, float duration) {
        this.level = level;
        this.random = new Random(seed);
        this.physics = new Physics(level);
        this.nav = NavBuilder.build(level, physics);
        this.motor = new CharacterMotor(physics);

        combatants.add(new Combatant(0, "You", false, COLORS[0]));
        for (int i = 0; i < botCount; i++) {
            combatants.add(new Combatant(i + 1, BOT_NAMES[i % BOT_NAMES.length], true, COLORS[(i + 1) % COLORS.length]));
        }
        brains = new BotBrain[combatants.size()];
        for (Combatant c : combatants) {
            if (c.bot || autopilot) brains[c.id] = new BotBrain(new Random(seed * 31 + c.id), c.id * 0.13f);
        }
        for (int i = 0; i < level.pickups.size(); i++) {
            LevelData.PickupSpot spot = level.pickups.get(i);
            pickups.add(new Pickup(i, spot.kind, spot.powerUp, spot.position, Pickup.defaultRespawn(spot.kind)));
        }
        match = new Match(combatants, killLimit, duration);

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < level.spawns.size(); i++) order.add(i);
        Collections.shuffle(order, random);
        for (int i = 0; i < combatants.size(); i++) {
            LevelData.Spawn s = level.spawns.get(order.get(i % order.size()));
            combatants.get(i).resetForSpawn(s.feet, s.yaw);
        }
    }

    public Combatant player() { return combatants.get(0); }
    public float time() { return time; }
    public BotBrain brain(int id) { return brains[id]; }

    public void step(float dt) {
        if (!match.isRunning()) return;
        time += dt;
        match.update(dt);

        for (Pickup p : pickups) {
            if (p.update(dt)) {
                GameEvent e = event(GameEvent.Type.PICKUP_RESPAWN);
                e.position.set(p.position);
                e.pickup = p;
            }
        }

        for (Combatant c : combatants) {
            c.timeSinceDamaged += dt;
            c.timeSinceFired += dt;
            if (!c.alive) {
                c.respawnTimer -= dt;
                if (c.respawnTimer <= 0f) respawn(c);
                continue;
            }
            if (brains[c.id] != null) brains[c.id].think(this, c, dt);
            updateCombatant(c, dt);
        }
        separateCharacters();
        collectPickups();

        if (!match.isRunning() && !matchEndAnnounced) {
            matchEndAnnounced = true;
            event(GameEvent.Type.MATCH_END);
        }
    }

    private void updateCombatant(Combatant c, float dt) {
        Controls in = c.controls;
        c.protection = Math.max(0f, c.protection - dt);

        PowerUpType expired = c.powerUp.update(dt);
        if (expired != null) {
            GameEvent e = event(GameEvent.Type.POWERUP_EXPIRED);
            e.actor = c.id;
            e.powerUp = expired;
        }

        for (int i = 0; i < c.weapons.length; i++) {
            if (c.weapons[i].update(dt) && i == c.weaponIndex) {
                GameEvent e = event(GameEvent.Type.RELOAD_DONE);
                e.actor = c.id;
                e.weapon = c.weapons[i].type;
            }
        }

        if (in.selectWeapon >= 0 && in.selectWeapon < c.weapons.length && in.selectWeapon != c.weaponIndex) {
            c.weapon().cancelReload();
            c.weaponIndex = in.selectWeapon;
            c.switchTimer = Tuning.WEAPON_SWITCH_TIME;
            GameEvent e = event(GameEvent.Type.WEAPON_SWITCH);
            e.actor = c.id;
            e.weapon = c.weapon().type;
        }
        c.switchTimer = Math.max(0f, c.switchTimer - dt);

        WeaponState weapon = c.weapon();
        boolean wantsReload = in.reload || (weapon.magazine() == 0 && weapon.reserve() > 0 && weapon.cooldown() <= 0f);
        if (wantsReload && c.switchTimer <= 0f && weapon.startReload()) {
            GameEvent e = event(GameEvent.Type.RELOAD_START);
            e.actor = c.id;
            e.weapon = weapon.type;
        }

        // Movement relative to view yaw
        float sin = MathUtils.sinDeg(c.yaw), cos = MathUtils.cosDeg(c.yaw);
        float wishX = sin * in.forward + cos * in.strafe;
        float wishZ = -cos * in.forward + sin * in.strafe;
        Modifiers mods = c.modifiers();
        motor.step(c.body, wishX, wishZ, in.jump, mods, dt);
        if (motor.jumped) {
            GameEvent e = event(GameEvent.Type.JUMP);
            e.actor = c.id;
            e.flag = motor.airJumped;
            e.position.set(c.body.position);
        }
        if (motor.landed) {
            GameEvent e = event(GameEvent.Type.LAND);
            e.actor = c.id;
            e.value = motor.landingSpeed;
            e.position.set(c.body.position);
        }
        in.jump = false;

        if (in.fire && c.switchTimer <= 0f) fire(c);

        float bound = level.boundsHalfSize + 6f;
        Vector3 p = c.body.position;
        if (p.y < Tuning.OUT_OF_BOUNDS_Y || Math.abs(p.x) > bound || Math.abs(p.z) > bound || Float.isNaN(p.x)) {
            LevelData.Spawn s = chooseSpawn(c);
            c.body.teleport(s.feet);
            c.yaw = s.yaw;
            GameEvent e = event(GameEvent.Type.OUT_OF_BOUNDS);
            e.actor = c.id;
            e.position.set(s.feet);
        }
    }

    /** Fires the current weapon if it is ready; handles spread, walls, piercing and damage. */
    void fire(Combatant c) {
        WeaponState weapon = c.weapon();
        Modifiers mods = c.modifiers();
        if (!weapon.tryFire(mods.fireRate)) {
            if (weapon.magazine() == 0 && weapon.reserve() == 0 && weapon.cooldown() <= 0f && c.timeSinceFired > 0.4f) {
                c.timeSinceFired = 0f;
                GameEvent e = event(GameEvent.Type.DRY_FIRE);
                e.actor = c.id;
                e.weapon = weapon.type;
            }
            return;
        }
        c.protection = 0f;
        c.timeSinceFired = 0f;
        c.eye(eye);
        c.aimDirection(aim);
        boolean moving = !c.body.grounded || c.body.horizontalSpeed() > 2f;
        float spread = (moving ? weapon.type.movingSpreadDegrees : weapon.type.spreadDegrees) * mods.spread;
        int maxVictims = 1 + mods.extraPierce;

        GameEvent shot = event(GameEvent.Type.SHOT);
        shot.actor = c.id;
        shot.weapon = weapon.type;
        shot.position.set(eye);
        shot.position2.set(aim);

        Map<Combatant, Float> damage = new LinkedHashMap<>();
        for (int pellet = 0; pellet < weapon.type.pellets; pellet++) {
            coneDirection(aim, spread, dir);
            end.set(dir).scl(weapon.type.range).add(eye);
            boolean wall = physics.rayCast(eye, end, rayHit);
            float wallDistance = wall ? rayHit.distance : weapon.type.range;
            ShotResolver.resolve(eye, dir, wallDistance, c, combatants, maxVictims, shotHits);
            for (ShotResolver.Hit h : shotHits) {
                damage.merge(h.victim, weapon.type.damageAt(h.distance), Float::sum);
            }
            boolean stoppedByBody = shotHits.size() >= maxVictims;
            float endDistance = stoppedByBody ? shotHits.get(shotHits.size() - 1).distance : wallDistance;
            GameEvent tracer = event(GameEvent.Type.TRACER);
            tracer.actor = c.id;
            tracer.weapon = weapon.type;
            tracer.position.set(eye);
            tracer.position2.set(dir).scl(endDistance).add(eye);
            if (wall && !stoppedByBody) {
                GameEvent impact = event(GameEvent.Type.IMPACT);
                impact.actor = c.id;
                impact.position.set(rayHit.point);
                impact.position2.set(rayHit.normal);
                impact.weapon = weapon.type;
            }
        }
        for (Map.Entry<Combatant, Float> entry : damage.entrySet()) {
            Combatant victim = entry.getKey();
            CombatRules.DamageResult result = CombatRules.applyDamage(c, victim, entry.getValue(), match);
            if (result.applied <= 0f) continue;
            GameEvent hit = event(GameEvent.Type.HIT);
            hit.actor = c.id;
            hit.target = victim.id;
            hit.value = result.applied;
            hit.flag = result.killed;
            hit.weapon = weapon.type;
            victim.chest(hit.position);
            c.eye(hit.position2);
            if (result.healed > 0f) {
                GameEvent heal = event(GameEvent.Type.HEAL);
                heal.actor = c.id;
                heal.value = result.healed;
            }
            if (result.killed) {
                GameEvent kill = event(GameEvent.Type.KILL);
                kill.actor = c.id;
                kill.target = victim.id;
                kill.weapon = weapon.type;
                victim.chest(kill.position);
            }
        }
        // Recoil climbs the aim slightly; the player pulls it back down.
        c.pitch = Math.min(85f, c.pitch + weapon.type.recoilDegrees * 0.35f);
    }

    /** Random direction inside a cone of half-angle {@code degrees}, uniform over the disc. */
    private void coneDirection(Vector3 axis, float degrees, Vector3 out) {
        if (degrees <= 0f) {
            out.set(axis);
            return;
        }
        u.set(Math.abs(axis.y) < 0.99f ? Vector3.Y : Vector3.X).crs(axis).nor();
        w.set(axis).crs(u).nor();
        float angle = degrees * MathUtils.degreesToRadians * (float) Math.sqrt(random.nextFloat());
        float phi = random.nextFloat() * MathUtils.PI2;
        float s = (float) Math.sin(angle);
        out.set(axis).scl((float) Math.cos(angle))
                .mulAdd(u, s * (float) Math.cos(phi))
                .mulAdd(w, s * (float) Math.sin(phi)).nor();
    }

    private void respawn(Combatant c) {
        LevelData.Spawn s = chooseSpawn(c);
        c.resetForSpawn(s.feet, s.yaw);
        GameEvent e = event(GameEvent.Type.RESPAWN);
        e.actor = c.id;
        e.position.set(s.feet);
        if (brains[c.id] != null) brains[c.id].reset();
    }

    /** Picks the spawn farthest from living opponents, avoiding ones they can see. */
    LevelData.Spawn chooseSpawn(Combatant who) {
        LevelData.Spawn best = level.spawns.get(0);
        float bestScore = -Float.MAX_VALUE;
        for (LevelData.Spawn s : level.spawns) {
            float nearest = 80f;
            boolean seen = false;
            tmp.set(s.feet).add(0f, Tuning.EYE_HEIGHT, 0f);
            for (Combatant other : combatants) {
                if (other == who || !other.alive) continue;
                nearest = Math.min(nearest, other.body.position.dst(s.feet));
                if (!seen && other.body.position.dst2(s.feet) < 30f * 30f && physics.lineOfSight(other.eye(eye), tmp)) seen = true;
            }
            float score = nearest - (seen ? 15f : 0f) + random.nextFloat() * 4f;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private void separateCharacters() {
        float minDist = Tuning.RADIUS * 2f;
        for (int i = 0; i < combatants.size(); i++) {
            Combatant a = combatants.get(i);
            if (!a.alive) continue;
            for (int j = i + 1; j < combatants.size(); j++) {
                Combatant b = combatants.get(j);
                if (!b.alive) continue;
                Vector3 pa = a.body.position, pb = b.body.position;
                if (Math.abs(pa.y - pb.y) > Tuning.HEIGHT) continue;
                float dx = pb.x - pa.x, dz = pb.z - pa.z;
                float d2 = dx * dx + dz * dz;
                if (d2 >= minDist * minDist) continue;
                float d = (float) Math.sqrt(d2);
                if (d < 1e-4f) {
                    dx = 1f;
                    dz = 0f;
                    d = 1f;
                    d2 = 0f;
                }
                float push = (minDist - (float) Math.sqrt(d2)) * 0.5f;
                dx /= d;
                dz /= d;
                pushIfClear(a, -dx * push, -dz * push);
                pushIfClear(b, dx * push, dz * push);
            }
        }
    }

    private void pushIfClear(Combatant c, float dx, float dz) {
        tmp.set(c.body.position).add(dx, 0f, dz);
        if (physics.capsuleFits(tmp.add(0f, 0.03f, 0f))) c.body.position.add(dx, 0f, dz);
    }

    private void collectPickups() {
        for (Pickup p : pickups) {
            if (!p.active) continue;
            for (Combatant c : combatants) {
                if (!c.alive) continue;
                Vector3 pos = c.body.position;
                float dx = pos.x - p.position.x, dz = pos.z - p.position.z;
                if (dx * dx + dz * dz > Tuning.PICKUP_RADIUS * Tuning.PICKUP_RADIUS) continue;
                if (pos.y < p.position.y - 0.6f || pos.y > p.position.y + 1.4f) continue;
                if (tryCollect(c, p)) break;
            }
        }
    }

    /** @return true if the pickup was consumed */
    boolean tryCollect(Combatant c, Pickup p) {
        GameEvent e;
        switch (p.kind) {
            case HEALTH: {
                float healed = c.health.heal(Tuning.HEALTH_PICKUP_AMOUNT);
                if (healed <= 0f) return false;
                e = event(GameEvent.Type.PICKUP);
                e.value = healed;
                break;
            }
            case AMMO: {
                int added = 0;
                for (WeaponState ws : c.weapons) added += ws.addReserve(ws.type.ammoPickupAmount);
                if (added <= 0) return false;
                e = event(GameEvent.Type.PICKUP);
                e.value = added;
                break;
            }
            default: {
                PowerUpState.ApplyResult result = c.powerUp.apply(p.powerUp);
                e = event(GameEvent.Type.PICKUP);
                e.powerUp = p.powerUp;
                e.flag = result == PowerUpState.ApplyResult.REFRESHED;
                break;
            }
        }
        e.actor = c.id;
        e.pickup = p;
        e.position.set(p.position);
        p.consume();
        return true;
    }

    public Combatant combatant(int id) {
        return id >= 0 && id < combatants.size() ? combatants.get(id) : null;
    }

    GameEvent event(GameEvent.Type type) {
        GameEvent e = new GameEvent(type);
        if (events.size() < MAX_EVENTS) events.add(e);
        return e;
    }

    @Override
    public void dispose() {
        physics.dispose();
    }
}
