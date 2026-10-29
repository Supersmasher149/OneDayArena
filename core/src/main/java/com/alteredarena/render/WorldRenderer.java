package com.alteredarena.render;

import com.alteredarena.level.LevelData;
import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponType;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.sim.Pickup;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Draws the arena, characters, pickups and effects for a {@link GameWorld}, plus the first-person weapon. */
public final class WorldRenderer implements Disposable {
    public final PerspectiveCamera camera;
    private final PerspectiveCamera viewCamera;
    private final ModelBatch batch;
    private final Environment environment;
    private final ModelFactory factory;

    private final ModelInstance level, rings, core, beam;
    private final Model characterModel, shadowModel, bubbleModel, auraModel, padModel, columnModel, cubeModel, flashModel;
    private final Map<String, Model> pickupModels = new HashMap<>();
    private final Array<CharacterView> characters = new Array<>();
    private final Array<PickupView> pickups = new Array<>();
    private final Effects effects;
    private final ViewModel viewModel;
    private final Array<ModelInstance> visible = new Array<>();
    private final Vector3 tmp = new Vector3(), tmp2 = new Vector3();
    private float time;

    public WorldRenderer(LevelData levelData) {
        DefaultShader.Config config = new DefaultShader.Config();
        config.numDirectionalLights = 2;
        config.numPointLights = 0;
        config.numSpotLights = 0;
        batch = new ModelBatch(new DefaultShaderProvider(config));

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.34f, 0.31f, 0.46f, 1f));
        environment.set(new ColorAttribute(ColorAttribute.Fog, 0.035f, 0.02f, 0.07f, 1f));
        environment.add(new DirectionalLight().set(0.85f, 0.62f, 0.95f, -0.35f, -1f, -0.25f));
        environment.add(new DirectionalLight().set(0.25f, 0.5f, 0.65f, 0.55f, -0.55f, 0.6f));

        camera = new PerspectiveCamera(72f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.05f;
        camera.far = 110f;
        viewCamera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        viewCamera.near = 0.01f;
        viewCamera.far = 5f;
        viewCamera.position.setZero();
        viewCamera.direction.set(0f, 0f, -1f);
        viewCamera.up.set(Vector3.Y);
        viewCamera.update();

        factory = new ModelFactory();
        level = new ModelInstance(factory.level(levelData));
        rings = new ModelInstance(factory.landmarkRings());
        core = new ModelInstance(factory.landmarkCore());
        beam = new ModelInstance(factory.beam());
        characterModel = factory.character();
        shadowModel = factory.shadow();
        bubbleModel = factory.bubble();
        auraModel = factory.auraRing();
        padModel = factory.pad();
        columnModel = factory.column();
        cubeModel = factory.glowCube();
        flashModel = factory.flash();
        effects = new Effects(cubeModel, flashModel);
        viewModel = new ViewModel(factory.pulseRifle(), factory.scattergun(), flashModel);
    }

    /** Creates views for a (new) world. Safe to call again on restart; old views are discarded. */
    public void bind(GameWorld world) {
        characters.clear();
        for (Combatant c : world.combatants) characters.add(new CharacterView(c));
        pickups.clear();
        for (Pickup p : world.pickups) pickups.add(new PickupView(p));
        effects.clear();
        viewModel.reset();
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        viewCamera.viewportWidth = width;
        viewCamera.viewportHeight = height;
        viewCamera.update();
    }

    // ------------------------------------------------------------------ events

    /** Spawns visual effects for a simulation event. {@code viewerId} is the first-person combatant or -1. */
    public void onEvent(GameEvent e, GameWorld world, int viewerId, boolean reducedEffects) {
        switch (e.type) {
            case SHOT: {
                if (e.actor == viewerId) viewModel.onShot(e.weapon, reducedEffects);
                else {
                    Combatant shooter = world.combatant(e.actor);
                    if (shooter != null) effects.flash(muzzle(shooter, tmp), colorOf(e.weapon, shooter), e.weapon == WeaponType.SCATTERGUN ? 0.35f : 0.22f);
                }
                break;
            }
            case TRACER: {
                Combatant shooter = world.combatant(e.actor);
                Vector3 start = e.actor == viewerId ? viewerMuzzle(shooter, tmp) : muzzle(shooter, tmp);
                effects.tracer(start, e.position2, colorOf(e.weapon, shooter), e.weapon == WeaponType.SCATTERGUN ? 0.02f : 0.035f,
                        e.weapon == WeaponType.SCATTERGUN ? 0.09f : 0.07f);
                break;
            }
            case IMPACT:
                effects.burst(e.position, e.position2, colorOf(e.weapon, world.combatant(e.actor)), e.weapon == WeaponType.SCATTERGUN ? 2 : 4, 4f, 0.06f);
                break;
            case HIT: {
                Combatant victim = world.combatant(e.target);
                effects.burst(e.position, tmp2.set(e.position2).sub(e.position).nor(), new Color(victim.rgba), 5, 5f, 0.07f);
                break;
            }
            case KILL: {
                Combatant victim = world.combatant(e.target);
                effects.burst(e.position, Vector3.Y, new Color(victim.rgba), 34, 8f, 0.14f);
                break;
            }
            case PICKUP:
            case PICKUP_RESPAWN: {
                Color c = pickupColor(e.pickup);
                effects.burst(tmp.set(e.position).add(0f, 1f, 0f), Vector3.Y, c, e.type == GameEvent.Type.PICKUP ? 22 : 12, 5f, 0.08f);
                break;
            }
            case RESPAWN:
                effects.burst(tmp.set(e.position).add(0f, 1f, 0f), Vector3.Y, new Color(world.combatant(e.actor).rgba), 20, 4f, 0.08f);
                break;
            case LAND:
                if (e.actor == viewerId) viewModel.onLand(e.value);
                break;
            default:
                break;
        }
    }

    private static Color colorOf(WeaponType weapon, Combatant shooter) {
        Color c = new Color(shooter != null ? shooter.rgba : 0xFFFFFFFF);
        return weapon == WeaponType.SCATTERGUN ? c.lerp(1f, 0.6f, 0.2f, 1f, 0.45f) : c.lerp(Color.WHITE, 0.35f);
    }

    static Color pickupColor(Pickup p) {
        if (p.kind == PickupKind.HEALTH) return new Color(0xFF3B5CFF);
        if (p.kind == PickupKind.AMMO) return new Color(0x4DA6FFFF);
        return new Color(p.powerUp.rgba);
    }

    private Vector3 muzzle(Combatant c, Vector3 out) {
        float s = MathUtils.sinDeg(c.yaw), co = MathUtils.cosDeg(c.yaw);
        // gun tip in model space (0.3, 1.2, -0.7), rotated by yaw
        return out.set(c.body.position).add(0.3f * co + 0.7f * s, 1.2f, 0.3f * s - 0.7f * co);
    }

    private Vector3 viewerMuzzle(Combatant c, Vector3 out) {
        Vector3 dir = c.aimDirection(new Vector3());
        Vector3 right = new Vector3(MathUtils.cosDeg(c.yaw), 0f, MathUtils.sinDeg(c.yaw));
        return c.eye(out).mulAdd(dir, 0.55f).mulAdd(right, 0.16f).add(0f, -0.13f, 0f);
    }

    // ------------------------------------------------------------------ frame

    public void update(float delta) {
        time += delta;
        effects.update(delta);
        viewModel.update(delta);
    }

    /** Renders the world from {@link #camera}. {@code hideId} is not drawn (the first-person viewer). */
    public void render(GameWorld world, float alpha, int hideId) {
        Gdx.gl.glClearColor(0.03f, 0.015f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        visible.clear();
        visible.add(level);

        // Landmark animation
        for (int i = 0; i < 3; i++) {
            Material m = rings.materials.get(i);
            float pulse = 0.55f + 0.45f * MathUtils.sin(time * 2.2f + i * 1.3f);
            ColorAttribute emissive = (ColorAttribute) m.get(ColorAttribute.Emissive);
            emissive.color.set(i % 2 == 0 ? 1f : 0.13f, i % 2 == 0 ? 0.17f : 0.94f, i % 2 == 0 ? 0.84f : 1f, 1f).mul(pulse);
        }
        visible.add(rings);
        core.getNode("core").rotation.set(Vector3.Y, time * 40f);
        core.getNode("halo").rotation.set(Vector3.Y, -time * 25f);
        core.transform.setToTranslation(0f, 11.2f + MathUtils.sin(time * 1.3f) * 0.25f, 0f);
        core.calculateTransforms();
        visible.add(core);
        beam.transform.setToTranslation(0f, 12.2f, 0f).scale(1f, 5.5f, 1f);
        visible.add(beam);

        for (PickupView v : pickups) v.collect(visible);
        for (CharacterView v : characters) {
            if (v.combatant.id == hideId) continue;
            v.collect(world, alpha, visible, Gdx.graphics.getDeltaTime());
        }
        effects.collect(visible);

        batch.begin(camera);
        batch.render(visible, environment);
        batch.end();
    }

    /** Draws the first-person weapon on top of the world. */
    public void renderViewModel(Combatant player, float bobSpeed, boolean reducedEffects) {
        if (player == null || !player.alive) return;
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        viewModel.prepare(player, bobSpeed, reducedEffects);
        batch.begin(viewCamera);
        viewModel.render(batch, environment);
        batch.end();
    }

    // ------------------------------------------------------------------ views

    private final class CharacterView {
        final Combatant combatant;
        final ModelInstance body, shadow, bubble, aura;
        final Node legL, legR;
        float phase;

        CharacterView(Combatant c) {
            combatant = c;
            body = new ModelInstance(characterModel);
            Color color = new Color(c.rgba);
            ((ColorAttribute) body.getMaterial("accent").get(ColorAttribute.Emissive)).color.set(color);
            ((ColorAttribute) body.getMaterial("visor").get(ColorAttribute.Emissive)).color.set(color).lerp(Color.WHITE, 0.5f);
            legL = body.getNode("legL");
            legR = body.getNode("legR");
            shadow = new ModelInstance(shadowModel);
            bubble = new ModelInstance(bubbleModel);
            aura = new ModelInstance(auraModel);
        }

        void collect(GameWorld world, float alpha, Array<ModelInstance> out, float dt) {
            Combatant c = combatant;
            if (!c.alive) return;
            Vector3 p = tmp.set(c.body.previousPosition).lerp(c.body.position, alpha);
            float speed = c.body.horizontalSpeed();
            phase += speed * dt * 1.6f;
            float swing = c.body.grounded ? MathUtils.sin(phase) * 38f * Math.min(1f, speed / 6f) : 20f;
            legL.rotation.set(Vector3.X, swing);
            legR.rotation.set(Vector3.X, c.body.grounded ? -swing : -10f);
            body.transform.setToTranslation(p).rotate(Vector3.Y, -c.yaw);
            body.calculateTransforms();
            out.add(body);

            float ground = world.physics.groundHeight(tmp2.set(p).add(0f, 0.5f, 0f), 8f, null);
            if (!Float.isNaN(ground)) {
                shadow.transform.setToTranslation(p.x, ground + 0.02f, p.z);
                out.add(shadow);
            }
            if (c.isProtected()) {
                float s = 1f + 0.05f * MathUtils.sin(time * 12f);
                bubble.transform.setToTranslation(p.x, p.y + 0.95f, p.z).scale(s, s, s);
                ((BlendingAttribute) bubble.materials.get(0).get(BlendingAttribute.Type)).opacity =
                        0.12f + 0.12f * (c.protection / Tuning.SPAWN_PROTECTION);
                out.add(bubble);
            }
            PowerUpType power = c.powerUp.active();
            if (power != null) {
                ((ColorAttribute) aura.materials.get(0).get(ColorAttribute.Emissive)).color.set(power.rgba);
                float s = 1f + 0.1f * MathUtils.sin(time * 6f);
                aura.transform.setToTranslation(p.x, p.y + 0.05f, p.z).scale(s, 1f, s);
                out.add(aura);
            }
        }
    }

    private final class PickupView {
        final Pickup pickup;
        final ModelInstance icon, pad, column;
        final Color color;

        PickupView(Pickup p) {
            pickup = p;
            String key = p.kind + ":" + p.powerUp;
            Model m = pickupModels.computeIfAbsent(key, k -> factory.pickup(p.kind, p.powerUp));
            icon = new ModelInstance(m);
            pad = new ModelInstance(padModel);
            column = new ModelInstance(columnModel);
            color = pickupColor(p);
            ((ColorAttribute) column.materials.get(0).get(ColorAttribute.Emissive)).color.set(color);
            pad.transform.setToTranslation(p.position.x, p.position.y + 0.03f, p.position.z);
            column.transform.setToTranslation(p.position.x, p.position.y + 1.5f, p.position.z);
        }

        void collect(Array<ModelInstance> out) {
            ColorAttribute ring = (ColorAttribute) pad.getMaterial("ring").get(ColorAttribute.Emissive);
            if (pickup.active) {
                ring.color.set(color);
                float bob = MathUtils.sin(time * 2.4f + pickup.id) * 0.12f;
                float scale = pickup.kind == PickupKind.POWER_UP ? 1.15f : 1f;
                icon.transform.setToTranslation(pickup.position.x, pickup.position.y + 1.05f + bob, pickup.position.z)
                        .rotate(Vector3.Y, time * 90f + pickup.id * 40f).scale(scale, scale, scale);
                out.add(icon);
                out.add(column);
            } else {
                float progress = 1f - pickup.timer / pickup.respawnTime;
                ring.color.set(color).mul(0.12f + 0.3f * progress);
            }
            out.add(pad);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        factory.dispose();
    }
}
