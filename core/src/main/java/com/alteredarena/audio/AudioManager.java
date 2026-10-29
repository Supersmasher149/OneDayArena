package com.alteredarena.audio;

import com.alteredarena.settings.Settings;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.EnumMap;
import java.util.Map;

/** Plays sound effects with master volume, distance attenuation and stereo panning. */
public final class AudioManager {
    private final Map<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private final Settings settings;
    private final Vector3 listener = new Vector3();
    private final Vector3 listenerRight = new Vector3(1f, 0f, 0f);
    private final Vector3 tmp = new Vector3();
    private boolean muted;

    public AudioManager(AssetManager assets, Settings settings) {
        this.settings = settings;
        for (Sfx s : Sfx.values()) sounds.put(s, assets.get(s.path, Sound.class));
    }

    public static void queue(AssetManager assets) {
        for (Sfx s : Sfx.values()) assets.load(s.path, Sound.class);
    }

    /** Silences world sounds (used by the menu's background match). */
    public void setMuted(boolean muted) { this.muted = muted; }

    public void setListener(Vector3 position, float yawDegrees) {
        listener.set(position);
        listenerRight.set(MathUtils.cosDeg(yawDegrees), 0f, MathUtils.sinDeg(yawDegrees));
    }

    public void play(Sfx sfx, float volume) {
        play(sfx, volume, 1f, 0f);
    }

    public void play(Sfx sfx, float volume, float pitch, float pan) {
        float v = volume * settings.masterVolume();
        if (muted || v <= 0.001f) return;
        sounds.get(sfx).play(Math.min(1f, v), pitch, MathUtils.clamp(pan, -1f, 1f));
    }

    /** Plays a sound in the world, attenuated and panned relative to the listener. */
    public void playAt(Sfx sfx, Vector3 position, float volume, float pitch) {
        tmp.set(position).sub(listener);
        float distance = tmp.len();
        if (distance > 70f) return;
        float attenuation = 1f / (1f + distance * 0.09f);
        float pan = distance > 0.5f ? tmp.scl(1f / distance).dot(listenerRight) * 0.8f : 0f;
        play(sfx, volume * attenuation, pitch, pan);
    }
}
