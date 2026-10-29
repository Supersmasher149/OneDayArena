package com.alteredarena;

import com.alteredarena.settings.Settings;
import com.alteredarena.settings.SettingsStore;
import com.badlogic.gdx.Preferences;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreTest {
    @Test
    void settingsRoundTripAndClamp() {
        MapPreferences prefs = new MapPreferences();
        Settings s = new SettingsStore(prefs).load();
        assertEquals(0.8f, s.masterVolume(), 1e-6);
        s.setMasterVolume(0.35f);
        s.setSensitivity(2.2f);
        s.setInvertY(true);
        s.setFullscreen(true);
        s.setReducedEffects(true);
        new SettingsStore(prefs).save(s);
        assertTrue(prefs.flushed);

        Settings loaded = new SettingsStore(prefs).load();
        assertEquals(0.35f, loaded.masterVolume(), 1e-6);
        assertEquals(2.2f, loaded.sensitivity(), 1e-6);
        assertTrue(loaded.invertY() && loaded.fullscreen() && loaded.reducedEffects());

        prefs.putFloat("sensitivity", 99f);
        prefs.putFloat("masterVolume", Float.NaN);
        Settings clamped = new SettingsStore(prefs).load();
        assertEquals(Settings.MAX_SENSITIVITY, clamped.sensitivity());
        assertEquals(0f, clamped.masterVolume());
    }

    /** In-memory Preferences so the test needs no libGDX backend. */
    static final class MapPreferences implements Preferences {
        final Map<String, Object> map = new HashMap<>();
        boolean flushed;
        public Preferences putBoolean(String k, boolean v) { map.put(k, v); return this; }
        public Preferences putInteger(String k, int v) { map.put(k, v); return this; }
        public Preferences putLong(String k, long v) { map.put(k, v); return this; }
        public Preferences putFloat(String k, float v) { map.put(k, v); return this; }
        public Preferences putString(String k, String v) { map.put(k, v); return this; }
        public Preferences put(Map<String, ?> vals) { map.putAll(vals); return this; }
        public boolean getBoolean(String k) { return getBoolean(k, false); }
        public int getInteger(String k) { return getInteger(k, 0); }
        public long getLong(String k) { return getLong(k, 0); }
        public float getFloat(String k) { return getFloat(k, 0); }
        public String getString(String k) { return getString(k, ""); }
        public boolean getBoolean(String k, boolean d) { return (Boolean) map.getOrDefault(k, d); }
        public int getInteger(String k, int d) { return (Integer) map.getOrDefault(k, d); }
        public long getLong(String k, long d) { return (Long) map.getOrDefault(k, d); }
        public float getFloat(String k, float d) { return (Float) map.getOrDefault(k, d); }
        public String getString(String k, String d) { return (String) map.getOrDefault(k, d); }
        public Map<String, ?> get() { return map; }
        public boolean contains(String k) { return map.containsKey(k); }
        public void clear() { map.clear(); }
        public void remove(String k) { map.remove(k); }
        public void flush() { flushed = true; }
    }
}
