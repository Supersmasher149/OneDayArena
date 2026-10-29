package com.alteredarena;

import com.alteredarena.audio.AudioManager;
import com.alteredarena.level.ArenaLayout;
import com.alteredarena.level.LevelData;
import com.alteredarena.net.NetSession;
import com.alteredarena.render.WorldRenderer;
import com.alteredarena.screens.GameScreen;
import com.alteredarena.screens.MenuScreen;
import com.alteredarena.settings.Settings;
import com.alteredarena.settings.SettingsStore;
import com.alteredarena.sim.Physics;
import com.alteredarena.ui.UiAssets;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;

/** Application root: owns long-lived resources and switches between the menu and the match. */
public final class AlteredArenaGame extends Game {
    public final LaunchOptions options;
    public AssetManager assets;
    public UiAssets ui;
    public AudioManager audio;
    public Settings settings;
    public LevelData level;
    public WorldRenderer renderer;
    /** The online game in progress (lobby or match), or null when playing solo. */
    public NetSession session;
    private SettingsStore store;

    public AlteredArenaGame(LaunchOptions options) {
        this.options = options;
    }

    @Override
    public void create() {
        Physics.loadNatives();
        store = new SettingsStore(Gdx.app.getPreferences(SettingsStore.PREFERENCES_NAME));
        settings = store.load();
        Gdx.app.log("Settings", String.format("loaded volume=%.2f sensitivity=%.2f invertY=%b fullscreen=%b reducedEffects=%b",
                settings.masterVolume(), settings.sensitivity(), settings.invertY(), settings.fullscreen(), settings.reducedEffects()));
        if (!options.smoke) applyDisplayMode();

        assets = new AssetManager();
        UiAssets.queue(assets);
        AudioManager.queue(assets);
        assets.finishLoading();
        ui = new UiAssets(assets);
        audio = new AudioManager(assets, settings);
        level = ArenaLayout.build();
        renderer = new WorldRenderer(level);

        if (options.autoplay) startMatch();
        else showMenu();
    }

    public void saveSettings() {
        store.save(settings);
    }

    public void applyDisplayMode() {
        if (settings.fullscreen()) {
            if (!Gdx.graphics.isFullscreen()) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(1280, 720);
        }
    }

    public void startMatch() {
        switchTo(new GameScreen(this));
    }

    /** Returns to the main menu, leaving any online game. */
    public void showMenu() {
        showMenu(null);
    }

    /** @param notice shown on the menu, e.g. why an online game ended; may be null */
    public void showMenu(String notice) {
        endSession();
        switchTo(new MenuScreen(this, notice));
    }

    public void endSession() {
        if (session != null) session.close();
        session = null;
    }

    /** Switches screens after the current frame so a screen is never disposed while it is running. */
    private void switchTo(Screen next) {
        Gdx.app.postRunnable(() -> {
            Screen old = getScreen();
            setScreen(next);
            if (old != null) old.dispose();
        });
    }

    /** Window lost focus or was minimized: pause gameplay and release the mouse. */
    public void onFocusLost() {
        if (getScreen() instanceof GameScreen) ((GameScreen) getScreen()).pauseFromFocusLoss();
    }

    @Override
    public void pause() {
        onFocusLost();
    }

    @Override
    public void dispose() {
        endSession();
        Screen current = getScreen();
        if (current != null) current.dispose();
        if (renderer != null) renderer.dispose();
        if (ui != null) ui.dispose();
        if (assets != null) assets.dispose();
    }
}
