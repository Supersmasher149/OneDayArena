package com.alteredarena.ui;

import com.alteredarena.rules.PowerUpType;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;
import java.util.Map;

/** Fonts, generated UI textures, power-up icons and the Scene2D skin. */
public final class UiAssets implements Disposable {
    public static final Color MAGENTA = new Color(0xFF2BD6FF), CYAN = new Color(0x22F0FFFF), YELLOW = new Color(0xFFE03BFF),
            PANEL = new Color(0x0B0816E6), DIM = new Color(0x9A93B8FF), RED = new Color(0xFF3B5CFF), GREEN = new Color(0x4CFF8AFF);

    private static final String BOLD = "fonts/ChakraPetch-Bold.ttf", SEMI = "fonts/ChakraPetch-SemiBold.ttf";
    /** Fonts are rasterized at this multiple of their layout size, then scaled down, for crisp HiDPI text. */
    private static final float OVERSAMPLE = 2f;

    public final Skin skin = new Skin();
    public BitmapFont title, h1, h2, body, small, hud;
    public TextureRegion white, hitmarker, damageArc, vignette, ring;
    public final Map<PowerUpType, TextureRegion> powerIcons = new EnumMap<>(PowerUpType.class);
    public TextureRegion healthIcon, ammoIcon;
    private final Array<Texture> textures = new Array<>();

