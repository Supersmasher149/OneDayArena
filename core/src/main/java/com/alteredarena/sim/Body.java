package com.alteredarena.sim;

import com.badlogic.gdx.math.Vector3;

/** Kinematic character state. {@link #position} is the bottom of the capsule (feet). */
public final class Body {
    public final Vector3 position = new Vector3();
    public final Vector3 previousPosition = new Vector3();
    public final Vector3 velocity = new Vector3();
    public boolean grounded;
    public float coyoteTimer;
    public int airJumpsUsed;
    /** Seconds airborne, used for landing feedback. */
    public float airTime;

    public void teleport(Vector3 feet) {
        position.set(feet);
        previousPosition.set(feet);
        velocity.setZero();
        grounded = false;
        coyoteTimer = 0f;
        airJumpsUsed = 0;
        airTime = 0f;
    }

    public float horizontalSpeed() {
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }
}
