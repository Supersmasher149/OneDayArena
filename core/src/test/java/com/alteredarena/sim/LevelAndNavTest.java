package com.alteredarena.sim;

import com.alteredarena.level.ArenaLayout;
import com.alteredarena.level.LevelData;
import com.alteredarena.level.NavGraph;
import com.alteredarena.rules.Modifiers;
import com.alteredarena.rules.Tuning;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntArray;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that level data, collision and the generated nav graph agree. */
class LevelAndNavTest {
    static LevelData level;
    static Physics physics;
    static NavGraph nav;

    @BeforeAll
    static void build() {
        level = ArenaLayout.build();
        physics = new Physics(level);
        nav = NavBuilder.build(level, physics);
    }

    @AfterAll
    static void dispose() {
        physics.dispose();
    }

    @Test
    void spawnsAndPickupsStandOnWalkableGroundWithRoom() {
        for (LevelData.Spawn s : level.spawns) assertStandable(s.feet, "spawn");
        for (LevelData.PickupSpot p : level.pickups) assertStandable(p.position, "pickup " + p.kind + " " + p.powerUp);
    }

    private void assertStandable(Vector3 feet, String what) {
        Vector3 normal = new Vector3();
        float h = physics.groundHeight(new Vector3(feet).add(0, 0.5f, 0), 1f, normal);
        assertEquals(feet.y, h, 0.05f, what + " ground height at " + feet);
        assertTrue(normal.y > Tuning.WALKABLE_NORMAL_Y, what);
        assertTrue(physics.capsuleFits(new Vector3(feet).add(0, 0.05f, 0)), what + " capsule fits at " + feet);
    }

    @Test
    void noTwoSpawnsStareAtEachOther() {
        for (int i = 0; i < level.spawns.size(); i++) {
            for (int j = i + 1; j < level.spawns.size(); j++) {
                LevelData.Spawn a = level.spawns.get(i), b = level.spawns.get(j);
                Vector3 ea = new Vector3(a.feet).add(0, Tuning.EYE_HEIGHT, 0), eb = new Vector3(b.feet).add(0, Tuning.EYE_HEIGHT, 0);
                if (!physics.lineOfSight(ea, eb)) continue;
                boolean aFacesB = facing(a, b.feet) < 25f, bFacesA = facing(b, a.feet) < 25f;
                assertFalse(aFacesB && bFacesA, "spawns " + i + " and " + j + " face each other");
            }
        }
    }

    private static float facing(LevelData.Spawn s, Vector3 target) {
        float yawTo = MathUtils.atan2(target.x - s.feet.x, -(target.z - s.feet.z)) * MathUtils.radiansToDegrees;
        float d = (yawTo - s.yaw) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return Math.abs(d);
    }

    @Test
    void navGraphCoversEveryElevationAndIsConnected() {
        assertTrue(nav.size() > 250, "nodes: " + nav.size());
        int ground = 0, platform = 0, terrace = 0, slope = 0;
        for (Vector3 n : nav.nodes) {
            if (n.y < 0.1f) ground++;
            else if (Math.abs(n.y - 2f) < 0.1f) platform++;
            else if (Math.abs(n.y - 4f) < 0.1f) terrace++;
            else slope++;
        }
        assertTrue(ground > 100 && platform > 8 && terrace > 20 && slope > 10,
                ground + "/" + platform + "/" + terrace + "/" + slope);
        // keepLargestComponent guarantees one component; check routes between levels explicitly.
        IntArray path = new IntArray();
        int start = nav.nearest(new Vector3(-25, 0, -12));
        for (Vector3 goal : new Vector3[]{new Vector3(0, 2, -4), new Vector3(0, 4, 27), new Vector3(0, 4, -27), new Vector3(26, 0, 26)}) {
            assertTrue(nav.findPath(start, nav.nearest(goal), path), "route to " + goal);
            assertTrue(path.size > 2);
        }
    }

    @Test
    void everyPickupHasANearbyNode() {
        for (LevelData.PickupSpot p : level.pickups) {
            Vector3 n = nav.nodes.get(nav.nearest(p.position));
            assertTrue(n.dst(p.position) < 3f, "pickup at " + p.position + " nearest node " + n);
        }
    }

    /** Physically walks the character controller along edges: all sloped edges plus a random sample. */
    @Test
    void navEdgesAreWalkableByTheCharacterController() {
        CharacterMotor motor = new CharacterMotor(physics);
        Body body = new Body();
        Random random = new Random(3);
        int tested = 0, failures = 0;
        StringBuilder report = new StringBuilder();
        for (int a = 0; a < nav.size(); a++) {
            IntArray e = nav.edges.get(a);
            for (int k = 0; k < e.size; k++) {
                int b = e.get(k);
                Vector3 from = nav.nodes.get(a), to = nav.nodes.get(b);
                boolean sloped = Math.abs(from.y - to.y) > 0.05f;
                if (!sloped && random.nextFloat() > 0.08f) continue;
                tested++;
                if (!walk(motor, body, from, to)) {
                    failures++;
                    if (failures < 10) report.append(from).append(" -> ").append(to).append(" ended ").append(body.position).append('\n');
                }
            }
        }
        assertTrue(tested > 150, "tested " + tested);
        assertEquals(0, failures, "unwalkable edges:\n" + report);
    }

    private static boolean walk(CharacterMotor motor, Body body, Vector3 from, Vector3 to) {
        body.teleport(new Vector3(from).add(0, 0.02f, 0));
        float limit = from.dst(to) / Tuning.MOVE_SPEED * 2.5f + 1f;
        for (float t = 0; t < limit; t += Tuning.FIXED_DT) {
            float dx = to.x - body.position.x, dz = to.z - body.position.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 0.4f) return Math.abs(body.position.y - to.y) < 0.3f;
            motor.step(body, dx / d, dz / d, false, Modifiers.NONE, Tuning.FIXED_DT);
        }
        return false;
    }
}