    public static void queue(AssetManager assets) {
        FileHandleResolver resolver = new InternalFileHandleResolver();
        assets.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        assets.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));
        font(assets, "title.ttf", BOLD, 84);
        font(assets, "h1.ttf", BOLD, 40);
        font(assets, "h2.ttf", SEMI, 26);
        font(assets, "body.ttf", SEMI, 20);
        font(assets, "small.ttf", SEMI, 15);
        font(assets, "hud.ttf", BOLD, 44);
    }

    private static void font(AssetManager assets, String name, String file, int size) {
        FreetypeFontLoader.FreeTypeFontLoaderParameter p = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        p.fontFileName = file;
        p.fontParameters.size = Math.round(size * OVERSAMPLE);
        p.fontParameters.minFilter = Texture.TextureFilter.Linear;
        p.fontParameters.magFilter = Texture.TextureFilter.Linear;
        p.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "•×→←";
        assets.load(name, BitmapFont.class, p);
    }

    public UiAssets(AssetManager assets) {
        title = scaled(assets.get("title.ttf", BitmapFont.class));
        h1 = scaled(assets.get("h1.ttf", BitmapFont.class));
        h2 = scaled(assets.get("h2.ttf", BitmapFont.class));
        body = scaled(assets.get("body.ttf", BitmapFont.class));
        small = scaled(assets.get("small.ttf", BitmapFont.class));
        hud = scaled(assets.get("hud.ttf", BitmapFont.class));
        buildTextures();
        buildSkin();
    }

    private static BitmapFont scaled(BitmapFont font) {
        font.getData().setScale(1f / OVERSAMPLE);
        font.setUseIntegerPositions(false);
        return font;
    }

    private TextureRegion region(Pixmap pixmap) {
        Texture t = new Texture(pixmap);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        textures.add(t);
        return new TextureRegion(t);
    }

    private void buildTextures() {
        Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        white = region(p);

        p = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        for (int i = 0; i < 4; i++) {
            float angle = 45f + i * 90f;
            for (float r = 14f; r < 30f; r += 0.5f) {
                int x = Math.round(32 + MathUtils.cosDeg(angle) * r), y = Math.round(32 + MathUtils.sinDeg(angle) * r);
                p.fillCircle(x, y, 3);
            }
        }
        hitmarker = region(p);

        p = new Pixmap(256, 64, Pixmap.Format.RGBA8888);
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 64; y++) {
                float dx = (x - 128f) / 128f;
                float dy = (y - 64f * 1.9f) / 64f;
                float dist = (float) Math.sqrt(dx * dx * 1.0f + dy * dy * 0.25f);
                float band = 1f - Math.abs(dist - 0.95f) / 0.08f;
                float fade = 1f - Math.abs(dx);
                float a = MathUtils.clamp(band, 0f, 1f) * MathUtils.clamp(fade * 1.6f, 0f, 1f);
                p.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
            }
        }
        damageArc = region(p);

        p = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 256; y++) {
                float dx = (x - 127.5f) / 127.5f, dy = (y - 127.5f) / 127.5f;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / 1.4142f;
                float a = MathUtils.clamp((d - 0.45f) / 0.55f, 0f, 1f);
                p.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a * a));
            }
        }
        vignette = region(p);

        p = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fillCircle(32, 32, 30);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        p.fillCircle(32, 32, 24);
        ring = region(p);

        for (PowerUpType type : PowerUpType.values()) powerIcons.put(type, region(Icons.draw(type)));
        healthIcon = region(Icons.health());
        ammoIcon = region(Icons.ammo());
    }

    private Drawable frame(int fill, int border, int borderWidth) {
        Pixmap p = new Pixmap(24, 24, Pixmap.Format.RGBA8888);
        p.setColor(new Color(border));
        p.fill();
        p.setBlending(Pixmap.Blending.None);
        p.setColor(new Color(fill));
        p.fillRectangle(borderWidth, borderWidth, 24 - 2 * borderWidth, 24 - 2 * borderWidth);
        TextureRegion r = region(p);
        NinePatchDrawable d = new NinePatchDrawable(new NinePatch(r, 6, 6, 6, 6));
        d.setLeftWidth(18);
        d.setRightWidth(18);
        d.setTopHeight(8);
        d.setBottomHeight(8);
        return d;
    }

    public Drawable tinted(Color color) {
        return new TextureRegionDrawable(white).tint(color);
    }

    private void buildSkin() {
        // Fonts belong to the AssetManager; they are not registered in the skin, which would dispose them twice.
        skin.add("default", new Label.LabelStyle(body, Color.WHITE));
        skin.add("title", new Label.LabelStyle(title, Color.WHITE));
        skin.add("h1", new Label.LabelStyle(h1, Color.WHITE));
        skin.add("h2", new Label.LabelStyle(h2, Color.WHITE));
        skin.add("small", new Label.LabelStyle(small, Color.WHITE));
        skin.add("hud", new Label.LabelStyle(hud, Color.WHITE));

        skin.add("panel", frame(0x0B0816E6, 0x3A2A66FF, 2), Drawable.class);
        skin.add("panel-bright", frame(0x0B0816F0, 0xFF2BD6FF, 2), Drawable.class);
        skin.add("white", new TextureRegionDrawable(white), Drawable.class);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.up = frame(0x120C24E6, 0x6A3AA8FF, 2);
        button.over = frame(0x1C1236F0, 0x22F0FFFF, 3);
        button.down = frame(0xFF2BD6FF, 0xFF2BD6FF, 2);
        button.font = h2;
        button.fontColor = Color.WHITE;
        button.overFontColor = CYAN;
        button.downFontColor = Color.BLACK;
        skin.add("default", button);

        Slider.SliderStyle slider = new Slider.SliderStyle();
        TextureRegionDrawable bar = new TextureRegionDrawable(white);
        slider.background = bar.tint(new Color(0x3A2A66FF));
        slider.background.setMinHeight(6);
        slider.knobBefore = new TextureRegionDrawable(white).tint(MAGENTA);
        slider.knobBefore.setMinHeight(6);
        Drawable knob = new TextureRegionDrawable(white).tint(CYAN);
        knob.setMinWidth(16);
        knob.setMinHeight(22);
        slider.knob = knob;
        skin.add("default-horizontal", slider);

        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle();
        check.checkboxOff = frame(0x120C24FF, 0x6A3AA8FF, 2);
        check.checkboxOff.setMinWidth(26);
        check.checkboxOff.setMinHeight(26);
        check.checkboxOn = frame(0xFF2BD6FF, 0x22F0FFFF, 3);
        check.checkboxOn.setMinWidth(26);
        check.checkboxOn.setMinHeight(26);
        check.checkboxOver = frame(0x1C1236FF, 0x22F0FFFF, 3);
        check.checkboxOver.setMinWidth(26);
        check.checkboxOver.setMinHeight(26);
        check.font = body;
        check.fontColor = Color.WHITE;
        skin.add("default", check);
    }

    public TextureRegion icon(PowerUpType type) { return powerIcons.get(type); }

    @Override
    public void dispose() {
        skin.dispose();
        for (Texture t : textures) t.dispose();
        textures.clear();
    }
}
