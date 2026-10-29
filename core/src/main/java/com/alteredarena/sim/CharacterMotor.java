package com.alteredarena.sim;

import com.alteredarena.rules.Modifiers;
import com.alteredarena.rules.Tuning;
import com.badlogic.gdx.math.Vector3;

/**
 * Kinematic capsule movement: acceleration-based walking, jumping, gravity, collide-and-slide against
 * the static arena and ground snapping on ramps. All collision goes through swept capsule tests, so a
 * single fixed step can never tunnel through geometry.
 */
public final class CharacterMotor {
    public static final float SKIN = 0.02f;
    private static final float GROUND_SNAP = 0.35f;
    private static final float HALF_HEIGHT = Tuning.HEIGHT * 0.5f;

    /** Results of the last step, for audio and effects. */
    public boolean jumped, airJumped, landed;
    public float landingSpeed;
    private float lastFallSpeed;

    private final Physics physics;
    private final Physics.SweepHit hit = new Physics.SweepHit();
    private final Vector3 center = new Vector3();
    private final Vector3 delta = new Vector3();
    private final Vector3 target = new Vector3();
    private final Vector3 dir = new Vector3();

    public CharacterMotor(Physics physics) {
        this.physics = physics;
    }

    public static float jumpVelocity(Modifiers modifiers) {
        return (float) Math.sqrt(2f * Tuning.GRAVITY * Tuning.JUMP_HEIGHT * modifiers.jumpHeight);
    }

    /**
     * @param wishX world-space desired direction X (length at most 1)
     * @param wishZ world-space desired direction Z
     */
    public void step(Body body, float wishX, float wishZ, boolean jump, Modifiers modifiers, float dt) {
        jumped = airJumped = landed = false;
        body.previousPosition.set(body.position);
        Vector3 v = body.velocity;
        boolean wasGrounded = body.grounded;

        float wishLen = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (wishLen > 1f) {
            wishX /= wishLen;
            wishZ /= wishLen;
            wishLen = 1f;
        }
        float maxSpeed = Tuning.MOVE_SPEED * modifiers.speed;
        float targetX = wishX * maxSpeed, targetZ = wishZ * maxSpeed;
        float accel;
        if (body.grounded) accel = (wishLen > 0.01f ? Tuning.GROUND_ACCEL : Tuning.GROUND_DECEL) * modifiers.acceleration;
        else if (wishLen > 0.01f) accel = Tuning.AIR_ACCEL * modifiers.acceleration;
        else accel = 0f; // keep momentum in the air when not steering
        float dvx = targetX - v.x, dvz = targetZ - v.z;
        float dvLen = (float) Math.sqrt(dvx * dvx + dvz * dvz);
        float maxDv = accel * dt;
        if (dvLen > maxDv && dvLen > 0f) {
            dvx *= maxDv / dvLen;
            dvz *= maxDv / dvLen;
        }
        // In the air, steering may redirect but never push past normal running speed.
        float newX = v.x + dvx, newZ = v.z + dvz;
        if (!body.grounded) {
            float oldSpeed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
            float newSpeed = (float) Math.sqrt(newX * newX + newZ * newZ);
            float cap = Math.max(oldSpeed, maxSpeed);
            if (newSpeed > cap) {
                newX *= cap / newSpeed;
                newZ *= cap / newSpeed;
            }
        }
        v.x = newX;
        v.z = newZ;

        if (jump) {
            if (body.grounded || body.coyoteTimer > 0f) {
                v.y = jumpVelocity(modifiers);
                body.grounded = false;
                body.coyoteTimer = 0f;
                jumped = true;
            } else if (body.airJumpsUsed < modifiers.extraJumps) {
                v.y = jumpVelocity(modifiers);
                body.airJumpsUsed++;
                jumped = airJumped = true;
            }
        }

        if (body.grounded) {
            v.y = 0f;
        } else {
            v.y = Math.max(v.y - Tuning.GRAVITY * dt, -Tuning.MAX_FALL_SPEED);
        }

        center.set(body.position).add(0f, HALF_HEIGHT, 0f);
        delta.set(v).scl(dt);
        slide(body);

        // Ground probe: snap down onto walkable ground while descending or walking.
        boolean grounded = false;
        if (v.y <= 0f) {
            float probe = wasGrounded ? GROUND_SNAP : SKIN * 3f;
            target.set(center).sub(0f, probe, 0f);
            if (physics.sweep(center, target, hit) && hit.normal.y >= Tuning.WALKABLE_NORMAL_Y) {
                float travel = Math.max(0f, hit.fraction * probe - SKIN);
                center.y -= travel;
                grounded = true;
                v.y = 0f;
            }
        }

        physics.depenetrate(center);
        body.position.set(center).sub(0f, HALF_HEIGHT, 0f);

        if (grounded) {
            if (!wasGrounded && body.airTime > 0.05f) {
                landed = true;
                landingSpeed = lastFallSpeed;
            }
            body.airJumpsUsed = 0;
            body.coyoteTimer = 0f;
            body.airTime = 0f;
        } else {
            if (wasGrounded && !jumped) body.coyoteTimer = Tuning.COYOTE_TIME;
            else body.coyoteTimer = Math.max(0f, body.coyoteTimer - dt);
            body.airTime += dt;
        }
        lastFallSpeed = -Math.min(0f, v.y);
        body.grounded = grounded;
    }

    /** Collide-and-slide along up to four surfaces. */
    private void slide(Body body) {
        Vector3 v = body.velocity;
        for (int i = 0; i < 4; i++) {
            float len = delta.len();
            if (len < 1e-6f) break;
            target.set(center).add(delta);
            if (!physics.sweep(center, target, hit)) {
                center.set(target);
                break;
            }
            dir.set(delta).scl(1f / len);
            float travel = Math.max(0f, hit.fraction * len - SKIN);
            center.mulAdd(dir, travel);
            delta.set(dir).scl(len - travel);
            Vector3 n = hit.normal;
            float into = delta.dot(n);
            if (into < 0f) delta.mulAdd(n, -into);
            boolean walkable = n.y >= Tuning.WALKABLE_NORMAL_Y;
            if (walkable && v.y <= 0f) {
                v.y = 0f; // landed on a floor or ramp; horizontal speed is kept
                if (delta.y < 0f) delta.y = 0f;
            } else {
                float vInto = v.dot(n);
                if (vInto < 0f) v.mulAdd(n, -vInto);
            }
            if (n.y < -0.3f && v.y > 0f) v.y = 0f; // head hit a ceiling
        }
    }
}
