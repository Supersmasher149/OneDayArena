package com.alteredarena.sim;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShotResolverTest {
    @Test
    void rayHitsUprightCapsule() {
        Vector3 o = new Vector3(0, 1, 0), d = new Vector3(1, 0, 0);
        assertEquals(4.55f, ShotResolver.rayVerticalCapsule(o, d, 100, new Vector3(5, 0, 0), 0.45f, 1.8f), 1e-3);
        assertEquals(-1f, ShotResolver.rayVerticalCapsule(o, d, 100, new Vector3(5, 0, 2), 0.45f, 1.8f));
        assertEquals(-1f, ShotResolver.rayVerticalCapsule(o, d, 3, new Vector3(5, 0, 0), 0.45f, 1.8f), "beyond max distance (wall)");
        assertEquals(-1f, ShotResolver.rayVerticalCapsule(new Vector3(0, 3, 0), d, 100, new Vector3(5, 0, 0), 0.45f, 1.8f), "over the head");
    }

    @Test
    void hitsAreOrderedAndLimitedToPierceCount() {
        List<Combatant> cs = TestFighters.make(4);
        cs.get(0).body.position.set(0, 0, 0);
        cs.get(1).body.position.set(9, 0, 0);
        cs.get(2).body.position.set(4, 0, 0);
        cs.get(3).body.position.set(14, 0, 0);
        Vector3 eye = new Vector3(0, 1.6f, 0), dir = new Vector3(1, 0, 0);
        List<ShotResolver.Hit> out = ShotResolver.newList();
        ShotResolver.resolve(eye, dir, 100f, cs.get(0), cs, 1, out);
        assertEquals(1, out.size());
        assertSame(cs.get(2), out.get(0).victim);
        ShotResolver.resolve(eye, dir, 100f, cs.get(0), cs, 2, out);
        assertEquals(2, out.size());
        assertSame(cs.get(1), out.get(1).victim);
        ShotResolver.resolve(eye, dir, 11f, cs.get(0), cs, 5, out);
        assertEquals(2, out.size(), "a wall at 11m stops the ray before the third target");
    }
}
