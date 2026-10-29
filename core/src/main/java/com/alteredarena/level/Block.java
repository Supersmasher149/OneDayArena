package com.alteredarena.level;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** A solid oriented box. The same transform and extents feed collision, rendering and navigation. */
public final class Block {
    public enum Style { FLOOR, WALL, PLATFORM, RAMP, COVER, PILLAR, RAIL, CEILING }

    public final Style style;
    public final Vector3 halfExtents;
    public final Matrix4 transform;
    /** Body color, RGBA8888. */
    public final int rgba;
    /** Neon trim color, RGBA8888, or 0 for none. */
    public final int trimRgba;

    public Block(Style style, Matrix4 transform, Vector3 halfExtents, int rgba, int trimRgba) {
        this.style = style;
        this.transform = transform;
        this.halfExtents = halfExtents;
        this.rgba = rgba;
        this.trimRgba = trimRgba;
    }

    public Vector3 center(Vector3 out) {
        return transform.getTranslation(out);
    }
}
