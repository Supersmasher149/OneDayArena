package com.alteredarena.settings;

import com.badlogic.gdx.Preferences;

/** Persists {@link Settings} in libGDX preferences (~/.prefs/AlteredArena on desktop). */
public final class SettingsStore {
    public static final String PREFERENCES_NAME = "AlteredArena";
    private final Preferences prefs;

    public SettingsStore(Preferences prefs) {
        this.prefs = prefs;
    }

    public Settings load() {
        Settings s = new Settings();
        s.setMasterVolume(prefs.getFloat("masterVolume", s.masterVolume()));
        s.setSensitivity(prefs.getFloat("sensitivity", s.sensitivity()));
        s.setInvertY(prefs.getBoolean("invertY", s.invertY()));
        s.setFullscreen(prefs.getBoolean("fullscreen", s.fullscreen()));
        s.setReducedEffects(prefs.getBoolean("reducedEffects", s.reducedEffects()));
        return s;
    }

    public void save(Settings s) {
        prefs.putFloat("masterVolume", s.masterVolume());
        prefs.putFloat("sensitivity", s.sensitivity());
        prefs.putBoolean("invertY", s.invertY());
        prefs.putBoolean("fullscreen", s.fullscreen());
        prefs.putBoolean("reducedEffects", s.reducedEffects());
        prefs.flush();
    }
}
