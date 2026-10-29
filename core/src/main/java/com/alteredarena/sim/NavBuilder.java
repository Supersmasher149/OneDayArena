package com.alteredarena.sim;

import com.alteredarena.level.LevelData;
import com.alteredarena.level.NavGraph;
import com.alteredarena.rules.Tuning;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntArray;

/**
 * Builds the bot waypoint graph from the level's walkable surfaces, then keeps only edges that the
 * collision world proves walkable: continuous ground with no step or drop, walkable slope,
 * and head and shoulder clearance along the whole segment.
 */
public final class NavBuilder {
    static final float GRID = 3f;
    static final float MAX_EDGE = 4.6f;
    static final float SAMPLE_STEP = 0.4f;
    static final float MAX_STEP_PER_SAMPLE = 0.25f;

    private final Physics physics;
    private final Vector3 a = new Vector3(), b = new Vector3(), from = new Vector3(), to = new Vector3();
    private final Vector3 normal = new Vector3(), side = new Vector3(), prev = new Vector3(), cur = new Vector3();

    private NavBuilder(Physics physics) {
        this.physics = physics;
    }

    public static NavGraph build(LevelData level, Physics physics) {
        return new NavBuilder(physics).run(level);
    }

    private NavGraph run(LevelData level) {
        NavGraph graph = new NavGraph();
        Vector3 p = new Vector3();
        for (LevelData.FlatSurface s : level.flatSurfaces) {
            float inset = Tuning.RADIUS + 0.4f;
            float minX = s.minX + inset, maxX = s.maxX - inset, minZ = s.minZ + inset, maxZ = s.maxZ - inset;
            int nx = Math.max(1, Math.round((maxX - minX) / GRID));
            int nz = Math.max(1, Math.round((maxZ - minZ) / GRID));
            for (int ix = 0; ix <= nx; ix++) {
                for (int iz = 0; iz <= nz; iz++) {
                    p.set(minX + (maxX - minX) * ix / nx, s.y, minZ + (maxZ - minZ) * iz / nz);
                    tryAdd(graph, p);
                }
            }
        }
        for (LevelData.Strip strip : level.strips) {
            float length = strip.low.dst(strip.high);
            int steps = Math.max(1, Math.round(length / 2.5f));
            for (int i = 0; i <= steps; i++) {
                p.set(strip.low).lerp(strip.high, (float) i / steps);
                tryAdd(graph, p);
            }
        }
        for (int i = 0; i < graph.size(); i++) {
            for (int j = i + 1; j < graph.size(); j++) {
                Vector3 ni = graph.nodes.get(i), nj = graph.nodes.get(j);
                if (ni.dst2(nj) > MAX_EDGE * MAX_EDGE) continue;
                if (walkable(ni, nj)) graph.connect(i, j);
            }
        }
        return keepLargestComponent(graph);
    }

    private void tryAdd(NavGraph graph, Vector3 p) {
        // Settle onto the actual surface and require standing room.
        from.set(p.x, p.y + 1.0f, p.z);
        float h = physics.groundHeight(from, 2.0f, normal);
        if (Float.isNaN(h) || normal.y < Tuning.WALKABLE_NORMAL_Y) return;
        cur.set(p.x, h + 0.03f, p.z);
        if (!physics.capsuleFits(cur)) return;
        for (Vector3 existing : graph.nodes) if (existing.dst2(cur) < 1.0f) return;
        cur.y = h;
        graph.addNode(cur);
    }

    /** True if a character can walk in a straight line between two nodes, in both directions. */
    boolean walkable(Vector3 start, Vector3 end) {
        float horizontal = (float) Math.hypot(end.x - start.x, end.z - start.z);
        if (horizontal < 0.1f) return false;
        if (Math.abs(end.y - start.y) > horizontal * 0.5f + 0.1f) return false;
        int samples = Math.max(2, (int) Math.ceil(horizontal / SAMPLE_STEP));
        side.set(end.z - start.z, 0f, start.x - end.x).nor().scl(Tuning.RADIUS);
        prev.set(start);
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / samples;
            a.set(start).lerp(end, t);
            from.set(a.x, a.y + 1.0f, a.z);
            float h = physics.groundHeight(from, 2.0f, normal);
            if (Float.isNaN(h) || normal.y < Tuning.WALKABLE_NORMAL_Y) return false;
            if (Math.abs(h - a.y) > 0.45f) return false;
            if (Math.abs(h - prev.y) > MAX_STEP_PER_SAMPLE) return false;
            cur.set(a.x, h, a.z);
            for (float lift : new float[]{0.6f, 1.75f}) {
                from.set(prev).add(0f, lift, 0f);
                to.set(cur).add(0f, lift, 0f);
                if (!physics.lineOfSight(from, to)) return false;
            }
            from.set(prev).add(side).add(0f, 0.6f, 0f);
            to.set(cur).add(side).add(0f, 0.6f, 0f);
            if (!physics.lineOfSight(from, to)) return false;
            from.set(prev).sub(side).add(0f, 0.6f, 0f);
            to.set(cur).sub(side).add(0f, 0.6f, 0f);
            if (!physics.lineOfSight(from, to)) return false;
            prev.set(cur);
        }
        return true;
    }

    private static NavGraph keepLargestComponent(NavGraph graph) {
        int n = graph.size();
        int[] component = new int[n];
        java.util.Arrays.fill(component, -1);
        int bestComponent = -1, bestSize = 0, id = 0;
        IntArray stack = new IntArray();
        for (int i = 0; i < n; i++) {
            if (component[i] >= 0) continue;
            int size = 0;
            stack.add(i);
            component[i] = id;
            while (stack.size > 0) {
                int at = stack.pop();
                size++;
                IntArray e = graph.edges.get(at);
                for (int k = 0; k < e.size; k++) {
                    int next = e.get(k);
                    if (component[next] < 0) {
                        component[next] = id;
                        stack.add(next);
                    }
                }
            }
            if (size > bestSize) {
                bestSize = size;
                bestComponent = id;
            }
            id++;
        }
        NavGraph result = new NavGraph();
        int[] remap = new int[n];
        for (int i = 0; i < n; i++) remap[i] = component[i] == bestComponent ? result.addNode(graph.nodes.get(i)) : -1;
        for (int i = 0; i < n; i++) {
            if (remap[i] < 0) continue;
            IntArray e = graph.edges.get(i);
            for (int k = 0; k < e.size; k++) if (remap[e.get(k)] >= 0) result.connect(remap[i], remap[e.get(k)]);
        }
        return result;
    }
}
