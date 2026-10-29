package com.alteredarena.screens;

import com.alteredarena.AlteredArenaGame;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.ui.Menus;
import com.alteredarena.ui.SettingsPanel;
import com.alteredarena.ui.UiAssets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/** Main menu over a live bot match seen from an orbiting camera. */
public final class MenuScreen extends ScreenAdapter {
    private final AlteredArenaGame game;
    private final Stage stage;
    private final Table mainColumn;
    private final Table legend;
    private final SettingsPanel settingsPanel;
    private final Table controls;
    private final Table content = new Table();
    private GameWorld attract;
    private float accumulator, time;

    public MenuScreen(AlteredArenaGame game) {
        this.game = game;
        UiAssets ui = game.ui;
        stage = new Stage(new ExtendViewport(1280, 720));

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        mainColumn = new Table();
        mainColumn.setBackground(ui.skin.getDrawable("panel"));
        mainColumn.pad(30, 40, 30, 40);
        Label title1 = Menus.title(ui, "ALTERED", UiAssets.MAGENTA);
        Label title2 = Menus.title(ui, "ARENA", UiAssets.CYAN);
        mainColumn.add(title1).left().row();
        mainColumn.add(title2).left().padTop(-24).row();
        Label tagline = new Label("NEON UNDERGROUND DEATHMATCH  •  v0.1", ui.skin, "small");
        tagline.setColor(UiAssets.DIM);
        mainColumn.add(tagline).left().padBottom(26).row();
        mainColumn.defaults().width(300).height(56).padBottom(12).left();
        mainColumn.add(Menus.button(ui, game.audio, "PLAY", game::startMatch)).row();
        mainColumn.add(Menus.button(ui, game.audio, "SETTINGS", this::showSettings)).row();
        mainColumn.add(Menus.button(ui, game.audio, "CONTROLS", this::showControls)).row();
        mainColumn.add(Menus.button(ui, game.audio, "QUIT", () -> Gdx.app.exit())).row();
        Label footer = new Label("You vs 4 bots  •  first to 15 or best after 5:00", ui.skin, "small");
        footer.setColor(UiAssets.DIM);
        mainColumn.add(footer).height(24).padTop(8).row();

        legend = new Table();
        legend.setBackground(ui.skin.getDrawable("panel"));
        legend.pad(20, 24, 20, 24);
        Label legendTitle = new Label("POWER-UPS", ui.skin, "h2");
        legendTitle.setColor(UiAssets.YELLOW);
        legend.add(legendTitle).colspan(2).left().padBottom(10).row();
        for (PowerUpType t : PowerUpType.values()) {
            legend.add(new Image(ui.icon(t))).size(40).padRight(12).padBottom(8);
            Table text = new Table();
            Label name = new Label(t.label + "  " + (int) t.duration + "s", ui.skin, "h2");
            name.setColor(new Color(t.rgba));
            text.add(name).left().row();
            Label desc = new Label(t.description, ui.skin, "small");
            desc.setColor(UiAssets.DIM);
            text.add(desc).left();
            legend.add(text).left().padBottom(8).row();
        }
        Label note = new Label("Fictional arcade abilities. One at a time.", ui.skin, "small");
        note.setColor(UiAssets.DIM);
        legend.add(note).colspan(2).left().padTop(6);

        settingsPanel = new SettingsPanel(ui, game.settings, new SettingsPanel.Listener() {
            @Override
            public void settingsChanged(boolean displayModeChanged) {
                game.saveSettings();
                if (displayModeChanged) game.applyDisplayMode();
            }

            @Override
            public void back() {
                showMain();
            }
        });
        controls = Menus.controlsTable(ui, this::showMain, game.audio);

        root.add(content).expand().fill().pad(40);
        showMain();
    }

    private void showMain() {
        content.clearChildren();
        content.add(mainColumn).expand().left().bottom();
        content.add(legend).expand().right().bottom();
    }

    private void showSettings() { show(settingsPanel); }

    private void showControls() { show(controls); }

    private void show(Table panel) {
        if (panel == settingsPanel) settingsPanel.sync();
        content.clearChildren();
        content.add(panel).expand().center();
    }

    @Override
    public void show() {
        attract = new GameWorld(game.level, MathUtils.random(Long.MAX_VALUE), Tuning.BOT_COUNT, true);
        game.renderer.bind(attract);
        Gdx.input.setCursorCatched(false);
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    showMain();
                    return true;
                }
                return false;
            }
        }));
    }

    @Override
    public void render(float delta) {
        float frame = Math.min(delta, Tuning.MAX_FRAME_TIME);
        time += frame;
        accumulator += frame;
        int steps = 0;
        while (accumulator >= Tuning.FIXED_DT && steps < Tuning.MAX_STEPS_PER_FRAME) {
            attract.step(Tuning.FIXED_DT);
            accumulator -= Tuning.FIXED_DT;
            steps++;
        }
        if (!attract.match.isRunning()) {
            attract.dispose();
            attract = new GameWorld(game.level, MathUtils.random(Long.MAX_VALUE), Tuning.BOT_COUNT, true);
            game.renderer.bind(attract);
        }
        for (GameEvent e : attract.events) game.renderer.onEvent(e, attract, -1, game.settings.reducedEffects());
        attract.events.clear();

        float angle = time * 0.06f;
        game.renderer.camera.position.set(MathUtils.cos(angle) * 30f, 15f, MathUtils.sin(angle) * 30f);
        game.renderer.camera.lookAt(0f, 2.5f, 0f);
        game.renderer.camera.up.set(0f, 1f, 0f);
        game.renderer.camera.fieldOfView = 60f;
        game.renderer.camera.update();
        game.renderer.update(frame);
        game.renderer.render(attract, accumulator / Tuning.FIXED_DT, -1);

        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        game.renderer.resize(width, height);
    }

    @Override
    public void hide() {
        if (attract != null) {
            attract.dispose();
            attract = null;
        }
    }

    @Override
    public void dispose() {
        hide();
        stage.dispose();
    }
}
