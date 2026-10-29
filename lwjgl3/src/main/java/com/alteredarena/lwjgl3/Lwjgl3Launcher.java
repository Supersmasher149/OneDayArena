package com.alteredarena.lwjgl3;

import com.alteredarena.AlteredArenaGame;
import com.alteredarena.LaunchOptions;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

/** Desktop entry point. */
public final class Lwjgl3Launcher {
    public static void main(String[] args) {
        // macOS needs -XstartOnFirstThread; this relaunches the JVM with it when missing.
        if (StartupHelper.startNewJvmIfRequired(args)) return;
        LaunchOptions options = LaunchOptions.parse(args);
        AlteredArenaGame game = new AlteredArenaGame(options);
        new Lwjgl3Application(game, configuration(game));
    }

    private static Lwjgl3ApplicationConfiguration configuration(AlteredArenaGame game) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Altered Arena");
        config.useVsync(true);
        // Cap at the monitor refresh rate (+1 so vsync, not the limiter, does the pacing).
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        config.setIdleFPS(30);
        config.setWindowedMode(1280, 720);
        config.setWindowSizeLimits(960, 540, -1, -1);
        config.setAudioConfig(32, 512, 9);
        config.setWindowIcon("icons/icon128.png", "icons/icon64.png", "icons/icon32.png", "icons/icon16.png");
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                if (Gdx.app != null) game.onFocusLost();
            }

            @Override
            public void iconified(boolean isIconified) {
                if (isIconified && Gdx.app != null) game.onFocusLost();
            }
        });
        return config;
    }
}
