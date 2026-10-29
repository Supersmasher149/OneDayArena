package com.alteredarena.render;

import com.alteredarena.level.Block;
import com.alteredarena.level.LevelData;
import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.rules.PowerUpType;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.ConeShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** Builds every 3D model from code: arena geometry (from {@link LevelData}), characters, pickups, weapons, effects. */
final class ModelFactory implements Disposable {
    static final long ATTR = Usage.Position | Usage.Normal | Usage.TextureCoordinates;
    static final long ATTR_PLAIN = Usage.Position | Usage.Normal;

    private final ModelBuilder mb = new ModelBuilder();
    private final Array<Model> models = new Array<>();
    final Textures textures = new Textures();

    private Model keep(Model m) {
        models.add(m);
        return m;
    }

    static Material solid(String id, int rgba) {
        return new Material(id, ColorAttribute.createDiffuse(new Color(rgba)));
    }

    static Material glow(String id, int rgba) {
        return new Material(id, ColorAttribute.createDiffuse(Color.BLACK), ColorAttribute.createEmissive(new Color(rgba)));
    }

    static Material glow(String id, Color color) {
        return new Material(id, ColorAttribute.createDiffuse(Color.BLACK), ColorAttribute.createEmissive(color));
    }

    static Material translucent(String id, int rgba, float opacity) {
        return new Material(id, ColorAttribute.createDiffuse(Color.BLACK), ColorAttribute.createEmissive(new Color(rgba)),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, opacity),
                IntAttribute.createCullFace(GL20.GL_NONE), new DepthTestAttribute(GL20.GL_LEQUAL, false));
    }

    private Material textured(String id, int rgba, Texture texture, float su, float sv) {
        TextureAttribute diffuse = TextureAttribute.createDiffuse(texture);
        diffuse.scaleU = su;
        diffuse.scaleV = sv;
        return new Material(id, ColorAttribute.createDiffuse(new Color(rgba)), diffuse);
    }

    // ------------------------------------------------------------------ arena

    Model level(LevelData level) {
        mb.begin();
        Vector3 t = new Vector3();
        Quaternion q = new Quaternion();
        for (int i = 0; i < level.blocks.size(); i++) {
            Block b = level.blocks.get(i);
            Node node = mb.node();
            node.id = "block" + i;
            b.transform.getTranslation(t);
            b.transform.getRotation(q, true);
            node.translation.set(t);
            node.rotation.set(q);
            float w = b.halfExtents.x * 2f, h = b.halfExtents.y * 2f, d = b.halfExtents.z * 2f;
            BoxShapeBuilder.build(mb.part("body" + i, GL20.GL_TRIANGLES, ATTR, blockMaterial(b, i)), w, h, d);
            if (b.trimRgba != 0) addTrim(b, i, w, h, d);
        }
        return keep(mb.end());
    }

    private Material blockMaterial(Block b, int i) {
        float w = b.halfExtents.x * 2f, h = b.halfExtents.y * 2f, d = b.halfExtents.z * 2f;
        String id = "m" + i;
        switch (b.style) {
            case FLOOR: {
                TextureAttribute diffuse = TextureAttribute.createDiffuse(textures.floor);
                diffuse.scaleU = w / 4f;
                diffuse.scaleV = d / 4f;
                TextureAttribute emissive = TextureAttribute.createEmissive(textures.floorGlow);
                emissive.scaleU = w / 4f;
                emissive.scaleV = d / 4f;
                return new Material(id, ColorAttribute.createDiffuse(new Color(b.rgba)), diffuse, emissive);
            }
            case CEILING: {
                TextureAttribute diffuse = TextureAttribute.createDiffuse(textures.floor);
                diffuse.scaleU = w / 6f;
                diffuse.scaleV = d / 6f;
                TextureAttribute emissive = TextureAttribute.createEmissive(textures.floorGlow);
                emissive.scaleU = w / 6f;
                emissive.scaleV = d / 6f;
                return new Material(id, ColorAttribute.createDiffuse(new Color(b.rgba)),
                        ColorAttribute.createEmissive(new Color(0xFF2BD6FF)), diffuse, emissive);
            }
            case WALL:
            case PILLAR:
                return textured(id, b.rgba, textures.panel, Math.max(w, d) / 4f, h / 4f);
            case PLATFORM:
                return textured(id, b.rgba, textures.plate, w / 3f, d / 3f);
            case RAMP:
                return textured(id, b.rgba, textures.chevron, 1f, d / 2f);
            case COVER:
                return textured(id, b.rgba, textures.crate, Math.max(1f, Math.round(Math.max(w, d) / 2f)), Math.max(1f, Math.round(h / 2f)));
            default:
                return solid(id, b.rgba);
        }
    }

    /** Neon strips along the top edges (and a baseboard on walls); decorative only. */
    private void addTrim(Block b, int i, float w, float h, float d) {
        MeshPartBuilder trim = mb.part("trim" + i, GL20.GL_TRIANGLES, ATTR_PLAIN, glow("t" + i, b.trimRgba));
        float s = 0.08f, top = h / 2f, o = 0.01f;
        if (b.style == Block.Style.RAMP) {
            BoxShapeBuilder.build(trim, -w / 2f, top - s / 2f + o, 0f, s, s, d);
            BoxShapeBuilder.build(trim, w / 2f, top - s / 2f + o, 0f, s, s, d);
            return;
        }
        BoxShapeBuilder.build(trim, 0f, top - s / 2f + o, -d / 2f, w + o * 2f, s, s);
        BoxShapeBuilder.build(trim, 0f, top - s / 2f + o, d / 2f, w + o * 2f, s, s);
        BoxShapeBuilder.build(trim, -w / 2f, top - s / 2f + o, 0f, s, s, d + o * 2f);
        BoxShapeBuilder.build(trim, w / 2f, top - s / 2f + o, 0f, s, s, d + o * 2f);
        if (b.style == Block.Style.WALL && h > 3f) {
            float y = -h / 2f + 0.35f;
            BoxShapeBuilder.build(trim, 0f, y, -d / 2f - o, w, 0.12f, s);
            BoxShapeBuilder.build(trim, 0f, y, d / 2f + o, w, 0.12f, s);
            BoxShapeBuilder.build(trim, -w / 2f - o, y, 0f, s, 0.12f, d);
            BoxShapeBuilder.build(trim, w / 2f + o, y, 0f, s, 0.12f, d);
        }
    }

    /** Rings around the reactor pillar plus the floating core and light beam above it. */
    Model landmarkRings() {
        mb.begin();
        for (int i = 0; i < 3; i++) {
            Node n = mb.node();
            n.id = "ring" + i;
            n.translation.set(0f, 3.4f + i * 2f, 0f);
            BoxShapeBuilder.build(mb.part("ring" + i, GL20.GL_TRIANGLES, ATTR_PLAIN,
                    glow("ring" + i, i % 2 == 0 ? 0xFF2BD6FF : 0x22F0FFFF)), 2.62f, 0.22f, 2.62f);
        }
        return keep(mb.end());
    }

    Model landmarkCore() {
        mb.begin();
        Node core = mb.node();
        core.id = "core";
        SphereShapeBuilder.build(mb.part("core", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("core", 0x22F0FFFF)), 2.2f, 3.2f, 2.2f, 4, 2);
        Node halo = mb.node();
        halo.id = "halo";
        MeshPartBuilder h = mb.part("halo", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("halo", 0xFF2BD6FF));
        for (int i = 0; i < 16; i++) {
            float a = i * 22.5f;
            Matrix4 m = new Matrix4().setToRotation(Vector3.Y, a).translate(0f, 0f, 2.4f).rotate(Vector3.X, 45f);
            h.setVertexTransform(m);
            BoxShapeBuilder.build(h, 0.35f, 0.12f, 0.35f);
        }
        h.setVertexTransform(null);
        return keep(mb.end());
    }

    Model beam() {
        mb.begin();
        CylinderShapeBuilder.build(mb.part("beam", GL20.GL_TRIANGLES, ATTR_PLAIN, translucent("beam", 0xFF2BD6FF, 0.18f)), 0.9f, 1f, 0.9f, 16);
        return keep(mb.end());
    }

    // ------------------------------------------------------------------ characters

    /** Low-poly fighter. Materials "accent" and "visor" are recolored per instance. Feet at the origin. */
    Model character() {
        mb.begin();
        Material suit = solid("suit", 0x2A2838FF);
        Material dark = solid("dark", 0x15141CFF);
        Material accent = glow("accent", 0xFFFFFFFF);
        Material visor = glow("visor", 0xFFFFFFFF);
        for (int side = -1; side <= 1; side += 2) {
            Node leg = mb.node();
            leg.id = side < 0 ? "legL" : "legR";
            leg.translation.set(0.15f * side, 0.82f, 0f);
            MeshPartBuilder lp = mb.part(leg.id, GL20.GL_TRIANGLES, ATTR_PLAIN, dark);
            BoxShapeBuilder.build(lp, 0f, -0.41f, 0f, 0.22f, 0.82f, 0.26f);
            MeshPartBuilder la = mb.part(leg.id + "a", GL20.GL_TRIANGLES, ATTR_PLAIN, accent);
            BoxShapeBuilder.build(la, 0f, -0.5f, -0.135f, 0.16f, 0.08f, 0.02f);
        }
        Node body = mb.node();
        body.id = "body";
        MeshPartBuilder bp = mb.part("torso", GL20.GL_TRIANGLES, ATTR_PLAIN, suit);
        BoxShapeBuilder.build(bp, 0f, 1.18f, 0f, 0.66f, 0.72f, 0.4f);
        BoxShapeBuilder.build(bp, 0f, 1.62f, 0f, 0.44f, 0.4f, 0.44f);
        MeshPartBuilder ba = mb.part("trim", GL20.GL_TRIANGLES, ATTR_PLAIN, accent);
        BoxShapeBuilder.build(ba, 0f, 1.3f, -0.205f, 0.5f, 0.1f, 0.02f);
        BoxShapeBuilder.build(ba, -0.36f, 1.42f, 0f, 0.1f, 0.2f, 0.42f);
        BoxShapeBuilder.build(ba, 0.36f, 1.42f, 0f, 0.1f, 0.2f, 0.42f);
        BoxShapeBuilder.build(ba, 0f, 0.86f, 0f, 0.68f, 0.06f, 0.42f);
        MeshPartBuilder vp = mb.part("visor", GL20.GL_TRIANGLES, ATTR_PLAIN, visor);
        BoxShapeBuilder.build(vp, 0f, 1.66f, -0.225f, 0.36f, 0.11f, 0.02f);
        MeshPartBuilder gun = mb.part("gun", GL20.GL_TRIANGLES, ATTR_PLAIN, dark);
        BoxShapeBuilder.build(gun, 0.3f, 1.2f, -0.36f, 0.12f, 0.14f, 0.62f);
        MeshPartBuilder tip = mb.part("guntip", GL20.GL_TRIANGLES, ATTR_PLAIN, accent);
        BoxShapeBuilder.build(tip, 0.3f, 1.2f, -0.68f, 0.08f, 0.08f, 0.04f);
        return keep(mb.end());
    }

    Model shadow() {
        mb.begin();
        CylinderShapeBuilder.build(mb.part("shadow", GL20.GL_TRIANGLES, ATTR_PLAIN, new Material("shadow",
                ColorAttribute.createDiffuse(Color.BLACK), new BlendingAttribute(0.45f))), 0.9f, 0.01f, 0.9f, 16);
        return keep(mb.end());
    }

    Model bubble() {
        mb.begin();
        SphereShapeBuilder.build(mb.part("bubble", GL20.GL_TRIANGLES, ATTR_PLAIN, translucent("bubble", 0x9FF6FFFF, 0.22f)), 1.5f, 2.3f, 1.5f, 16, 12);
        return keep(mb.end());
    }

    Model auraRing() {
        mb.begin();
        CylinderShapeBuilder.build(mb.part("aura", GL20.GL_TRIANGLES, ATTR_PLAIN, translucent("aura", 0xFFFFFFFF, 0.6f)), 1.1f, 0.05f, 1.1f, 20);
        return keep(mb.end());
    }

    // ------------------------------------------------------------------ pickups

    Model pickup(PickupKind kind, PowerUpType type) {
        mb.begin();
        if (kind == PickupKind.HEALTH) {
            MeshPartBuilder p = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", 0xFFFFFFFF));
            BoxShapeBuilder.build(p, 0.6f, 0.2f, 0.2f);
            BoxShapeBuilder.build(p, 0.2f, 0.6f, 0.2f);
            MeshPartBuilder r = mb.part("core", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("core", 0xFF3B5CFF));
            BoxShapeBuilder.build(r, 0.66f, 0.12f, 0.12f);
            BoxShapeBuilder.build(r, 0.12f, 0.66f, 0.12f);
        } else if (kind == PickupKind.AMMO) {
            MeshPartBuilder p = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", 0x4DA6FFFF));
            MeshPartBuilder tips = mb.part("tips", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("tips", 0xFFD36BFF));
            for (int i = -1; i <= 1; i++) {
                p.setVertexTransform(new Matrix4().setToTranslation(i * 0.2f, -0.05f, 0f));
                CylinderShapeBuilder.build(p, 0.14f, 0.45f, 0.14f, 10);
                tips.setVertexTransform(new Matrix4().setToTranslation(i * 0.2f, 0.27f, 0f));
                ConeShapeBuilder.build(tips, 0.14f, 0.18f, 0.14f, 10);
            }
            p.setVertexTransform(null);
            tips.setVertexTransform(null);
        } else {
            powerUpShape(type);
        }
        return keep(mb.end());
    }

    private void powerUpShape(PowerUpType type) {
        int c = type.rgba;
        switch (type) {
            case WEED: {
                MeshPartBuilder p = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", c));
                float[] angles = {-70, -45, -22, 0, 22, 45, 70};
                float[] lengths = {0.42f, 0.55f, 0.66f, 0.72f, 0.66f, 0.55f, 0.42f};
                for (int i = 0; i < angles.length; i++) {
                    Matrix4 m = new Matrix4().setToTranslation(0f, -0.3f, 0f).rotate(Vector3.Z, angles[i])
                            .translate(0f, lengths[i] / 2f, 0f).rotate(Vector3.Y, 45f);
                    p.setVertexTransform(m);
                    SphereShapeBuilder.build(p, 0.12f, lengths[i], 0.12f, 4, 4);
                }
                p.setVertexTransform(new Matrix4().setToTranslation(0f, -0.42f, 0f));
                BoxShapeBuilder.build(p, 0.05f, 0.25f, 0.05f);
                p.setVertexTransform(null);
                break;
            }
            case MOLLY: {
                MeshPartBuilder p = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", c));
                p.setVertexTransform(new Matrix4().setToTranslation(-0.14f, 0.1f, 0f));
                SphereShapeBuilder.build(p, 0.34f, 0.34f, 0.24f, 12, 8);
                p.setVertexTransform(new Matrix4().setToTranslation(0.14f, 0.1f, 0f));
                SphereShapeBuilder.build(p, 0.34f, 0.34f, 0.24f, 12, 8);
                p.setVertexTransform(new Matrix4().setToTranslation(0f, -0.06f, 0f).rotate(Vector3.Z, 45f));
                BoxShapeBuilder.build(p, 0.34f, 0.34f, 0.22f);
                p.setVertexTransform(null);
                break;
            }
            case ACID: {
                TextureAttribute tex = TextureAttribute.createEmissive(textures.acidTab);
                Material m = new Material("icon", ColorAttribute.createDiffuse(Color.BLACK), ColorAttribute.createEmissive(Color.WHITE), tex);
                BoxShapeBuilder.build(mb.part("icon", GL20.GL_TRIANGLES, ATTR, m), 0.62f, 0.62f, 0.05f);
                break;
            }
            case MUSHROOMS: {
                MeshPartBuilder cap = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", c));
                cap.setVertexTransform(new Matrix4().setToTranslation(0f, 0.12f, 0f));
                SphereShapeBuilder.build(cap, 0.8f, 0.5f, 0.8f, 14, 8, 0f, 360f, 0f, 90f);
                cap.setVertexTransform(null);
                MeshPartBuilder stem = mb.part("stem", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("stem", 0xF4ECFFFF));
                stem.setVertexTransform(new Matrix4().setToTranslation(0f, -0.08f, 0f));
                CylinderShapeBuilder.build(stem, 0.24f, 0.42f, 0.24f, 10);
                for (int i = 0; i < 5; i++) {
                    float a = i * 72f;
                    stem.setVertexTransform(new Matrix4().setToRotation(Vector3.Y, a).translate(0f, 0.27f, 0.22f));
                    SphereShapeBuilder.build(stem, 0.09f, 0.09f, 0.09f, 6, 4);
                }
                stem.setVertexTransform(null);
                break;
            }
            case COKE: {
                MeshPartBuilder p = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", c));
                SphereShapeBuilder.build(p, 0.36f, 0.8f, 0.36f, 4, 2);
                p.setVertexTransform(new Matrix4().setToTranslation(0.2f, -0.18f, 0.05f).rotate(Vector3.Z, -30f));
                SphereShapeBuilder.build(p, 0.2f, 0.42f, 0.2f, 4, 2);
                p.setVertexTransform(new Matrix4().setToTranslation(-0.18f, -0.2f, -0.05f).rotate(Vector3.Z, 35f));
                SphereShapeBuilder.build(p, 0.18f, 0.36f, 0.18f, 4, 2);
                p.setVertexTransform(null);
                break;
            }
            case ADDY: {
                Matrix4 tilt = new Matrix4().setToRotation(Vector3.Z, 55f);
                MeshPartBuilder a = mb.part("icon", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("icon", c));
                a.setVertexTransform(new Matrix4(tilt).translate(0f, 0.11f, 0f));
                CylinderShapeBuilder.build(a, 0.24f, 0.22f, 0.24f, 12);
                a.setVertexTransform(new Matrix4(tilt).translate(0f, 0.22f, 0f));
                SphereShapeBuilder.build(a, 0.24f, 0.24f, 0.24f, 12, 8);
                a.setVertexTransform(null);
                MeshPartBuilder b = mb.part("half", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("half", 0xFFFFFFFF));
                b.setVertexTransform(new Matrix4(tilt).translate(0f, -0.11f, 0f));
                CylinderShapeBuilder.build(b, 0.24f, 0.22f, 0.24f, 12);
                b.setVertexTransform(new Matrix4(tilt).translate(0f, -0.22f, 0f));
                SphereShapeBuilder.build(b, 0.24f, 0.24f, 0.24f, 12, 8);
                b.setVertexTransform(null);
                break;
            }
        }
    }

    Model pad() {
        mb.begin();
        CylinderShapeBuilder.build(mb.part("pad", GL20.GL_TRIANGLES, ATTR_PLAIN, solid("pad", 0x101018FF)), 1.5f, 0.06f, 1.5f, 24);
        Node ring = mb.node();
        ring.id = "ring";
        CylinderShapeBuilder.build(mb.part("ring", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("ring", 0xFFFFFFFF)), 1.62f, 0.04f, 1.62f, 24);
        return keep(mb.end());
    }

    Model column() {
        mb.begin();
        CylinderShapeBuilder.build(mb.part("column", GL20.GL_TRIANGLES, ATTR_PLAIN, translucent("column", 0xFFFFFFFF, 0.12f)), 1.1f, 3f, 1.1f, 16, 0f, 360f, false);
        return keep(mb.end());
    }

    // ------------------------------------------------------------------ effects

    Model glowCube() {
        mb.begin();
        BoxShapeBuilder.build(mb.part("cube", GL20.GL_TRIANGLES, ATTR_PLAIN, translucent("fx", 0xFFFFFFFF, 1f)), 1f, 1f, 1f);
        return keep(mb.end());
    }

    Model flash() {
        mb.begin();
        MeshPartBuilder p = mb.part("flash", GL20.GL_TRIANGLES, ATTR_PLAIN, translucent("fx", 0xFFFFFFFF, 1f));
        SphereShapeBuilder.build(p, 1f, 1f, 1f, 6, 4);
        p.setVertexTransform(new Matrix4().setToRotation(Vector3.Z, 45f));
        BoxShapeBuilder.build(p, 0.25f, 2.2f, 0.25f);
        p.setVertexTransform(new Matrix4().setToRotation(Vector3.Z, -45f));
        BoxShapeBuilder.build(p, 0.25f, 2.2f, 0.25f);
        p.setVertexTransform(null);
        return keep(mb.end());
    }

    // ------------------------------------------------------------------ first-person weapons (view space, -Z forward)

    Model pulseRifle() {
        mb.begin();
        MeshPartBuilder body = mb.part("body", GL20.GL_TRIANGLES, ATTR_PLAIN, solid("body", 0x2E2A40FF));
        BoxShapeBuilder.build(body, 0f, 0f, 0f, 0.09f, 0.11f, 0.5f);
        BoxShapeBuilder.build(body, 0f, -0.1f, 0.06f, 0.06f, 0.14f, 0.09f); // magazine
        BoxShapeBuilder.build(body, 0f, -0.08f, 0.2f, 0.06f, 0.12f, 0.07f); // grip
        BoxShapeBuilder.build(body, 0f, 0.08f, 0.02f, 0.04f, 0.04f, 0.18f); // sight rail
        MeshPartBuilder barrel = mb.part("barrel", GL20.GL_TRIANGLES, ATTR_PLAIN, solid("barrel", 0x16141EFF));
        barrel.setVertexTransform(new Matrix4().setToTranslation(0f, 0.01f, -0.36f).rotate(Vector3.X, 90f));
        CylinderShapeBuilder.build(barrel, 0.045f, 0.26f, 0.045f, 10);
        barrel.setVertexTransform(null);
        MeshPartBuilder glow = mb.part("glow", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("glow", 0x22F0FFFF));
        BoxShapeBuilder.build(glow, 0.047f, 0.02f, -0.02f, 0.01f, 0.025f, 0.38f);
        BoxShapeBuilder.build(glow, -0.047f, 0.02f, -0.02f, 0.01f, 0.025f, 0.38f);
        BoxShapeBuilder.build(glow, 0f, 0.105f, 0.02f, 0.02f, 0.015f, 0.05f);
        for (int i = 0; i < 3; i++) BoxShapeBuilder.build(glow, 0f, 0.01f, -0.3f - i * 0.06f, 0.06f, 0.06f, 0.012f);
        return keep(mb.end());
    }

    Model scattergun() {
        mb.begin();
        MeshPartBuilder body = mb.part("body", GL20.GL_TRIANGLES, ATTR_PLAIN, solid("body", 0x3A2A30FF));
        BoxShapeBuilder.build(body, 0f, 0f, 0.05f, 0.15f, 0.13f, 0.38f);
        BoxShapeBuilder.build(body, 0f, -0.09f, 0.2f, 0.07f, 0.14f, 0.08f);
        BoxShapeBuilder.build(body, 0f, -0.07f, -0.2f, 0.11f, 0.06f, 0.2f); // pump
        MeshPartBuilder barrel = mb.part("barrel", GL20.GL_TRIANGLES, ATTR_PLAIN, solid("barrel", 0x16141EFF));
        for (int side = -1; side <= 1; side += 2) {
            barrel.setVertexTransform(new Matrix4().setToTranslation(0.037f * side, 0.02f, -0.3f).rotate(Vector3.X, 90f));
            CylinderShapeBuilder.build(barrel, 0.065f, 0.38f, 0.065f, 10);
        }
        barrel.setVertexTransform(null);
        MeshPartBuilder glow = mb.part("glow", GL20.GL_TRIANGLES, ATTR_PLAIN, glow("glow", 0xFF8A1FFF));
        BoxShapeBuilder.build(glow, 0f, 0.068f, 0.05f, 0.1f, 0.012f, 0.3f);
        BoxShapeBuilder.build(glow, 0.077f, 0f, 0.05f, 0.01f, 0.05f, 0.3f);
        BoxShapeBuilder.build(glow, -0.077f, 0f, 0.05f, 0.01f, 0.05f, 0.3f);
        BoxShapeBuilder.build(glow, 0f, 0.02f, -0.47f, 0.15f, 0.08f, 0.015f);
        return keep(mb.end());
    }

    @Override
    public void dispose() {
        for (Model m : models) m.dispose();
        models.clear();
        textures.dispose();
    }
}
