package com.alteredarena.level;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BinaryHeap;
import com.badlogic.gdx.utils.IntArray;

/** Waypoint graph with A* search. Nodes are feet positions on walkable surfaces. */
public final class NavGraph {
    public final Array<Vector3> nodes = new Array<>();
    public final Array<IntArray> edges = new Array<>();

    public int addNode(Vector3 position) {
        nodes.add(new Vector3(position));
        edges.add(new IntArray(8));
        return nodes.size - 1;
    }

    public void connect(int a, int b) {
        if (!edges.get(a).contains(b)) edges.get(a).add(b);
        if (!edges.get(b).contains(a)) edges.get(b).add(a);
    }

    public int size() { return nodes.size; }

    public int edgeCount() {
        int n = 0;
        for (IntArray e : edges) n += e.size;
        return n / 2;
    }

    /** Nearest node by straight distance, preferring nodes on a similar height. */
    public int nearest(Vector3 p) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < nodes.size; i++) {
            Vector3 n = nodes.get(i);
            float dy = Math.abs(n.y - p.y);
            float score = n.dst2(p.x, n.y, p.z) + dy * dy * 9f;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Indices of nodes sorted by distance (closest first), at most {@code count}. */
    public IntArray nearestCandidates(Vector3 p, int count, IntArray out) {
        out.clear();
        float[] scores = new float[count];
        for (int i = 0; i < nodes.size; i++) {
            Vector3 n = nodes.get(i);
            float dy = Math.abs(n.y - p.y);
            float score = n.dst2(p.x, n.y, p.z) + dy * dy * 9f;
            int insert = out.size;
            while (insert > 0 && scores[insert - 1] > score) insert--;
            if (insert >= count) continue;
            if (out.size < count) out.add(0);
            for (int j = out.size - 1; j > insert; j--) {
                out.items[j] = out.items[j - 1];
                scores[j] = scores[j - 1];
            }
            out.items[insert] = i;
            scores[insert] = score;
        }
        return out;
    }

    private final class SearchNode extends BinaryHeap.Node {
        final int index;
        SearchNode(int index) {
            super(0f);
            this.index = index;
        }
    }

    /**
     * A* from start to goal. Writes node indices (start..goal) into {@code out}.
     * @return false if unreachable
     */
    public boolean findPath(int start, int goal, IntArray out) {
        out.clear();
        if (start < 0 || goal < 0) return false;
        int n = nodes.size;
        float[] g = new float[n];
        int[] parent = new int[n];
        boolean[] closed = new boolean[n];
        SearchNode[] open = new SearchNode[n];
        java.util.Arrays.fill(g, Float.MAX_VALUE);
        java.util.Arrays.fill(parent, -1);
        BinaryHeap<SearchNode> heap = new BinaryHeap<>();
        g[start] = 0f;
        open[start] = new SearchNode(start);
        heap.add(open[start], nodes.get(start).dst(nodes.get(goal)));
        Vector3 goalPos = nodes.get(goal);
        while (heap.size > 0) {
            SearchNode current = heap.pop();
            int ci = current.index;
            if (ci == goal) break;
            closed[ci] = true;
            IntArray neighbours = edges.get(ci);
            for (int k = 0; k < neighbours.size; k++) {
                int ni = neighbours.get(k);
                if (closed[ni]) continue;
                float cost = g[ci] + nodes.get(ci).dst(nodes.get(ni));
                if (cost >= g[ni]) continue;
                g[ni] = cost;
                parent[ni] = ci;
                float f = cost + nodes.get(ni).dst(goalPos);
                if (open[ni] == null) {
                    open[ni] = new SearchNode(ni);
                    heap.add(open[ni], f);
                } else {
                    heap.setValue(open[ni], f);
                }
            }
        }
        if (start != goal && parent[goal] < 0) return false;
        for (int at = goal; at != -1; at = parent[at]) {
            out.add(at);
            if (at == start) break;
        }
        out.reverse();
        return true;
    }
}
