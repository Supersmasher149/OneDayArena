package com.alteredarena.screens;

import com.alteredarena.AlteredArenaGame;
import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.Physics;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Drives the scripted graphical smoke test (--smoke): the player is a bot, and this director takes
 * screenshots, opens the scoreboard, pauses, toggles a setting, resumes, finishes a match, restarts,
 * finishes a second match and quits, logging what happened.
 */
final class SmokeDirector {
    private final AlteredArenaGame game;
    private final GameScreen screen;
    private final FileHandle dir;
    private float time;
    private int match = 1;
    private float resultsTime = -1f;
    private float nextPeriodic = 12f;
    private final Set<PowerUpType> playerPowerShots = EnumSet.noneOf(PowerUpType.class);
    private final Map<PowerUpType, Integer> collected = new EnumMap<>(PowerUpType.class);
    private final Map<PowerUpType, Integer> expired = new EnumMap<>(PowerUpType.class);
    private final Map<String, Integer> counts = new TreeMap<>();
    private float pendingShot = -1f;
    private String pendingName;
    private boolean deathShot, pauseDone, scoreboardDone;
    private float pauseAt = 16f;

    SmokeDirector(AlteredArenaGame game, GameScreen screen) {
        this.game = game;
        this.screen = screen;
        dir = Gdx.files.absolute(new java.io.File(game.options.screenshotDir).getAbsolutePath());
        dir.mkdirs();
        log("start match 1, timeScale=" + game.options.timeScale + ", physics worlds alive=" + Physics.liveCount());
    }

    static void log(String s) {
        System.out.println("[SMOKE] " + s);
    }

    void onEvent(GameEvent e) {
        Combatant actor = screen.world.combatant(e.actor);
        Combatant target = screen.world.combatant(e.target);
        counts.merge(e.type.name(), 1, Integer::sum);
        switch (e.type) {
            case KILL:
                log(String.format("t=%.1f KILL %s -> %s (%s)", screen.world.time(), actor.name, target.name, e.weapon));
                if (target.id == 0 && !deathShot) schedule("death_cam", 0.6f);
                break;
            case PICKUP:
                if (e.powerUp != null) {
                    collected.merge(e.powerUp, 1, Integer::sum);
                    log(String.format("t=%.1f PICKUP %s took %s%s", screen.world.time(), actor.name, e.powerUp, e.flag ? " (refresh)" : ""));
                    if (actor.id == 0 && playerPowerShots.add(e.powerUp)) schedule("powerup_" + e.powerUp.name().toLowerCase(), 0.3f);
                } else {
                    log(String.format("t=%.1f PICKUP %s took %s", screen.world.time(), actor.name, e.pickup.kind));
                }
                break;
            case POWERUP_EXPIRED:
                expired.merge(e.powerUp, 1, Integer::sum);
                log(String.format("t=%.1f EXPIRED %s on %s", screen.world.time(), e.powerUp, actor.name));
                break;
            case RESPAWN:
                log(String.format("t=%.1f RESPAWN %s", screen.world.time(), actor.name));
                break;
            case OUT_OF_BOUNDS:
                log(String.format("t=%.1f OUT_OF_BOUNDS %s", screen.world.time(), actor.name));
                break;
            case MATCH_END:
                log("match " + match + " ended: " + screen.world.match.results().headline() + " reason=" + screen.world.match.endReason());
                for (Combatant c : screen.world.combatants) log("  " + c.name + " kills=" + c.kills + " deaths=" + c.deaths);
                break;
            default:
                break;
        }
    }

    private void schedule(String name, float delay) {
        if (pendingShot >= 0f) return;
        pendingName = name;
        pendingShot = delay;
        if (name.equals("death_cam")) deathShot = true;
    }

    void onRestart() {
        match++;
        resultsTime = -1f;
        log("restart -> match " + match + ", physics worlds alive=" + Physics.liveCount());
    }

    // Power-up tour state
    private int tourIndex = -1;
    private boolean tourShot;

