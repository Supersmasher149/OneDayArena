package com.alteredarena.ai;

import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.level.NavGraph;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponState;
import com.alteredarena.rules.WeaponType;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.Controls;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.sim.Physics;
import com.alteredarena.sim.Pickup;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntArray;

import java.util.Random;

/**
 * Deathmatch bot: perceives opponents by distance, view cone and line of sight; reacts after a delay;
 * aims with drifting error and a turn-rate limit; strafes, pursues and disengages; and routes over the
 * nav graph to pickups, last-known enemy positions or random points. It drives a {@link Controls}
 * exactly like the player does, so it obeys every movement, ammo and power-up rule.
 */
public final class BotBrain {
    public enum Goal { NONE, HEALTH, AMMO, POWER_UP, HUNT, ROAM, CHASE }

    private static final float PERCEIVE_INTERVAL = 0.1f;
    private static final float VIEW_RANGE = 55f;
    private static final float VIEW_HALF_ANGLE = 80f;

    private final Random rng;
    private final float aimSkill;
    private final float reactionBase;
    private final float turnSpeed;

    // Perception
    private float perceiveTimer;
    private Combatant target;
    private boolean targetVisible;
    private float reactionTimer;
    private float notVisibleTime = 99f;
    private final Vector3 lastSeen = new Vector3();
    private float lastSeenAge = 99f;

    // Aim error
    private float errYaw, errPitch, errYawTarget, errPitchTarget, errTimer;

    // Navigation
    private final IntArray path = new IntArray();
    private int pathIndex;
    private final Vector3 goal = new Vector3();
    private Goal goalKind = Goal.NONE;
    private Pickup goalPickup;
    private float goalTimer;
    private float repathTimer;

    // Stuck recovery
    private final Vector3 stuckAnchor = new Vector3();
    private float stuckTimer;
    private int stuckCount;
    private float unstickTimer;
    private float unstickStrafe;

    // Combat movement
    private float strafeSign = 1f;
    private float strafeTimer;
    private float jumpTimer = 2f;
    private boolean doubleJumpPending;
    private float burstTimer;
    private boolean bursting = true;

    private final Vector3 eye = new Vector3(), aimPoint = new Vector3(), move = new Vector3(),
            tmp = new Vector3(), tmp2 = new Vector3(), lastMove = new Vector3();
    private final IntArray candidates = new IntArray();
    private final Physics.RayHit rayHit = new Physics.RayHit();

    /** @param skillOffset small per-bot variation so bots do not behave identically */
    public BotBrain(Random rng, float skillOffset) {
        this.rng = rng;
        this.aimSkill = MathUtils.clamp(0.55f + skillOffset * 0.5f, 0f, 1f);
        this.reactionBase = 0.28f - skillOffset * 0.1f;
        this.turnSpeed = 300f + skillOffset * 200f;
    }

    public Goal goal() { return goalKind; }
    public Combatant target() { return targetVisible ? target : null; }

    public void reset() {
        target = null;
        targetVisible = false;
        notVisibleTime = 99f;
        lastSeenAge = 99f;
        path.clear();
        goalKind = Goal.NONE;
        goalPickup = null;
        goalTimer = 0f;
        stuckCount = 0;
        stuckTimer = 0f;
        unstickTimer = 0f;
    }

