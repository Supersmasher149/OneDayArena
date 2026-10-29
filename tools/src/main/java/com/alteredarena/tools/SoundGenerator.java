package com.alteredarena.tools;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Synthesizes every sound effect the game ships with as 16-bit mono WAV files.
 * Deterministic: the same seed always produces identical files.
 */
public final class SoundGenerator {
    static final int RATE = 44100;
    static final Random RNG = new Random(1337);

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "assets/sounds");
        out.mkdirs();
        write(out, "rifle", rifle());
        write(out, "scattergun", scattergun());
        write(out, "impact", impact());
        write(out, "hit", hitMarker(2200, 0.05));
        write(out, "kill", killConfirm());
        write(out, "hurt", hurt());
        write(out, "death", death());
        write(out, "respawn", sweepTone(300, 900, 0.45, 0.35, true));
        write(out, "pickup_power", powerUp());
        write(out, "pickup_health", notes(new double[]{523, 784}, 0.11, 0.35));
        write(out, "pickup_ammo", ammo());
        write(out, "expire", sweepTone(900, 260, 0.4, 0.3, true));
        write(out, "reload", reload());
        write(out, "reload_done", clicks(new double[]{0.0, 0.07}, 0.5));
        write(out, "dry", clicks(new double[]{0.0}, 0.35));
        write(out, "switch", swish(0.16, 0.3));
        write(out, "jump", swish(0.18, 0.18));
        write(out, "land", thud(0.16, 0.4));
        write(out, "ui_click", notes(new double[]{880}, 0.05, 0.3));
        write(out, "ui_hover", notes(new double[]{1320}, 0.025, 0.12));
        write(out, "match_end", matchEnd());
        write(out, "pickup_spawn", sweepTone(500, 1200, 0.25, 0.18, false));
        System.out.println("Sounds written to " + out.getAbsolutePath());
    }

    static double[] buf(double seconds) { return new double[(int) (seconds * RATE)]; }

    static double env(double t, double attack, double decay) {
        if (t < attack) return t / attack;
        return Math.exp(-(t - attack) / decay);
    }

    static double noise() { return RNG.nextDouble() * 2 - 1; }

    static double[] rifle() {
        double[] s = buf(0.16);
        double phase = 0, lp = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double f = 1400 * Math.exp(-t * 22) + 180;
            phase += f / RATE;
            double square = (phase % 1.0) < 0.5 ? 1 : -1;
            lp += (noise() - lp) * 0.35;
            s[i] = (square * 0.45 + lp * 0.7) * env(t, 0.002, 0.035);
        }
        return s;
    }

    static double[] scattergun() {
        double[] s = buf(0.45);
        double lp = 0, phase = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            lp += (noise() - lp) * (0.5 * Math.exp(-t * 8) + 0.04);
            phase += (110 * Math.exp(-t * 6) + 40) / RATE;
            double thump = Math.sin(phase * Math.PI * 2) * env(t, 0.003, 0.09);
            s[i] = lp * 1.4 * env(t, 0.001, 0.11) + thump * 0.9;
        }
        return s;
    }

    static double[] impact() {
        double[] s = buf(0.08);
        double prev = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double n = noise();
            s[i] = (n - prev) * 0.6 * env(t, 0.0005, 0.015);
            prev = n;
        }
        return s;
    }

    static double[] hitMarker(double freq, double seconds) {
        double[] s = buf(seconds);
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            s[i] = Math.sin(t * freq * Math.PI * 2) * env(t, 0.001, seconds / 4) * 0.6;
        }
        return s;
    }

    static double[] killConfirm() {
        double[] s = buf(0.35);
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double f = t < 0.08 ? 988 : 1480;
            double local = t < 0.08 ? t : t - 0.08;
            s[i] = (Math.sin(t * f * Math.PI * 2) + 0.3 * Math.sin(t * f * 2 * Math.PI * 2)) * env(local, 0.002, 0.07) * 0.45;
        }
        return s;
    }

    static double[] hurt() {
        double[] s = buf(0.22);
        double lp = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            lp += (noise() - lp) * 0.08;
            s[i] = (lp * 1.6 + Math.sin(t * 140 * Math.PI * 2) * 0.5) * env(t, 0.002, 0.05);
        }
        return s;
    }

    static double[] death() {
        double[] s = buf(0.7);
        double phase = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double f = 420 * Math.exp(-t * 3.5) + 50;
            phase += f / RATE;
            double saw = 2 * (phase % 1.0) - 1;
            s[i] = (saw * 0.5 + noise() * 0.15) * env(t, 0.005, 0.22);
        }
        return s;
    }

    static double[] sweepTone(double from, double to, double seconds, double gain, boolean shimmer) {
        double[] s = buf(seconds);
        double phase = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double k = t / seconds;
            double f = from * Math.pow(to / from, k);
            phase += f / RATE;
            double v = Math.sin(phase * Math.PI * 2);
            if (shimmer) v += 0.35 * Math.sin(phase * 3.01 * Math.PI * 2) * Math.sin(t * 40);
            s[i] = v * gain * Math.sin(Math.PI * k);
        }
        return s;
    }

    static double[] powerUp() {
        double[] s = buf(0.55);
        double[] notes = {523.25, 659.25, 783.99, 1046.5};
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double v = 0;
            for (int n = 0; n < notes.length; n++) {
                double start = n * 0.07;
                if (t < start) continue;
                double lt = t - start;
                v += (Math.sin(lt * notes[n] * Math.PI * 2) + 0.25 * Math.sin(lt * notes[n] * 2.01 * Math.PI * 2)) * env(lt, 0.004, 0.16);
            }
            s[i] = v * 0.28;
        }
        return s;
    }

    static double[] notes(double[] freqs, double each, double gain) {
        double[] s = buf(each * freqs.length + 0.1);
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            int n = Math.min(freqs.length - 1, (int) (t / each));
            double lt = t - n * each;
            s[i] = Math.sin(t * freqs[n] * Math.PI * 2) * env(lt, 0.003, each * 0.6) * gain;
        }
        return s;
    }

    static double[] ammo() {
        double[] s = buf(0.25);
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double v = Math.sin(t * 1800 * Math.PI * 2) * env(t, 0.001, 0.02) + Math.sin(t * 2700 * Math.PI * 2) * env(Math.max(0, t - 0.09), 0.001, 0.025) * (t > 0.09 ? 1 : 0);
            s[i] = (v + noise() * 0.2 * env(t, 0.001, 0.01)) * 0.45;
        }
        return s;
    }

    static double[] clicks(double[] times, double gain) {
        double[] s = buf(times[times.length - 1] + 0.06);
        for (double start : times) {
            int offset = (int) (start * RATE);
            for (int i = 0; i < (int) (0.05 * RATE) && offset + i < s.length; i++) {
                double t = (double) i / RATE;
                s[offset + i] += (noise() * 0.7 + Math.sin(t * 3200 * Math.PI * 2) * 0.5) * env(t, 0.0005, 0.008) * gain;
            }
        }
        return s;
    }

    static double[] reload() {
        double[] a = clicks(new double[]{0.0, 0.22, 0.3}, 0.45);
        double[] s = buf(0.5);
        System.arraycopy(a, 0, s, 0, Math.min(a.length, s.length));
        double lp = 0;
        for (int i = (int) (0.05 * RATE); i < (int) (0.2 * RATE); i++) {
            double t = (double) i / RATE - 0.05;
            lp += (noise() - lp) * 0.2;
            s[i] += lp * 0.25 * Math.sin(Math.PI * t / 0.15);
        }
        return s;
    }

    static double[] swish(double seconds, double gain) {
        double[] s = buf(seconds);
        double lp = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double k = t / seconds;
            lp += (noise() - lp) * (0.05 + 0.25 * k);
            s[i] = lp * gain * 2.5 * Math.sin(Math.PI * k);
        }
        return s;
    }

    static double[] thud(double seconds, double gain) {
        double[] s = buf(seconds);
        double phase = 0, lp = 0;
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            phase += (90 * Math.exp(-t * 10) + 45) / RATE;
            lp += (noise() - lp) * 0.06;
            s[i] = (Math.sin(phase * Math.PI * 2) + lp) * env(t, 0.002, 0.04) * gain;
        }
        return s;
    }

    static double[] matchEnd() {
        double[] s = buf(1.6);
        double[] chord = {261.63, 329.63, 392.0, 523.25};
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / RATE;
            double v = 0;
            for (int n = 0; n < chord.length; n++) {
                double start = n * 0.06;
                if (t < start) continue;
                double lt = t - start;
                double f = chord[n];
                v += (Math.sin(lt * f * Math.PI * 2) + 0.4 * Math.sin(lt * f * 2 * Math.PI * 2) * Math.exp(-lt * 3)) * env(lt, 0.01, 0.6);
            }
            s[i] = v * 0.2;
        }
        return s;
    }

    static void write(File dir, String name, double[] samples) throws IOException {
        double peak = 1e-9;
        for (double v : samples) peak = Math.max(peak, Math.abs(v));
        double norm = peak > 0.95 ? 0.95 / peak : 1.0;
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (double v : samples) {
            int sample = (int) Math.round(Math.max(-1, Math.min(1, v * norm)) * 32767);
            pcm.write(sample & 0xff);
            pcm.write((sample >> 8) & 0xff);
        }
        byte[] data = pcm.toByteArray();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(new File(dir, name + ".wav")))) {
            out.writeBytes("RIFF");
            out.writeInt(Integer.reverseBytes(36 + data.length));
            out.writeBytes("WAVE");
            out.writeBytes("fmt ");
            out.writeInt(Integer.reverseBytes(16));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeInt(Integer.reverseBytes(RATE));
            out.writeInt(Integer.reverseBytes(RATE * 2));
            out.writeShort(Short.reverseBytes((short) 2));
            out.writeShort(Short.reverseBytes((short) 16));
            out.writeBytes("data");
            out.writeInt(Integer.reverseBytes(data.length));
            out.write(data);
        }
    }
}
