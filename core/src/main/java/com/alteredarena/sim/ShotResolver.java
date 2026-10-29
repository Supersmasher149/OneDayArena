package com.alteredarena.sim;

import com.alteredarena.rules.Tuning;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a single hitscan ray: the nearest wall stops it, and up to {@code maxVictims} characters
 * in front of that wall are hit in order of distance.
 */
public final class ShotResolver {
    private ShotResolver() {}

    public static final class Hit {
        public final Combatant victim;
        public final float distance;
        Hit(Combatant victim, float distance) {
            this.victim = victim;
            this.distance = distance;
        }
    }

    /**
     * @param wallDistance distance to the first solid surface along the ray (or the weapon range)
     * @return hits sorted by distance, at most {@code maxVictims}
     */
    public static List<Hit> resolve(Vector3 origin, Vector3 dir, float wallDistance, Combatant shooter,
                                    List<Combatant> combatants, int maxVictims, List<Hit> out) {
        out.clear();
        for (Combatant c : combatants) {
            if (c == shooter || !c.alive) continue;
            float t = rayVerticalCapsule(origin, dir, wallDistance, c.body.position, Tuning.HITBOX_RADIUS, Tuning.HEIGHT);
            if (t >= 0f) out.add(new Hit(c, t));
        }
        out.sort((a, b) -> Float.compare(a.distance, b.distance));
        while (out.size() > maxVictims) out.remove(out.size() - 1);
        return out;
    }

    public static List<Hit> newList() { return new ArrayList<>(); }

    /**
     * Ray against an upright capsule standing on {@code feet}. {@code dir} must be normalized.
     * @return entry distance in [0, maxT], or -1 if missed
     */
    public static float rayVerticalCapsule(Vector3 o, Vector3 dir, float maxT, Vector3 feet, float radius, float height) {
        float yBottom = feet.y + radius, yTop = feet.y + height - radius;
        float best = Float.MAX_VALUE;
        // Cylinder section
        float ox = o.x - feet.x, oz = o.z - feet.z;
        float a = dir.x * dir.x + dir.z * dir.z;
        if (a > 1e-8f) {
            float b = ox * dir.x + oz * dir.z;
            float c = ox * ox + oz * oz - radius * radius;
            float disc = b * b - a * c;
            if (disc >= 0f) {
                float t = (-b - (float) Math.sqrt(disc)) / a;
                if (t < 0f && c <= 0f) t = 0f; // origin inside the cylinder
                float y = o.y + dir.y * t;
                if (t >= 0f && y >= yBottom && y <= yTop) best = t;
            }
        }
        best = Math.min(best, raySphere(o, dir, feet.x, yBottom, feet.z, radius));
        best = Math.min(best, raySphere(o, dir, feet.x, yTop, feet.z, radius));
        return best <= maxT ? best : -1f;
    }

    private static float raySphere(Vector3 o, Vector3 dir, float cx, float cy, float cz, float r) {
        float lx = o.x - cx, ly = o.y - cy, lz = o.z - cz;
        float b = lx * dir.x + ly * dir.y + lz * dir.z;
        float c = lx * lx + ly * ly + lz * lz - r * r;
        if (c <= 0f) return 0f;
        float disc = b * b - c;
        if (disc < 0f) return Float.MAX_VALUE;
        float t = -b - (float) Math.sqrt(disc);
        return t >= 0f ? t : Float.MAX_VALUE;
    }
}