    public void think(GameWorld world, Combatant self, float dt) {
        Controls c = self.controls;
        c.clear();
        self.eye(eye);

        perceive(world, self, dt);
        float targetDist = targetVisible ? target.body.position.dst(self.body.position) : Float.MAX_VALUE;
        chooseWeapon(self, targetDist, c);

        boolean engaged = targetVisible && reactionTimerDone();
        boolean lowHealth = self.health.current() < 35f;
        Pickup healthPickup = lowHealth ? nearestActive(world, self, PickupKind.HEALTH) : null;

        move.setZero();
        if (targetVisible && (healthPickup == null || targetDist < 5f)) {
            combatMove(world, self, dt, targetDist);
        } else {
            if (targetVisible && healthPickup != null) setGoal(world, self, Goal.HEALTH, healthPickup, healthPickup.position);
            updateGoal(world, self, dt);
            followPath(world, self, dt);
        }
        handleStuck(world, self, dt, c);

        // Aim and fire
        if (targetVisible) {
            target.chest(aimPoint);
            aimAt(self, aimPoint, dt, true);
            burstTimer -= dt;
            if (burstTimer <= 0f) {
                bursting = !bursting;
                burstTimer = bursting ? 0.45f + rng.nextFloat() * 0.6f : 0.3f + rng.nextFloat() * 0.45f;
            }
            boolean rifle = self.weapon().type == WeaponType.PULSE_RIFLE;
            if (engaged && (bursting || !rifle) && aligned(self, targetDist)) c.fire = true;
        } else {
            float lookYaw = self.yaw;
            if (move.len2() > 0.01f) lookYaw = MathUtils.atan2(move.x, -move.z) * MathUtils.radiansToDegrees;
            else if (lastSeenAge < 3f) lookYaw = yawTo(self.body.position, lastSeen);
            self.yaw = turnToward(self.yaw, lookYaw, turnSpeed * 0.7f * dt, dt);
            self.pitch += (0f - self.pitch) * Math.min(1f, 6f * dt);
        }

        // Reload while nothing is in sight
        WeaponState weapon = self.weapon();
        if (!targetVisible && weapon.magazine() < weapon.type.magazineSize * 0.5f + 0.5f && weapon.reserve() > 0) c.reload = true;
        if (!targetVisible && weapon.type == WeaponType.SCATTERGUN && weapon.magazine() < weapon.type.magazineSize && weapon.reserve() > 0) c.reload = true;

        // Translate the world-space move into forward/strafe relative to the current view.
        float sin = MathUtils.sinDeg(self.yaw), cos = MathUtils.cosDeg(self.yaw);
        c.forward = MathUtils.clamp(move.x * sin - move.z * cos, -1f, 1f);
        c.strafe = MathUtils.clamp(move.x * cos + move.z * sin, -1f, 1f);
        lastMove.set(move);
    }

    private boolean reactionTimerDone() { return reactionTimer <= 0f; }

    // ---------------------------------------------------------------- perception

