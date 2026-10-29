package com.alteredarena;

/** Command-line options. Normal players need none of these. */
public final class LaunchOptions {
    /** Player is driven by bot AI (attract/soak mode). */
    public boolean autoplay;
    /** Scripted graphical smoke test: autoplay, screenshots, pause/resume, restart, then exit. */
    public boolean smoke;
    /** Smoke variant: no bots; the player is walked onto each power-up in turn until every one expires. */
    public boolean tour;
    /** Simulation speed multiplier (smoke tests only). */
    public float timeScale = 1f;
    /** Directory for smoke-test screenshots. */
    public String screenshotDir = "smoke-output";
    /** Shortened match length in seconds for smoke runs, or 0 for the normal rules. */
    public float matchSeconds;

    public static LaunchOptions parse(String[] args) {
        LaunchOptions o = new LaunchOptions();
        if (args == null) return o;
        for (String a : args) {
            if (a.equals("--autoplay")) o.autoplay = true;
            else if (a.equals("--smoke")) {
                o.smoke = true;
                o.autoplay = true;
            } else if (a.equals("--tour")) {
                o.smoke = true;
                o.autoplay = true;
                o.tour = true;
            } else if (a.startsWith("--timescale=")) o.timeScale = Math.max(0.1f, Math.min(8f, Float.parseFloat(a.substring(12))));
            else if (a.startsWith("--screenshots=")) o.screenshotDir = a.substring(14);
            else if (a.startsWith("--match-seconds=")) o.matchSeconds = Float.parseFloat(a.substring(16));
        }
        return o;
    }
}
