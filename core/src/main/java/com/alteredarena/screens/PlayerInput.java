package com.alteredarena.screens;

import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.Controls;
import com.alteredarena.settings.Settings;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.MathUtils;

/**
 * Keyboard and mouse for the human player. Look is applied every rendered frame for responsiveness;
 * movement and button presses are sampled once per simulation tick, with edge-triggered presses queued
 * so a tap can never be lost between ticks.
 */
final class PlayerInput extends InputAdapter {
    static final float DEGREES_PER_PIXEL = 0.12f;

    interface Listener {
        void pauseRequested();
    }

    private final Settings settings;
    private final Listener listener;
    private boolean jumpQueued, reloadQueued;
    private int weaponQueued = -1;
    private boolean scoreboard;
    private int skipMouseFrames = 2;
    boolean enabled;

    PlayerInput(Settings settings, Listener listener) {
        this.settings = settings;
        this.listener = listener;
    }

    /** Drop the next mouse deltas (after capturing the cursor) to avoid a view jump. */
    void resetMouse() {
        skipMouseFrames = 2;
        jumpQueued = reloadQueued = false;
        weaponQueued = -1;
    }

    boolean scoreboardHeld() { return scoreboard; }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.ESCAPE:
            case Input.Keys.P:
                listener.pauseRequested();
                return true;
            case Input.Keys.TAB:
                scoreboard = true;
                return true;
            default:
                break;
        }
        if (!enabled) return false;
        switch (keycode) {
            case Input.Keys.SPACE: jumpQueued = true; return true;
            case Input.Keys.R: reloadQueued = true; return true;
            case Input.Keys.NUM_1: weaponQueued = 0; return true;
            case Input.Keys.NUM_2: weaponQueued = 1; return true;
            case Input.Keys.Q: weaponQueued = 2; return true;
            default: return false;
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.TAB) {
            scoreboard = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!enabled || amountY == 0f) return false;
        weaponQueued = 2;
        return true;
    }

    /** Applies mouse look to the player; call once per rendered frame. */
    void applyLook(Combatant player) {
        int dx = Gdx.input.getDeltaX(), dy = Gdx.input.getDeltaY();
        if (skipMouseFrames > 0) {
            skipMouseFrames--;
            return;
        }
        if (!enabled || !player.alive) return;
        float scale = DEGREES_PER_PIXEL * settings.sensitivity();
        player.yaw = (player.yaw + dx * scale) % 360f;
        float pitchDelta = dy * scale * (settings.invertY() ? 1f : -1f);
        player.pitch = MathUtils.clamp(player.pitch + pitchDelta, -88f, 88f);
    }

    /** Fills the player's controls for one simulation tick. */
    void fillControls(Combatant player) {
        Controls c = player.controls;
        c.clear();
        if (!enabled) return;
        boolean w = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean s = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        boolean a = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean d = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        c.forward = (w ? 1f : 0f) - (s ? 1f : 0f);
        c.strafe = (d ? 1f : 0f) - (a ? 1f : 0f);
        c.fire = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        c.jump = jumpQueued;
        c.reload = reloadQueued;
        if (weaponQueued == 2) c.selectWeapon = 1 - player.weaponIndex;
        else c.selectWeapon = weaponQueued;
        jumpQueued = reloadQueued = false;
        weaponQueued = -1;
    }
}
