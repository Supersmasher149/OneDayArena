package com.alteredarena.sim;

import com.alteredarena.level.Block;
import com.alteredarena.level.LevelData;
import com.alteredarena.rules.Tuning;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.ClosestConvexResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ContactResultCallback;
import com.badlogic.gdx.physics.bullet.collision.LocalConvexResult;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObjectWrapper;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btManifoldPoint;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Static arena collision built from {@link LevelData} blocks, plus the queries the game needs:
 * closest ray, capsule sweep and capsule depenetration. Characters are not in the Bullet world;
 * their hitboxes are analytic capsules (see {@link ShotResolver}).
 */
public final class Physics implements Disposable {
    private static boolean bulletLoaded;
    private static int live;

    /** Number of physics worlds not yet disposed (used to check restarts do not leak). */
    public static synchronized int liveCount() { return live; }

    public static synchronized void loadNatives() {
        if (!bulletLoaded) {
            Bullet.init();
            bulletLoaded = true;
        }
    }

    public static final class RayHit {
        public final Vector3 point = new Vector3();
        public final Vector3 normal = new Vector3();
        public float distance;
        public float fraction;
    }

    public static final class SweepHit {
        public final Vector3 normal = new Vector3();
        public float fraction;
    }

    private final btDefaultCollisionConfiguration configuration;
    private final btCollisionDispatcher dispatcher;
    private final btDbvtBroadphase broadphase;
    private final btCollisionWorld world;
    private final Array<btCollisionShape> shapes = new Array<>();
    private final Array<btCollisionObject> objects = new Array<>();

    private final btCapsuleShape capsule;
    private final btCollisionObject probe;
    private final ClosestRayResultCallback rayCallback;
    private final SweepCallback sweepCallback;
    private final PenetrationCallback penetrationCallback;

    private final Matrix4 fromTransform = new Matrix4();
    private final Matrix4 toTransform = new Matrix4();
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmp2 = new Vector3();
    private int rayCount;

