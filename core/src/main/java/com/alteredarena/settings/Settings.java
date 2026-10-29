package com.alteredarena.settings;

import java.util.Locale;

/** Player options. Values are clamped on every write so a corrupt preferences file cannot break the game. */
public final class Settings {
    public static final float MIN_SENSITIVITY = 0.1f, MAX_SENSITIVITY = 3f;
    public static final int MAX_NAME_LENGTH = 14, MAX_ADDRESS_LENGTH = 80;
    public static final String DEFAULT_NAME = "PLAYER";

    private float masterVolume = 0.8f;
    private float sensitivity = 1f;
    private boolean invertY;
    private boolean fullscreen;
    private boolean reducedEffects;
    private String playerName = DEFAULT_NAME;
    private String lastAddress = "";

    public float masterVolume() { return masterVolume; }
    public float sensitivity() { return sensitivity; }
    public boolean invertY() { return invertY; }
    public boolean fullscreen() { return fullscreen; }
    public boolean reducedEffects() { return reducedEffects; }
    public String playerName() { return playerName; }
    public String lastAddress() { return lastAddress; }

    public void setMasterVolume(float v) { masterVolume = clamp(v, 0f, 1f); }
    public void setSensitivity(float v) { sensitivity = clamp(v, MIN_SENSITIVITY, MAX_SENSITIVITY); }
    public void setInvertY(boolean v) { invertY = v; }
    public void setFullscreen(boolean v) { fullscreen = v; }
    public void setReducedEffects(boolean v) { reducedEffects = v; }
    public void setPlayerName(String v) {
        String name = sanitizeName(v);
        playerName = name.isEmpty() ? DEFAULT_NAME : name;
    }
    public void setLastAddress(String v) {
        String a = v == null ? "" : v.trim();
        lastAddress = a.length() > MAX_ADDRESS_LENGTH ? a.substring(0, MAX_ADDRESS_LENGTH) : a;
    }

    /** Upper-case letters, digits, space, dash and underscore, trimmed to {@link #MAX_NAME_LENGTH}; may be empty. */
    public static String sanitizeName(String v) {
        if (v == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : v.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == ' ' || ch == '-' || ch == '_') sb.append(ch);
        }
        String s = sb.toString().trim().replaceAll(" +", " ");
        return s.length() > MAX_NAME_LENGTH ? s.substring(0, MAX_NAME_LENGTH).trim() : s;
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
