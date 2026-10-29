package com.alteredarena.tools;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Draws the window/app icon: a neon pill-shaped "A" mark on a dark rounded tile. */
public final class IconGenerator {
    public static void main(String[] args) throws IOException {
        File iconsDir = new File(args[0]);
        File logoDir = new File(args[1]);
        iconsDir.mkdirs();
        logoDir.mkdirs();
        for (int size : new int[]{16, 32, 64, 128}) ImageIO.write(draw(size), "png", new File(iconsDir, "icon" + size + ".png"));
        ImageIO.write(draw(512), "png", new File(logoDir, "logo.png"));
        System.out.println("Icons written");
    }

    static BufferedImage draw(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float k = s / 128f;
        g.setColor(new Color(0x14, 0x0C, 0x26));
        g.fillRoundRect(0, 0, s, s, Math.round(28 * k), Math.round(28 * k));
        g.setStroke(new BasicStroke(Math.max(1f, 10f * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D a = new Path2D.Float();
        a.moveTo(28 * k, 104 * k);
        a.lineTo(64 * k, 22 * k);
        a.lineTo(100 * k, 104 * k);
        g.setColor(new Color(0xFF, 0x2B, 0xD6));
        g.draw(a);
        g.setColor(new Color(0x22, 0xF0, 0xFF));
        g.drawLine(Math.round(44 * k), Math.round(76 * k), Math.round(84 * k), Math.round(76 * k));
        g.dispose();
        return img;
    }
}