    public Physics(LevelData level) {
        loadNatives();
        synchronized (Physics.class) { live++; }
        configuration = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(configuration);
        broadphase = new btDbvtBroadphase();
        world = new btCollisionWorld(dispatcher, broadphase, configuration);

        for (int i = 0; i < level.blocks.size(); i++) {
            Block block = level.blocks.get(i);
            btBoxShape shape = new btBoxShape(block.halfExtents);
            btCollisionObject object = new btCollisionObject();
            object.setCollisionShape(shape);
            object.setWorldTransform(block.transform);
            object.setUserValue(i);
            object.setCollisionFlags(object.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
            world.addCollisionObject(object);
            shapes.add(shape);
            objects.add(object);
        }

        capsule = new btCapsuleShape(Tuning.RADIUS, Tuning.HEIGHT - 2f * Tuning.RADIUS);
        probe = new btCollisionObject();
        probe.setCollisionShape(capsule);
        probe.setUserValue(-1);
        rayCallback = new ClosestRayResultCallback(Vector3.Zero, Vector3.Z);
        sweepCallback = new SweepCallback();
        penetrationCallback = new PenetrationCallback();
    }

    /** Closest hit against arena geometry. */
    public boolean rayCast(Vector3 from, Vector3 to, RayHit out) {
        rayCount++;
        rayCallback.setCollisionObject(null);
        rayCallback.setClosestHitFraction(1f);
        rayCallback.setRayFromWorld(from);
        rayCallback.setRayToWorld(to);
        world.rayTest(from, to, rayCallback);
        if (!rayCallback.hasHit()) return false;
        if (out != null) {
            out.fraction = rayCallback.getClosestHitFraction();
            rayCallback.getHitPointWorld(out.point);
            rayCallback.getHitNormalWorld(out.normal);
            out.distance = from.dst(to) * out.fraction;
        }
        return true;
    }

    public boolean lineOfSight(Vector3 from, Vector3 to) {
        return !rayCast(from, to, null);
    }

    /**
     * Sweeps the character capsule between two capsule-center positions.
     * Contacts whose normal does not oppose the motion are ignored so resting contacts never block sliding.
     */
    public boolean sweep(Vector3 fromCenter, Vector3 toCenter, SweepHit out) {
        sweepCallback.direction.set(toCenter).sub(fromCenter);
        if (sweepCallback.direction.isZero(1e-7f)) return false;
        sweepCallback.direction.nor();
        sweepCallback.setClosestHitFraction(1f);
        sweepCallback.setHitCollisionObject(null);
        sweepCallback.hit = false;
        fromTransform.setToTranslation(fromCenter);
        toTransform.setToTranslation(toCenter);
        world.convexSweepTest(capsule, fromTransform, toTransform, sweepCallback, 0f);
        if (!sweepCallback.hit) return false;
        out.fraction = sweepCallback.getClosestHitFraction();
        out.normal.set(sweepCallback.hitNormal);
        return true;
    }

    /**
     * Pushes a capsule centered at {@code center} out of any geometry it overlaps.
     * @return true if the center was moved
     */
    public boolean depenetrate(Vector3 center) {
        boolean moved = false;
        for (int iteration = 0; iteration < 4; iteration++) {
            probe.setWorldTransform(fromTransform.setToTranslation(center));
            penetrationCallback.push.setZero();
            penetrationCallback.contacts = 0;
            world.contactTest(probe, penetrationCallback);
            if (penetrationCallback.contacts == 0 || penetrationCallback.push.isZero(1e-5f)) break;
            center.add(penetrationCallback.push);
            moved = true;
        }
        return moved;
    }

    /** True if a standing capsule with feet at {@code feet} overlaps no geometry. */
    public boolean capsuleFits(Vector3 feet) {
        tmp.set(feet).add(0f, Tuning.HEIGHT * 0.5f, 0f);
        probe.setWorldTransform(fromTransform.setToTranslation(tmp));
        penetrationCallback.push.setZero();
        penetrationCallback.contacts = 0;
        world.contactTest(probe, penetrationCallback);
        return penetrationCallback.contacts == 0;
    }

    /** Height of the first surface below {@code from} within {@code maxDrop}, or NaN. Writes the normal. */
    public float groundHeight(Vector3 from, float maxDrop, Vector3 normalOut) {
        tmp2.set(from).sub(0f, maxDrop, 0f);
        RayHit hit = scratchHit;
        if (!rayCast(from, tmp2, hit)) return Float.NaN;
        if (normalOut != null) normalOut.set(hit.normal);
        return hit.point.y;
    }

    private final RayHit scratchHit = new RayHit();

    public int rayCount() { return rayCount; }

    @Override
    public void dispose() {
        penetrationCallback.dispose();
        sweepCallback.dispose();
        rayCallback.dispose();
        probe.dispose();
        capsule.dispose();
        for (btCollisionObject object : objects) {
            world.removeCollisionObject(object);
            object.dispose();
        }
        objects.clear();
        for (btCollisionShape shape : shapes) shape.dispose();
        shapes.clear();
        world.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        configuration.dispose();
        synchronized (Physics.class) { live--; }
    }

    private static final class SweepCallback extends ClosestConvexResultCallback {
        final Vector3 direction = new Vector3();
        final Vector3 hitNormal = new Vector3();
        final Vector3 normal = new Vector3();
        boolean hit;

        SweepCallback() {
            super(Vector3.Zero, Vector3.Z);
        }

        @Override
        public float addSingleResult(LocalConvexResult result, boolean normalInWorldSpace) {
            result.getHitNormalLocal(normal);
            if (!normalInWorldSpace) {
                normal.rot(result.getHitCollisionObject().getWorldTransform());
            }
            // Ignore contacts we are sliding along or moving away from.
            if (normal.dot(direction) > -1e-3f) return 1f;
            float fraction = result.getHitFraction();
            if (fraction <= getClosestHitFraction()) {
                hit = true;
                hitNormal.set(normal);
            }
            return super.addSingleResult(result, true);
        }
    }

    private static final class PenetrationCallback extends ContactResultCallback {
        final Vector3 push = new Vector3();
        final Vector3 normal = new Vector3();
        int contacts;

        @Override
        public float addSingleResult(btManifoldPoint cp, btCollisionObjectWrapper colObj0Wrap, int partId0, int index0,
                                     btCollisionObjectWrapper colObj1Wrap, int partId1, int index1) {
            float distance = cp.getDistance();
            if (distance >= -0.001f) return 0f;
            contacts++;
            cp.getNormalWorldOnB(normal);
            // normalWorldOnB points from B towards A; move the probe along it if the probe is A.
            float sign = colObj0Wrap.getCollisionObject().getUserValue() == -1 ? 1f : -1f;
            float depth = -distance + 0.002f;
            float along = push.dot(normal) * sign;
            if (along < depth) push.mulAdd(normal, sign * (depth - Math.max(0f, along)));
            return 0f;
        }
    }
}
