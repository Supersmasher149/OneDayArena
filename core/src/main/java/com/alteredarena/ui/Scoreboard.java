package com.alteredarena.ui;

import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.sim.Match;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

/** Standings table used by the Tab overlay and the results screen. */
public final class Scoreboard {
    public final Table table = new Table();
    private final UiAssets ui;

    public Scoreboard(UiAssets ui) {
        this.ui = ui;
        table.setBackground(ui.skin.getDrawable("panel"));
        table.pad(18, 28, 18, 28);
    }

    public void update(GameWorld world) {
        table.clearChildren();
        Match.Results results = world.match.results();
        header("#", Align.left);
        header("PLAYER", Align.left);
        header("KILLS", Align.right);
        header("DEATHS", Align.right);
        header("EFFECT", Align.left);
        table.row();
        Image rule = new Image(ui.white);
        rule.setColor(new Color(0x3A2A66FF));
        table.add(rule).colspan(5).height(2).fillX().padBottom(6).row();
        for (Match.Standing s : results.standings) {
            Combatant c = s.combatant;
            Color color = new Color(c.rgba);
            boolean you = !c.bot;
            cell(String.valueOf(s.rank), you ? Color.WHITE : UiAssets.DIM, Align.left);
            cell(c.name.toUpperCase(), color, Align.left);
            cell(String.valueOf(c.kills), Color.WHITE, Align.right);
            cell(String.valueOf(c.deaths), UiAssets.DIM, Align.right);
            String effect = !c.alive ? "DOWN" : c.powerUp.isActive() ? c.powerUp.active().label : "—";
            Color effectColor = c.powerUp.isActive() ? new Color(c.powerUp.active().rgba) : UiAssets.DIM;
            cell(effect, effectColor, Align.left);
            table.row();
        }
        table.pack();
    }

    private void header(String text, int align) {
        Label l = new Label(text, new Label.LabelStyle(ui.small, UiAssets.DIM));
        l.setAlignment(align);
        table.add(l).minWidth(text.equals("PLAYER") ? 220 : 90).pad(4, 10, 4, 10).align(align);
    }

    private void cell(String text, Color color, int align) {
        Label l = new Label(text, new Label.LabelStyle(ui.h2, color));
        l.setAlignment(align);
        table.add(l).pad(3, 10, 3, 10).align(align).fillX();
    }
}
