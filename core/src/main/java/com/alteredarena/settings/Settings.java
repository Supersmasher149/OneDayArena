package com.alteredarena.settings;

/** Player options. Values are clamped on every write so a corrupt preferences file cannot break the game. */
public final class Settings {
    public static final float MIN_SENSITIVITY = 0.1f, MAX_SENSITIVITY = 3f;

    private float masterVolume = 0.8f;
    private float sensitivity = 1f;
    private boolean invertY;
    private boolean fullscreen;
    private boolean reducedEffects;

    public float masterVolume() { return masterVolume; }
    public float sensitivity() { return sensitivity; }
    public boolean invertY() { return invertY; }
    public boolean fullscreen() { return fullscreen; }
    public boolean reducedEffects() { return reducedEffects; }

    public void setMasterVolume(float v) { masterVolume = clamp(v, 0f, 1f); }
    public void setSensitivity(float v) { sensitivity = clamp(v, MIN_SENSITIVITY, MAX_SENSITIVITY); }
    public void setInvertY(boolean v) { invertY = v; }
    public void setFullscreen(boolean v) { fullscreen = v; }
    public void setReducedEffects(boolean v) { reducedEffects = v; }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
