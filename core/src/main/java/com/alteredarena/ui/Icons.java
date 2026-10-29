package com.alteredarena.ui;

import com.alteredarena.rules.PowerUpType;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;

/** Procedurally drawn 96x96 pickup icons, each with a distinct silhouette. */
final class Icons {
    static final int S = 96;

    private Icons() {}

    static Pixmap draw(PowerUpType type) {
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        Color c = new Color(type.rgba);
        p.setColor(c);
        switch (type) {
            case WEED: {
                float[] angles = {-72, -48, -24, 0, 24, 48, 72};
                float[] lengths = {26, 34, 40, 44, 40, 34, 26};
                for (int i = 0; i < angles.length; i++) leaf(p, 48, 66, angles[i], lengths[i], 6.5f);
                p.fillRectangle(46, 64, 4, 26);
                break;
            }
            case MOLLY: {
                p.fillCircle(33, 38, 18);
                p.fillCircle(63, 38, 18);
                p.fillTriangle(16, 44, 80, 44, 48, 84);
                p.fillTriangle(20, 40, 76, 40, 48, 60);
                break;
            }
            case ACID: {
                // Blotter tab with perforations and a smiley.
                p.fillRectangle(14, 14, 68, 68);
                p.setBlending(Pixmap.Blending.None);
                p.setColor(0, 0, 0, 0);
                for (int i = 18; i < 80; i += 8) {
                    p.fillRectangle(i, 14, 3, 3);
                    p.fillRectangle(i, 79, 3, 3);
                }
                p.setColor(0.1f, 0.05f, 0.15f, 1f);
                p.fillCircle(37, 40, 6);
                p.fillCircle(59, 40, 6);
                for (int a = 200; a <= 340; a += 4) {
                    p.fillCircle(Math.round(48 + MathUtils.cosDeg(a) * 18), Math.round(52 - MathUtils.sinDeg(a) * 16), 3);
                }
                break;
            }
            case MUSHROOMS: {
                p.fillCircle(48, 46, 34);
                p.setBlending(Pixmap.Blending.None);
                p.setColor(0, 0, 0, 0);
                p.fillRectangle(0, 50, S, S - 50);
                p.setBlending(Pixmap.Blending.SourceOver);
                p.setColor(Color.WHITE);
                p.fillRectangle(38, 50, 20, 34);
                p.fillCircle(48, 84, 10);
                p.fillCircle(34, 32, 6);
                p.fillCircle(56, 24, 5);
                p.fillCircle(64, 40, 5);
                break;
            }
            case COKE: {
                // Faceted crystal
                p.fillTriangle(48, 8, 22, 40, 74, 40);
                p.fillTriangle(22, 40, 74, 40, 48, 90);
                p.setColor(Color.WHITE);
                p.fillTriangle(48, 8, 40, 40, 56, 40);
                p.setColor(c.cpy().mul(0.75f, 0.85f, 0.95f, 1f));
                p.fillTriangle(48, 90, 56, 40, 74, 40);
                break;
            }
            case ADDY: {
                pill(p, 26, 70, 70, 26, 15, c, Color.WHITE);
                break;
            }
        }
        return p;
    }

    static Pixmap health() {
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fillRectangle(36, 14, 24, 68);
        p.fillRectangle(14, 36, 68, 24);
        return p;
    }

    static Pixmap ammo() {
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            int x = 22 + i * 20;
            p.fillRectangle(x, 34, 12, 48);
            p.fillTriangle(x, 34, x + 12, 34, x + 6, 16);
        }
        return p;
    }

    private static void leaf(Pixmap p, float x, float y, float angle, float length, float width) {
        float dx = MathUtils.sinDeg(angle), dy = -MathUtils.cosDeg(angle);
        float tipX = x + dx * length, tipY = y + dy * length;
        float midX = x + dx * length * 0.45f, midY = y + dy * length * 0.45f;
        float px = -dy * width, py = dx * width;
        p.fillTriangle(Math.round(x), Math.round(y), Math.round(midX + px), Math.round(midY + py), Math.round(tipX), Math.round(tipY));
        p.fillTriangle(Math.round(x), Math.round(y), Math.round(midX - px), Math.round(midY - py), Math.round(tipX), Math.round(tipY));
    }

    private static void pill(Pixmap p, int x0, int y0, int x1, int y1, int r, Color a, Color b) {
        int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        segment(p, x0, y0, mx, my, r, a);
        segment(p, mx, my, x1, y1, r, b);
    }

    private static void segment(Pixmap p, int x0, int y0, int x1, int y1, int r, Color color) {
        p.setColor(color);
        int steps = 40;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            p.fillCircle(Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t), r);
        }
    }
}
