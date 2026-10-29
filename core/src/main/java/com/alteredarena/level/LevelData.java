package com.alteredarena.level;

import com.alteredarena.rules.PowerUpType;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/** Everything that defines an arena. Built once and shared by physics, navigation and rendering. */
public final class LevelData {
    public final List<Block> blocks = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    public final List<PickupSpot> pickups = new ArrayList<>();
    /** Flat walkable rectangles used to seed navigation nodes. */
    public final List<FlatSurface> flatSurfaces = new ArrayList<>();
    /** Sloped walkable strips (ramps and bridges) used to seed navigation nodes. */
    public final List<Strip> strips = new ArrayList<>();
    public final Vector3 landmark = new Vector3();
    public float boundsHalfSize;

    public enum PickupKind { HEALTH, AMMO, POWER_UP }

    public static final class Spawn {
        public final Vector3 feet;
        public final float yaw;
        public Spawn(Vector3 feet, float yaw) {
            this.feet = feet;
            this.yaw = yaw;
        }
    }

    public static final class PickupSpot {
        public final PickupKind kind;
        public final PowerUpType powerUp;
        public final Vector3 position;
        public PickupSpot(PickupKind kind, PowerUpType powerUp, Vector3 position) {
            this.kind = kind;
            this.powerUp = powerUp;
            this.position = position;
        }
    }

    public static final class FlatSurface {
        public final float minX, maxX, minZ, maxZ, y;
        public FlatSurface(float minX, float maxX, float minZ, float maxZ, float y) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.y = y;
        }
    }

    public static final class Strip {
        public final Vector3 low, high;
        public final float width;
        public Strip(Vector3 low, Vector3 high, float width) {
            this.low = low;
            this.high = high;
            this.width = width;
        }
    }
}
