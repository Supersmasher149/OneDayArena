package com.alteredarena.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** Procedural surface textures (all generated at startup, no image files). */
final class Textures implements Disposable {
    final Texture floor, floorGlow, panel, plate, chevron, crate, acidTab;
    private final Array<Texture> all = new Array<>();
    private final Array<Pixmap> pixmaps = new Array<>();

    Textures() {
        floor = make(floorPixmap(false));
        floorGlow = make(floorPixmap(true));
        panel = make(panelPixmap());
        plate = make(platePixmap());
        chevron = make(chevronPixmap());
        crate = make(cratePixmap());
        acidTab = make(acidPixmap());
    }

    private Texture make(Pixmap p) {
        // CPU mipmap generation may free the pixmap itself, so it is released in dispose() only if still alive.
        Texture t = new Texture(new PixmapTextureData(p, null, true, false));
        pixmaps.add(p);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        all.add(t);
        return t;
    }

    private static Pixmap floorPixmap(boolean glow) {
        int s = 256;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setColor(glow ? Color.BLACK : new Color(0.55f, 0.55f, 0.7f, 1f));
        p.fill();
        if (!glow) {
            // subtle tile variation
            p.setColor(new Color(0.48f, 0.48f, 0.62f, 1f));
            p.fillRectangle(0, 0, s / 2, s / 2);
            p.fillRectangle(s / 2, s / 2, s / 2, s / 2);
        }
        p.setColor(glow ? new Color(0.05f, 0.45f, 0.55f, 1f) : new Color(0.3f, 0.9f, 1f, 1f));
        p.fillRectangle(0, 0, s, 3);
        p.fillRectangle(0, 0, 3, s);
        p.setColor(glow ? new Color(0.03f, 0.18f, 0.25f, 1f) : new Color(0.4f, 0.5f, 0.75f, 1f));
        p.fillRectangle(0, s / 2, s, 1);
        p.fillRectangle(s / 2, 0, 1, s);
        return p;
    }

    private static Pixmap panelPixmap() {
        int s = 128;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0.85f, 0.85f, 0.95f, 1f));
        p.fill();
        p.setColor(new Color(0.62f, 0.6f, 0.75f, 1f));
        p.fillRectangle(0, 0, s, 4);
        p.fillRectangle(0, 0, 4, s);
        p.setColor(new Color(0.75f, 0.74f, 0.88f, 1f));
        p.fillRectangle(12, 12, s - 24, s - 24);
        p.setColor(new Color(0.92f, 0.9f, 1f, 1f));
        p.fillRectangle(12, 12, s - 24, 3);
        return p;
    }

    private static Pixmap platePixmap() {
        int s = 128;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0.8f, 0.82f, 0.95f, 1f));
        p.fill();
        p.setColor(new Color(0.6f, 0.62f, 0.8f, 1f));
        for (int i = -s; i < s; i += 16) {
            for (int k = 0; k < 3; k++) p.drawLine(i + k, 0, i + k + s, s);
        }
        p.setColor(new Color(1f, 1f, 1f, 1f));
        p.fillRectangle(0, 0, s, 2);
        p.fillRectangle(0, 0, 2, s);
        return p;
    }

    private static Pixmap chevronPixmap() {
        int s = 128;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0.75f, 0.75f, 0.9f, 1f));
        p.fill();
        p.setColor(new Color(1f, 0.95f, 0.6f, 1f));
        for (int y = 0; y < s; y += 32) {
            for (int k = 0; k < 6; k++) {
                p.drawLine(0, y + k + 16, s / 2, y + k);
                p.drawLine(s / 2, y + k, s, y + k + 16);
            }
        }
        return p;
    }

    private static Pixmap cratePixmap() {
        int s = 128;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0.8f, 0.85f, 0.95f, 1f));
        p.fill();
        p.setColor(new Color(1f, 1f, 1f, 1f));
        p.fillRectangle(0, 0, s, 8);
        p.fillRectangle(0, s - 8, s, 8);
        p.fillRectangle(0, 0, 8, s);
        p.fillRectangle(s - 8, 0, 8, s);
        p.setColor(new Color(0.6f, 0.65f, 0.8f, 1f));
        for (int k = -4; k <= 4; k++) {
            p.drawLine(8, 8 + k, s - 8, s - 8 + k);
            p.drawLine(8, s - 8 + k, s - 8, 8 + k);
        }
        return p;
    }

    private static Pixmap acidPixmap() {
        int s = 128;
        Pixmap p = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0xF2FF3BFF));
        p.fill();
        p.setColor(new Color(0xFF2BD6FF));
        for (int i = 0; i < s; i += 32) p.fillRectangle(0, i, s, 4);
        p.setColor(new Color(0.08f, 0.04f, 0.12f, 1f));
        p.fillCircle(46, 50, 9);
        p.fillCircle(82, 50, 9);
        for (int a = 200; a <= 340; a += 3) {
            p.fillCircle(Math.round(64 + (float) Math.cos(Math.toRadians(a)) * 28), Math.round(70 - (float) Math.sin(Math.toRadians(a)) * 24), 4);
        }
        return p;
    }

    @Override
    public void dispose() {
        for (Texture t : all) t.dispose();
        all.clear();
        for (Pixmap p : pixmaps) if (!p.isDisposed()) p.dispose();
        pixmaps.clear();
    }
}
