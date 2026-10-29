package com.alteredarena.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Pooled tracers, spark particles and muzzle flashes. Nothing is allocated per frame after startup. */
final class Effects {
    private static final int TRACERS = 128, PARTICLES = 360, FLASHES = 24;

    private static final class Fx {
        final ModelInstance instance;
        final ColorAttribute emissive;
        final BlendingAttribute blend;
        final Vector3 a = new Vector3(), b = new Vector3(), velocity = new Vector3();
        final Color color = new Color();
        float life, maxLife, size;

        Fx(Model model) {
            instance = new ModelInstance(model);
            emissive = (ColorAttribute) instance.materials.get(0).get(ColorAttribute.Emissive);
            blend = (BlendingAttribute) instance.materials.get(0).get(BlendingAttribute.Type);
        }
    }

    private final Fx[] tracers = new Fx[TRACERS], particles = new Fx[PARTICLES], flashes = new Fx[FLASHES];
    private int nextTracer, nextParticle, nextFlash;
    private final Vector3 tmp = new Vector3();

    Effects(Model cube, Model flash) {
        for (int i = 0; i < TRACERS; i++) tracers[i] = new Fx(cube);
        for (int i = 0; i < PARTICLES; i++) particles[i] = new Fx(cube);
        for (int i = 0; i < FLASHES; i++) flashes[i] = new Fx(flash);
    }

    void clear() {
        for (Fx f : tracers) f.life = 0f;
        for (Fx f : particles) f.life = 0f;
        for (Fx f : flashes) f.life = 0f;
    }

    void tracer(Vector3 from, Vector3 to, Color color, float width, float life) {
        Fx f = tracers[nextTracer = (nextTracer + 1) % TRACERS];
        f.a.set(from);
        f.b.set(to);
        f.color.set(color);
        f.size = width;
        f.life = f.maxLife = life;
    }

    void flash(Vector3 at, Color color, float size) {
        Fx f = flashes[nextFlash = (nextFlash + 1) % FLASHES];
        f.a.set(at);
        f.color.set(color);
        f.size = size;
        f.life = f.maxLife = 0.06f;
    }

    void burst(Vector3 at, Vector3 normal, Color color, int count, float speed, float size) {
        for (int i = 0; i < count; i++) {
            Fx f = particles[nextParticle = (nextParticle + 1) % PARTICLES];
            f.a.set(at);
            f.velocity.set(MathUtils.random(-1f, 1f), MathUtils.random(-1f, 1f), MathUtils.random(-1f, 1f)).nor()
                    .add(normal).nor().scl(speed * MathUtils.random(0.4f, 1f));
            f.color.set(color);
            f.size = size * MathUtils.random(0.6f, 1.3f);
            f.life = f.maxLife = MathUtils.random(0.25f, 0.55f);
        }
    }

    void update(float dt) {
        for (Fx f : tracers) if (f.life > 0f) f.life -= dt;
        for (Fx f : flashes) if (f.life > 0f) f.life -= dt;
        for (Fx f : particles) {
            if (f.life <= 0f) continue;
            f.life -= dt;
            f.velocity.y -= 12f * dt;
            f.velocity.scl(1f - 2f * dt);
            f.a.mulAdd(f.velocity, dt);
        }
    }

    void collect(Array<ModelInstance> out) {
        for (Fx f : tracers) {
            if (f.life <= 0f) continue;
            float k = f.life / f.maxLife;
            tmp.set(f.b).sub(f.a);
            float length = tmp.len();
            if (length < 0.05f) continue;
            tmp.scl(1f / length);
            f.instance.transform.setToTranslation(f.a).translate(tmp.x * length * 0.5f, tmp.y * length * 0.5f, tmp.z * length * 0.5f)
                    .rotate(Vector3.Z, tmp).scale(f.size, f.size, length);
            f.emissive.color.set(f.color);
            f.blend.opacity = k;
            out.add(f.instance);
        }
        for (Fx f : particles) {
            if (f.life <= 0f) continue;
            float k = f.life / f.maxLife;
            float s = f.size * (0.4f + 0.6f * k);
            f.instance.transform.setToTranslation(f.a).scale(s, s, s);
            f.emissive.color.set(f.color);
            f.blend.opacity = Math.min(1f, k * 1.5f);
            out.add(f.instance);
        }
        for (Fx f : flashes) {
            if (f.life <= 0f) continue;
            float s = f.size * (0.6f + 0.4f * f.life / f.maxLife);
            f.instance.transform.setToTranslation(f.a).rotate(Vector3.Z, MathUtils.random(360f)).scale(s, s, s);
            f.emissive.color.set(f.color);
            f.blend.opacity = 1f;
            out.add(f.instance);
        }
    }
}
