package com.alteredarena.ui;

import com.alteredarena.audio.AudioManager;
import com.alteredarena.audio.Sfx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/** Small helpers for building neon menu widgets. */
public final class Menus {
    private Menus() {}

    public static TextButton button(UiAssets ui, AudioManager audio, String text, Runnable action) {
        TextButton b = new TextButton(text, ui.skin);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.play(Sfx.UI_CLICK, 0.7f);
                action.run();
            }
        });
        b.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1 && fromActor != null && !fromActor.isDescendantOf(b)) audio.play(Sfx.UI_HOVER, 0.4f);
            }
        });
        return b;
    }

    public static Table controlsTable(UiAssets ui, Runnable back, AudioManager audio) {
        Table t = new Table();
        t.setBackground(ui.skin.getDrawable("panel"));
        t.pad(26, 36, 26, 36);
        Label title = new Label("CONTROLS", ui.skin, "h1");
        title.setColor(UiAssets.CYAN);
        t.add(title).colspan(2).left().padBottom(14).row();
        String[][] rows = {
                {"W A S D", "Move"},
                {"Mouse", "Look"},
                {"Space", "Jump (double jump with Mushrooms)"},
                {"Left mouse", "Fire"},
                {"R", "Reload"},
                {"1 / 2, Q, mouse wheel", "Pulse Rifle / Scattergun / swap"},
                {"Tab (hold)", "Scoreboard"},
                {"Esc", "Pause and release the mouse"},
        };
        for (String[] r : rows) {
            Label key = new Label(r[0], ui.skin, "h2");
            key.setColor(UiAssets.YELLOW);
            t.add(key).left().padRight(40).padBottom(6);
            t.add(new Label(r[1], ui.skin)).left().padBottom(6).row();
        }
        Label rules = new Label("Free-for-all: first to 15 eliminations or most after 5:00 wins. Equal top scores are a draw.\n"
                + "Power-ups last a few seconds; a new one replaces the old, the same one refreshes it.", ui.skin, "small");
        rules.setColor(UiAssets.DIM);
        t.add(rules).colspan(2).left().padTop(14).row();
        t.add(button(ui, audio, "BACK", back)).colspan(2).left().width(220).height(52).padTop(18);
        return t;
    }

    public static Label title(UiAssets ui, String text, Color color) {
        Label l = new Label(text, ui.skin, "title");
        l.setColor(color);
        return l;
    }
}
