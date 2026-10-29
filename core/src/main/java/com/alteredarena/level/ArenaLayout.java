package com.alteredarena.level;

import com.alteredarena.level.Block.Style;
import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.level.LevelData.PickupSpot;
import com.alteredarena.rules.PowerUpType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * "The Reactor": a 60 x 60 m arena. Ground floor (y=0) loops around a raised central platform (y=2)
 * holding the reactor pillar landmark. North and south terraces (y=4) connect to the ground by corner ramps
 * and to the platform by long bridges. Side walls with a vaultable window split off the east and west lanes.
 */
public final class ArenaLayout {
    private ArenaLayout() {}

    // Palette (RGBA8888)
    static final int FLOOR = 0x141428FF, WALL = 0x221437FF, PLATFORM = 0x1B2340FF, RAMP = 0x2A2050FF,
            COVER = 0x1E2C3CFF, PILLAR = 0x2C1838FF, RAIL = 0x262040FF;
    static final int MAGENTA = 0xFF2BD6FF, CYAN = 0x22F0FFFF, YELLOW = 0xFFE03BFF, ORANGE = 0xFF8A1FFF, LIME = 0x8CFF3BFF;

    public static LevelData build() {
        LevelData d = new LevelData();
        d.boundsHalfSize = 30f;
        d.landmark.set(0f, 9f, 0f);

        // Floor and outer walls
        box(d, Style.FLOOR, 0, -0.5f, 0, 31, 0.5f, 31, FLOOR, 0);
        box(d, Style.WALL, 0, 4, -30.5f, 31, 4, 0.5f, WALL, MAGENTA);
        box(d, Style.WALL, 0, 4, 30.5f, 31, 4, 0.5f, WALL, MAGENTA);
        box(d, Style.WALL, -30.5f, 4, 0, 0.5f, 4, 31, WALL, CYAN);
        box(d, Style.WALL, 30.5f, 4, 0, 0.5f, 4, 31, WALL, CYAN);
        d.flatSurfaces.add(new LevelData.FlatSurface(-29.5f, 29.5f, -29.5f, 29.5f, 0f));
        // Underground ceiling, far above the highest reachable point.
        box(d, Style.CEILING, 0, 15, 0, 31, 0.5f, 31, 0x0E0A18FF, 0);

        // Central platform and the reactor pillar landmark
        box(d, Style.PLATFORM, 0, 1, 0, 6, 1, 6, PLATFORM, CYAN);
        box(d, Style.PILLAR, 0, 5.5f, 0, 1.2f, 3.5f, 1.2f, PILLAR, MAGENTA);
        d.flatSurfaces.add(new LevelData.FlatSurface(-6f, 6f, -6f, 6f, 2f));
        ramp(d, new Vector3(12, 0, 0), new Vector3(6, 2, 0), 3.6f, 0.6f, YELLOW);
        ramp(d, new Vector3(-12, 0, 0), new Vector3(-6, 2, 0), 3.6f, 0.6f, YELLOW);

        // North and south terraces, their corner ramps, railings and bridges to the platform
        for (int side = -1; side <= 1; side += 2) {
            float zc = 26.5f * side;
            box(d, Style.PLATFORM, 0, 2, zc, 18, 2, 3.5f, PLATFORM, MAGENTA);
            float zFront = 23f * side;
            d.flatSurfaces.add(new LevelData.FlatSurface(-18f, 18f, Math.min(zFront, 30f * side), Math.max(zFront, 30f * side), 4f));
            ramp(d, new Vector3(16, 0, 13 * side), new Vector3(16, 4, zFront), 4f, 0.6f, ORANGE);
            ramp(d, new Vector3(-16, 0, 13 * side), new Vector3(-16, 4, zFront), 4f, 0.6f, ORANGE);
            float railZ = zFront + 0.25f * side;
            box(d, Style.RAIL, -9.5f, 4.55f, railZ, 4.5f, 0.55f, 0.25f, RAIL, CYAN);
            box(d, Style.RAIL, 9.5f, 4.55f, railZ, 4.5f, 0.55f, 0.25f, RAIL, CYAN);
            ramp(d, new Vector3(0, 2, 6 * side), new Vector3(0, 4, zFront), 3.6f, 0.5f, CYAN);
        }

        // East and west lane walls with a low, vaultable window
        for (int side = -1; side <= 1; side += 2) {
            float x = 21f * side;
            box(d, Style.WALL, x, 2, -4.5f, 0.5f, 2, 3, WALL, LIME);
            box(d, Style.WALL, x, 2, 4.5f, 0.5f, 2, 3, WALL, LIME);
            box(d, Style.COVER, x, 0.55f, 0, 0.5f, 0.55f, 1.5f, COVER, LIME);
        }

        // Ground cover
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(d, Style.COVER, 9 * sx, 1, 9 * sz, 1, 1, 1, COVER, ORANGE);
                box(d, Style.COVER, 10.6f * sx, 0.5f, 7.4f * sz, 0.5f, 0.5f, 0.5f, COVER, ORANGE);
                box(d, Style.PILLAR, 8 * sx, 3, 18 * sz, 0.75f, 3, 0.75f, PILLAR, MAGENTA);
                box(d, Style.COVER, 24 * sx, 0.6f, 16 * sz, 2, 0.6f, 0.3f, COVER, CYAN);
            }
        }

        // Spawns: spread over every region, each turned 35 degrees off the center line so no pair stares
        // straight down the same sightline.
        spawn(d, -25, 0, -12, 35);
        spawn(d, 25, 0, 12, 35);
        spawn(d, -25, 0, 12, -35);
        spawn(d, 25, 0, -12, -35);
        spawn(d, -6, 4, -27, 35);
        spawn(d, 6, 4, 27, 35);
        spawn(d, -12, 0, -4, -35);
        spawn(d, 12, 0, 4, -35);

        // Power-ups: platform pair is exposed from four approaches; terrace pair needs a climb;
        // corner pair is a long detour into a dead end.
        pickup(d, PickupKind.POWER_UP, PowerUpType.ADDY, 0, 2, -3.8f);
        pickup(d, PickupKind.POWER_UP, PowerUpType.ACID, 0, 2, 3.8f);
        pickup(d, PickupKind.POWER_UP, PowerUpType.WEED, 0, 4, -27);
        pickup(d, PickupKind.POWER_UP, PowerUpType.MOLLY, 0, 4, 27);
        pickup(d, PickupKind.POWER_UP, PowerUpType.MUSHROOMS, -26, 0, -26);
        pickup(d, PickupKind.POWER_UP, PowerUpType.COKE, 26, 0, 26);
        pickup(d, PickupKind.HEALTH, null, 25.5f, 0, 0);
        pickup(d, PickupKind.HEALTH, null, -25.5f, 0, 0);
        pickup(d, PickupKind.HEALTH, null, 0, 0, -15);
        pickup(d, PickupKind.HEALTH, null, 0, 0, 15);
        pickup(d, PickupKind.AMMO, null, 24, 0, -24);
        pickup(d, PickupKind.AMMO, null, -24, 0, 24);
        pickup(d, PickupKind.AMMO, null, -11, 0, -20);
        pickup(d, PickupKind.AMMO, null, 11, 0, 20);
        return d;
    }

    private static void box(LevelData d, Style style, float x, float y, float z, float hx, float hy, float hz,
                            int rgba, int trim) {
        d.blocks.add(new Block(style, new Matrix4().setToTranslation(x, y, z), new Vector3(hx, hy, hz), rgba, trim));
    }

    /**
     * Adds a sloped slab whose top surface runs exactly from {@code low} to {@code high}.
     * The low end is extended under the floor so there is no lip to catch on.
     */
    static void ramp(LevelData d, Vector3 low, Vector3 high, float width, float thickness, int trim) {
        Vector3 forward = new Vector3(high).sub(low).nor();
        Vector3 side = new Vector3(forward).crs(Vector3.Y).nor();
        Vector3 normal = new Vector3(side).crs(forward).nor();
        if (normal.y < 0) normal.scl(-1f);
        Vector3 xAxis = new Vector3(normal).crs(forward).nor();
        float extend = 0.5f;
        Vector3 start = new Vector3(low).mulAdd(forward, -extend);
        float length = start.dst(high);
        Vector3 center = new Vector3(start).add(high).scl(0.5f).mulAdd(normal, -thickness * 0.5f);
        Matrix4 transform = basis(xAxis, normal, forward, center);
        d.blocks.add(new Block(Style.RAMP, transform, new Vector3(width * 0.5f, thickness * 0.5f, length * 0.5f), RAMP, trim));
        d.strips.add(new LevelData.Strip(new Vector3(low), new Vector3(high), width));
    }

    /** Matrix whose columns are the given axes (Matrix4.set(Vector3...) fills rows instead). */
    static Matrix4 basis(Vector3 x, Vector3 y, Vector3 z, Vector3 position) {
        Matrix4 m = new Matrix4();
        float[] v = m.val;
        v[Matrix4.M00] = x.x; v[Matrix4.M10] = x.y; v[Matrix4.M20] = x.z;
        v[Matrix4.M01] = y.x; v[Matrix4.M11] = y.y; v[Matrix4.M21] = y.z;
        v[Matrix4.M02] = z.x; v[Matrix4.M12] = z.y; v[Matrix4.M22] = z.z;
        v[Matrix4.M03] = position.x; v[Matrix4.M13] = position.y; v[Matrix4.M23] = position.z;
        return m;
    }

    private static void spawn(LevelData d, float x, float y, float z, float offsetDegrees) {
        // yaw 0 faces -Z, so facing direction (sin yaw, -cos yaw) points at the origin when yaw = atan2(-x, z).
        float yawToCenter = MathUtils.atan2(-x, z) * MathUtils.radiansToDegrees;
        d.spawns.add(new LevelData.Spawn(new Vector3(x, y, z), yawToCenter + offsetDegrees));
    }

    private static void pickup(LevelData d, PickupKind kind, PowerUpType type, float x, float y, float z) {
        d.pickups.add(new PickupSpot(kind, type, new Vector3(x, y, z)));
    }
}