    private void updateTour(float delta) {
        Combatant player = screen.world.player();
        PowerUpType[] all = PowerUpType.values();
        if (tourIndex >= 0 && tourIndex < all.length) {
            PowerUpType current = all[tourIndex];
            if (player.powerUp.active() == current && !tourShot && player.powerUp.fraction() < 0.6f) {
                screenshot("tour_" + current.name().toLowerCase() + "_active");
                tourShot = true;
            }
            if (player.powerUp.isActive() || !tourShot) return;
        }
        tourIndex++;
        if (tourIndex >= all.length) {
            log("tour complete: collected " + collected + " expired " + expired);
            screenshot("tour_end_baseline");
            log(String.format("baseline after tour: speedMult=%.2f fireRateMult=%.2f extraJumps=%d damageTaken=%.2f",
                    player.modifiers().speed, player.modifiers().fireRate, player.modifiers().extraJumps, player.modifiers().damageTaken));
            Gdx.app.exit();
            tourIndex = 1000;
            return;
        }
        for (com.alteredarena.sim.Pickup p : screen.world.pickups) {
            if (p.powerUp == all[tourIndex]) {
                player.body.teleport(new com.badlogic.gdx.math.Vector3(p.position).add(0f, 0.05f, 0f));
                player.yaw = p.position.z > 0 ? 0f : 180f;
                player.pitch = -10f;
                player.protection = 0f;
                log("tour: walking onto " + p.powerUp);
            }
        }
        tourShot = false;
    }

    void update(float delta) {
        time += delta;
        if (game.options.tour) {
            if (pendingShot >= 0f) {
                pendingShot -= delta;
                if (pendingShot < 0f) screenshot(pendingName);
            }
            if (time > 1.5f) updateTour(delta);
            return;
        }
        if (pendingShot >= 0f) {
            pendingShot -= delta;
            if (pendingShot < 0f) screenshot(pendingName);
        }
        if (time > 3f && time - delta <= 3f) screenshot("m" + match + "_gameplay_start");
        if (time > nextPeriodic && !screen.isPaused()) {
            screenshot(String.format("m%d_gameplay_%03d", match, (int) time));
            nextPeriodic += 12f;
        }
        if (match == 1 && !scoreboardDone && time > 9f) {
            screen.forceScoreboard(true);
            if (time > 10f) {
                screenshot("m1_scoreboard");
                screen.forceScoreboard(false);
                scoreboardDone = true;
            }
        }
        if (match == 1 && !pauseDone) {
            if (time > pauseAt && !screen.isPaused()) {
                screen.pauseGame();
                log("paused at sim t=" + screen.world.time());
            } else if (time > pauseAt + 0.6f && time < pauseAt + 0.7f && screen.isPaused()) {
                screenshot("m1_pause_menu");
            } else if (time > pauseAt + 1.2f && screen.isPaused() && !screen.isSettingsOpen()) {
                screen.openSettings();
                // Flip two settings so persistence can be checked on the next launch.
                game.settings.setReducedEffects(!game.settings.reducedEffects());
                game.settings.setSensitivity(game.settings.sensitivity() == 1f ? 1.35f : 1f);
                game.saveSettings();
                log(String.format("settings changed and saved: reducedEffects=%b sensitivity=%.2f",
                        game.settings.reducedEffects(), game.settings.sensitivity()));
            } else if (time > pauseAt + 2f && screen.isPaused() && screen.isSettingsOpen()) {
                screenshot("m1_pause_settings");
                float simBefore = screen.world.time();
                screen.resumeGame();
                pauseDone = true;
                log("resumed; sim time did not advance while paused: " + simBefore);
            }
        }
        if (screen.resultsShown()) {
            if (resultsTime < 0f) resultsTime = 0f;
            resultsTime += delta;
            if (resultsTime > 1f && resultsTime - delta <= 1f) screenshot("m" + match + "_results");
            if (resultsTime > 2.5f) {
                if (match == 1) {
                    summary();
                    screen.restart();
                } else {
                    summary();
                    log("returning to main menu");
                    game.showMenu();
                    Gdx.app.postRunnable(() -> Gdx.app.postRunnable(() -> new Thread(() -> {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException ignored) {
                        }
                        Gdx.app.postRunnable(() -> {
                            screenshot("main_menu");
                            log("done, physics worlds alive=" + Physics.liveCount());
                            Gdx.app.exit();
                        });
                    }).start()));
                    resultsTime = -1000f;
                }
            }
        }
    }

    private void summary() {
        log("events " + counts);
        log("power-ups collected " + collected);
        log("power-ups expired   " + expired);
        counts.clear();
    }

    private void screenshot(String name) {
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        FileHandle file = dir.child(name + ".png");
        PixmapIO.writePNG(file, pixmap, -1, true);
        pixmap.dispose();
        log("screenshot " + file.path());
    }
}