    private void perceive(GameWorld world, Combatant self, float dt) {
        reactionTimer -= dt;
        lastSeenAge += dt;
        perceiveTimer -= dt;
        if (targetVisible) {
            target.chest(lastSeen);
            lastSeenAge = 0f;
        }
        if (perceiveTimer > 0f) {
            if (target != null && !target.alive) {
                target = null;
                targetVisible = false;
            }
            return;
        }
        perceiveTimer = PERCEIVE_INTERVAL;

        Combatant best = null;
        float bestScore = Float.MAX_VALUE;
        for (Combatant other : world.combatants) {
            if (other == self || !other.alive) continue;
            float dist = other.body.position.dst(self.body.position);
            if (dist > VIEW_RANGE) continue;
            boolean attackedMe = self.lastAttackerId == other.id && self.timeSinceDamaged < 1.5f;
            if (other.isProtected() && !attackedMe) continue;
            if (dist > 7f && !attackedMe) {
                float angle = Math.abs(angleDiff(self.yaw, yawTo(self.body.position, other.body.position)));
                if (angle > VIEW_HALF_ANGLE && other != target) continue;
            }
            if (!canSee(world, other)) continue;
            float score = dist;
            if (other == target) score *= 0.6f;
            if (attackedMe) score *= 0.7f;
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        if (best != null) {
            if (best != target || notVisibleTime > 0.8f) {
                reactionTimer = reactionBase + rng.nextFloat() * 0.2f;
                newAimError(best.body.position.dst(self.body.position), true);
            }
            target = best;
            targetVisible = true;
            notVisibleTime = 0f;
        } else {
            if (targetVisible) notVisibleTime = 0f;
            targetVisible = false;
            notVisibleTime += PERCEIVE_INTERVAL;
            if (notVisibleTime > 3f) target = null;
        }
    }

    private boolean canSee(GameWorld world, Combatant other) {
        other.chest(tmp);
        if (world.physics.lineOfSight(eye, tmp)) return true;
        other.eye(tmp);
        return world.physics.lineOfSight(eye, tmp);
    }

    // ---------------------------------------------------------------- weapons and aim

    private void chooseWeapon(Combatant self, float dist, Controls c) {
        WeaponState rifle = self.weapons[0], scatter = self.weapons[1];
        int want = self.weaponIndex;
        if (dist < 9f && !scatter.isEmpty()) want = 1;
        else if (dist > 13f && !rifle.isEmpty()) want = 0;
        if (self.weapons[want].isEmpty()) want = 1 - want;
        if (want != self.weaponIndex && !self.weapons[want].isEmpty()) c.selectWeapon = want;
    }

    private void newAimError(float dist, boolean fresh) {
        float magnitude = (1.4f + dist * 0.07f) * (1.8f - aimSkill);
        if (fresh) magnitude *= 2.2f; // first glance is sloppy, then the bot settles
        errYawTarget = (float) rng.nextGaussian() * magnitude;
        errPitchTarget = (float) rng.nextGaussian() * magnitude * 0.6f;
        if (fresh) {
            errYaw = errYawTarget;
            errPitch = errPitchTarget;
        }
        errTimer = 0.35f + rng.nextFloat() * 0.4f;
    }

    private void aimAt(Combatant self, Vector3 point, float dt, boolean useError) {
        float dist = point.dst(eye);
        errTimer -= dt;
        if (errTimer <= 0f) newAimError(dist, false);
        float blend = Math.min(1f, 3f * dt);
        errYaw += (errYawTarget - errYaw) * blend;
        errPitch += (errPitchTarget - errPitch) * blend;
        float desiredYaw = yawTo(eye, point) + (useError ? errYaw : 0f);
        float desiredPitch = pitchTo(eye, point) + (useError ? errPitch : 0f);
        self.yaw = turnToward(self.yaw, desiredYaw, turnSpeed * dt, dt);
        float pitchStep = turnSpeed * 0.6f * dt;
        self.pitch += MathUtils.clamp(desiredPitch - self.pitch, -pitchStep, pitchStep);
        self.pitch = MathUtils.clamp(self.pitch, -80f, 80f);
    }

    private boolean aligned(Combatant self, float dist) {
        WeaponType type = self.weapon().type;
        if (type == WeaponType.SCATTERGUN && dist > 20f) return false;
        target.chest(tmp);
        float yawErr = Math.abs(angleDiff(self.yaw, yawTo(eye, tmp)));
        float pitchErr = Math.abs(self.pitch - pitchTo(eye, tmp));
        float tolerance = type == WeaponType.SCATTERGUN ? 9f
                : MathUtils.atan2(0.7f, Math.max(1f, dist)) * MathUtils.radiansToDegrees + 3f;
        return yawErr < tolerance && pitchErr < tolerance + 3f;
    }

    // ---------------------------------------------------------------- movement

    private void combatMove(GameWorld world, Combatant self, float dt, float dist) {
        Vector3 pos = self.body.position;
        Vector3 tpos = target.body.position;
        float preferred = self.weapon().type == WeaponType.SCATTERGUN ? 4f : 13f;
        boolean differentLevel = Math.abs(tpos.y - pos.y) > 1.5f;

        if (differentLevel && dist > preferred) {
            // Route to the target's level rather than strafing below it.
            if (goalKind != Goal.CHASE || repathTimer <= 0f) setGoal(world, self, Goal.CHASE, null, tpos);
            followPath(world, self, dt);
            return;
        }

        strafeTimer -= dt;
        if (strafeTimer <= 0f) {
            strafeTimer = 0.5f + rng.nextFloat() * 1.0f;
            if (rng.nextFloat() < 0.6f) strafeSign = -strafeSign;
        }
        tmp.set(tpos).sub(pos);
        tmp.y = 0f;
        if (tmp.isZero(1e-4f)) tmp.set(1f, 0f, 0f);
        tmp.nor();
        float radial = MathUtils.clamp((dist - preferred) / 4f, -1f, 1f);
        move.set(tmp).scl(radial).add(-tmp.z * strafeSign * 0.9f, 0f, tmp.x * strafeSign * 0.9f);
        if (move.len2() > 1f) move.nor();
        if (!safeToMove(world, self, move)) {
            strafeSign = -strafeSign;
            move.set(tmp).scl(radial).add(-tmp.z * strafeSign * 0.9f, 0f, tmp.x * strafeSign * 0.9f);
            if (move.len2() > 1f) move.nor();
            if (!safeToMove(world, self, move)) move.setZero();
        }

        jumpTimer -= dt;
        if (jumpTimer <= 0f && self.body.grounded) {
            jumpTimer = 1.5f + rng.nextFloat() * 3f;
            if (rng.nextFloat() < 0.45f) {
                self.controls.jump = true;
                doubleJumpPending = self.modifiers().extraJumps > 0;
            }
        } else if (doubleJumpPending && !self.body.grounded && self.body.velocity.y < 0.5f) {
            self.controls.jump = true;
            doubleJumpPending = false;
        }
    }

    /** Rejects moves into walls or off ledges taller than a short hop. */
    private boolean safeToMove(GameWorld world, Combatant self, Vector3 dir) {
        if (dir.isZero(1e-3f)) return true;
        Vector3 pos = self.body.position;
        tmp2.set(dir).nor();
        float ax = pos.x + tmp2.x * 1.2f, az = pos.z + tmp2.z * 1.2f;
        // wall check at knee height
        aimPoint.set(pos).add(0f, 0.5f, 0f);
        Vector3 ahead = new Vector3(ax, pos.y + 0.5f, az);
        if (!world.physics.lineOfSight(aimPoint, ahead)) return false;
        // ledge check
        float h = world.physics.groundHeight(ahead.set(ax, pos.y + 0.8f, az), 2.2f, null);
        return !Float.isNaN(h);
    }

    private void updateGoal(GameWorld world, Combatant self, float dt) {
        goalTimer -= dt;
        boolean pickupGone = goalPickup != null && !goalPickup.active;
        boolean reached = goalKind != Goal.NONE && pathIndex >= path.size
                && horizontalDist(self.body.position, goal) < 0.7f;
        if (goalTimer > 0f && !pickupGone && !reached && goalKind != Goal.NONE && goalKind != Goal.CHASE) return;

        Pickup p = null;
        Goal kind = Goal.ROAM;
        if (self.health.current() < 60f) {
            p = nearestActive(world, self, PickupKind.HEALTH);
            kind = Goal.HEALTH;
        }
        if (p == null && ammoLow(self)) {
            p = nearestActive(world, self, PickupKind.AMMO);
            kind = Goal.AMMO;
        }
        if (p == null && (!self.powerUp.isActive() || self.powerUp.remaining() < 4f) && rng.nextFloat() < 0.75f) {
            p = nearestPowerUp(world, self);
            kind = Goal.POWER_UP;
        }
        if (p != null) {
            setGoal(world, self, kind, p, p.position);
        } else if (lastSeenAge < 6f && rng.nextFloat() < 0.7f) {
            setGoal(world, self, Goal.HUNT, null, lastSeen);
        } else {
            NavGraph nav = world.nav;
            Vector3 node = nav.nodes.get(rng.nextInt(nav.size()));
            setGoal(world, self, Goal.ROAM, null, node);
        }
    }

    private void setGoal(GameWorld world, Combatant self, Goal kind, Pickup pickup, Vector3 position) {
        goalKind = kind;
        goalPickup = pickup;
        goal.set(position);
        goalTimer = 7f + rng.nextFloat() * 5f;
        repath(world, self);
    }

    private void repath(GameWorld world, Combatant self) {
        repathTimer = goalKind == Goal.CHASE ? 1.2f : 3f;
        path.clear();
        pathIndex = 0;
        int start = startNode(world, self.body.position);
        int end = world.nav.nearest(goal);
        if (start < 0 || end < 0) return;
        world.nav.findPath(start, end, path);
    }

    /** Closest node the bot can see (so it does not path through a wall). */
    private int startNode(GameWorld world, Vector3 pos) {
        NavGraph nav = world.nav;
        nav.nearestCandidates(pos, 6, candidates);
        tmp.set(pos).add(0f, 0.6f, 0f);
        for (int i = 0; i < candidates.size; i++) {
            int index = candidates.get(i);
            tmp2.set(nav.nodes.get(index)).add(0f, 0.6f, 0f);
            if (Math.abs(tmp2.y - tmp.y) < 1.6f && world.physics.lineOfSight(tmp, tmp2)) return index;
        }
        return candidates.size > 0 ? candidates.get(0) : -1;
    }

    private void followPath(GameWorld world, Combatant self, float dt) {
        repathTimer -= dt;
        Vector3 pos = self.body.position;
        if (goalKind == Goal.NONE) return;
        if (repathTimer <= 0f) repath(world, self);
        while (pathIndex < path.size) {
            Vector3 node = world.nav.nodes.get(path.get(pathIndex));
            if (horizontalDist(pos, node) < 0.9f && Math.abs(node.y - pos.y) < 1.6f) pathIndex++;
            else break;
        }
        Vector3 next = pathIndex < path.size ? world.nav.nodes.get(path.get(pathIndex)) : goal;
        if (pathIndex < path.size && (horizontalDist(pos, next) > 8f || Math.abs(next.y - pos.y) > 2.6f)) {
            repath(world, self); // knocked off the route
            next = pathIndex < path.size ? world.nav.nodes.get(path.get(pathIndex)) : goal;
        }
        move.set(next.x - pos.x, 0f, next.z - pos.z);
        float len = move.len();
        if (len < 0.3f && pathIndex >= path.size) {
            move.setZero();
            goalTimer = 0f;
            return;
        }
        move.scl(1f / Math.max(len, 1e-4f));
    }

    private void handleStuck(GameWorld world, Combatant self, float dt, Controls c) {
        if (unstickTimer > 0f) {
            unstickTimer -= dt;
            float sx = -move.z * unstickStrafe, sz = move.x * unstickStrafe;
            move.add(sx, 0f, sz);
            if (move.len2() > 1f) move.nor();
        }
        stuckTimer += dt;
        if (stuckTimer < 0.75f) return;
        stuckTimer = 0f;
        float moved = horizontalDist(self.body.position, stuckAnchor);
        stuckAnchor.set(self.body.position);
        if (lastMove.len2() < 0.09f || targetVisible) {
            stuckCount = 0;
            return;
        }
        if (moved < 0.5f) {
            stuckCount++;
            c.jump = true;
            unstickTimer = 0.6f;
            unstickStrafe = rng.nextBoolean() ? 1f : -1f;
            if (stuckCount >= 3) {
                stuckCount = 0;
                goalTimer = 0f;
                goalKind = Goal.NONE;
                path.clear();
            }
        } else {
            stuckCount = 0;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static boolean ammoLow(Combatant self) {
        return self.weapons[0].ammoLow() || self.weapons[1].isEmpty();
    }

    private Pickup nearestActive(GameWorld world, Combatant self, PickupKind kind) {
        Pickup best = null;
        float bestDist = Float.MAX_VALUE;
        for (Pickup p : world.pickups) {
            if (!p.active || p.kind != kind) continue;
            float d = p.position.dst(self.body.position);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private Pickup nearestPowerUp(GameWorld world, Combatant self) {
        Pickup best = null;
        float bestScore = Float.MAX_VALUE;
        for (Pickup p : world.pickups) {
            if (!p.active || p.kind != PickupKind.POWER_UP) continue;
            if (p.powerUp == self.powerUp.active() && self.powerUp.remaining() > 4f) continue;
            float score = p.position.dst(self.body.position) + rng.nextFloat() * 12f;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private static float horizontalDist(Vector3 a, Vector3 b) {
        float dx = a.x - b.x, dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    static float yawTo(Vector3 from, Vector3 to) {
        return MathUtils.atan2(to.x - from.x, -(to.z - from.z)) * MathUtils.radiansToDegrees;
    }

    static float pitchTo(Vector3 from, Vector3 to) {
        float horizontal = horizontalDist(from, to);
        return MathUtils.atan2(to.y - from.y, horizontal) * MathUtils.radiansToDegrees;
    }

    static float angleDiff(float from, float to) {
        float d = (to - from) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float turnToward(float current, float desired, float maxStep, float dt) {
        float diff = angleDiff(current, desired);
        // Ease in near the target and cap the speed far from it.
        float step = MathUtils.clamp(diff * Math.min(1f, 12f * dt), -maxStep, maxStep);
        return current + step;
    }
}
