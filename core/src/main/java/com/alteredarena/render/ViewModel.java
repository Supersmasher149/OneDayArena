package com.alteredarena.render;

import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponState;
import com.alteredarena.rules.WeaponType;
import com.alteredarena.sim.Combatant;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** First-person weapon: bob, recoil kick, reload dip, switch lower/raise and muzzle flash. */
final class ViewModel {
    private final ModelInstance rifle, scatter, flash;
    private final ColorAttribute flashColor;
    private ModelInstance current;
    private float kick, flashLife, bobPhase, landDip, flashRoll;
    private final Vector3 muzzle = new Vector3();

    ViewModel(Model rifleModel, Model scatterModel, Model flashModel) {
        rifle = new ModelInstance(rifleModel);
        scatter = new ModelInstance(scatterModel);
        flash = new ModelInstance(flashModel);
        flashColor = (ColorAttribute) flash.materials.get(0).get(ColorAttribute.Emissive);
    }

    void reset() {
        kick = flashLife = landDip = 0f;
    }

    void onShot(WeaponType weapon, boolean reducedEffects) {
        kick = Math.min(1.6f, kick + (weapon == WeaponType.SCATTERGUN ? 1.2f : 0.35f));
        flashLife = 0.05f;
        flashRoll = MathUtils.random(360f);
        flashColor.color.set(weapon == WeaponType.SCATTERGUN ? new Color(0xFFB04DFF) : new Color(0x8FF8FFFF));
    }

    void onLand(float speed) {
        landDip = Math.min(1f, speed / 14f);
    }

    void update(float dt) {
        kick *= (float) Math.exp(-11f * dt);
        landDip *= (float) Math.exp(-8f * dt);
        flashLife -= dt;
    }

    void prepare(Combatant player, float speed, boolean reducedEffects) {
        WeaponState weapon = player.weapon();
        boolean scattergun = weapon.type == WeaponType.SCATTERGUN;
        current = scattergun ? scatter : rifle;

        float moving = player.body.grounded ? Math.min(1f, speed / Tuning.MOVE_SPEED) : 0f;
        bobPhase += speed * 0.016f * 1.4f;
        float bobScale = reducedEffects ? 0.4f : 1f;
        float bobX = MathUtils.sin(bobPhase) * 0.012f * moving * bobScale;
        float bobY = -Math.abs(MathUtils.cos(bobPhase)) * 0.014f * moving * bobScale;

        float lower = 0f, tilt = 0f;
        if (player.switchTimer > 0f) lower = player.switchTimer / Tuning.WEAPON_SWITCH_TIME;
        if (weapon.isReloading()) {
            float r = MathUtils.sin(weapon.reloadProgress() * MathUtils.PI);
            lower = Math.max(lower, r * 0.55f);
            tilt = r * 35f;
        }

        float x = 0.24f + bobX, y = -0.23f + bobY - lower * 0.3f - landDip * 0.04f, z = -0.62f + kick * 0.05f;
        current.transform.setToTranslation(x, y, z)
                .rotate(Vector3.X, kick * (scattergun ? 7f : 3f) - lower * 25f)
                .rotate(Vector3.Z, tilt)
                .rotate(Vector3.Y, -3f);
        muzzle.set(0f, scattergun ? 0.02f : 0.01f, scattergun ? -0.52f : -0.52f).mul(current.transform);
    }

    void render(ModelBatch batch, Environment environment) {
        batch.render(current, environment);
        if (flashLife > 0f) {
            float s = current == scatter ? 0.11f : 0.065f;
            flash.transform.setToTranslation(muzzle).rotate(Vector3.Z, flashRoll).scale(s, s, s);
            batch.render(flash);
        }
    }
}
