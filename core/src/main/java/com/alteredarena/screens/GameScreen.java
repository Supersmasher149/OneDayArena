package com.alteredarena.screens;

import com.alteredarena.AlteredArenaGame;
import com.alteredarena.audio.AudioManager;
import com.alteredarena.audio.Sfx;
import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.net.NetClient;
import com.alteredarena.net.NetHost;
import com.alteredarena.net.NetSession;
import com.alteredarena.net.Protocol;
import com.alteredarena.render.WorldRenderer;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponType;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.sim.Match;
import com.alteredarena.ui.Hud;
import com.alteredarena.ui.Menus;
import com.alteredarena.ui.Scoreboard;
import com.alteredarena.ui.SettingsPanel;
import com.alteredarena.ui.UiAssets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/**
 * A match: fixed-step simulation, first-person camera, HUD, pause menu and results. Online, the host simulates
 * as in solo play and also feeds its clients; a client never steps the world and only shows the host's snapshots.
 * The pause menu never stops an online match.
 */
public final class GameScreen extends ScreenAdapter implements PlayerInput.Listener {
    private static final float BASE_FOV = 72f;
    private static int matchCounter;

    private final AlteredArenaGame game;
    private final WorldRenderer renderer;
    private final AudioManager audio;
    private final Stage stage;
    private final Hud hud;
    private final PlayerInput input;
    private final InputMultiplexer multiplexer;
    private final Table dim, pauseMenu, results, pauseContent;
    private final Table pauseButtons;
    private final SettingsPanel settingsPanel;
    private final Table controls;
    private final Label resultsHeadline, resultsReason;
    private final Scoreboard resultsBoard;
    private final SmokeDirector smoke;
    private final NetSession session;
    private final NetHost host;
    private final NetClient client;
    private float inputTimer;

    GameWorld world;
    private float accumulator;
    private boolean paused, resultsShown;
    private float shake, deathTime, fov = BASE_FOV;
    private final Vector3 tmp = new Vector3();

    public GameScreen(AlteredArenaGame game) {
        this.game = game;
        this.renderer = game.renderer;
        this.audio = game.audio;
        UiAssets ui = game.ui;
        session = game.session;
        host = session instanceof NetHost ? (NetHost) session : null;
        client = session instanceof NetClient ? (NetClient) session : null;
        stage = new Stage(new ExtendViewport(1280, 720));
        hud = new Hud(ui, stage);
        input = new PlayerInput(game.settings, this);
        multiplexer = new InputMultiplexer(stage, input);

        dim = new Table();
        dim.setFillParent(true);
        dim.setBackground(ui.tinted(new Color(0.02f, 0f, 0.05f, 0.6f)));
        stage.addActor(dim);

        // Pause menu
        pauseMenu = new Table();
        pauseMenu.setFillParent(true);
        pauseContent = new Table();
        pauseButtons = new Table();
        pauseButtons.setBackground(ui.skin.getDrawable("panel-bright"));
        pauseButtons.pad(30, 44, 30, 44);
        Label pausedTitle = new Label("PAUSED", ui.skin, "h1");
        pausedTitle.setColor(UiAssets.MAGENTA);
        pauseButtons.add(pausedTitle).padBottom(20).row();
        pauseButtons.defaults().width(300).height(54).padBottom(12);
        pauseButtons.add(Menus.button(ui, audio, "RESUME", this::resumeGame)).row();
        if (client == null) pauseButtons.add(Menus.button(ui, audio, host != null ? "RESTART FOR ALL" : "RESTART MATCH", this::restart)).row();
        pauseButtons.add(Menus.button(ui, audio, "SETTINGS", () -> showPausePanel(settingsPanelHolder()))).row();
        pauseButtons.add(Menus.button(ui, audio, "CONTROLS", () -> showPausePanel(controlsHolder()))).row();
        pauseButtons.add(Menus.button(ui, audio, session != null ? "LEAVE GAME" : "MAIN MENU", game::showMenu)).row();
        pauseButtons.add(Menus.button(ui, audio, "QUIT GAME", () -> Gdx.app.exit())).row();
        settingsPanel = new SettingsPanel(ui, game.settings, new SettingsPanel.Listener() {
            @Override
            public void settingsChanged(boolean displayModeChanged) {
                game.saveSettings();
                if (displayModeChanged) game.applyDisplayMode();
            }

            @Override
            public void back() {
                showPausePanel(pauseButtons);
            }
        });
        controls = Menus.controlsTable(ui, () -> showPausePanel(pauseButtons), audio);
        pauseMenu.add(pauseContent);
        stage.addActor(pauseMenu);

        // Results
        results = new Table();
        results.setFillParent(true);
        resultsHeadline = new Label("", ui.skin, "title");
        resultsReason = new Label("", ui.skin, "h2");
        resultsReason.setColor(UiAssets.DIM);
        resultsBoard = new Scoreboard(ui);
        results.add(resultsHeadline).row();
        results.add(resultsReason).padBottom(18).row();
        results.add(resultsBoard.table).padBottom(24).row();
        Table resultButtons = new Table();
        resultButtons.defaults().width(260).height(56).pad(8);
        if (client == null) {
            resultButtons.add(Menus.button(ui, audio, "PLAY AGAIN", this::restart));
        } else {
            Label waiting = new Label("WAITING FOR THE HOST", ui.skin, "h2");
            waiting.setColor(UiAssets.DIM);
            resultButtons.add(waiting).width(320);
        }
        resultButtons.add(Menus.button(ui, audio, session != null ? "LEAVE GAME" : "MAIN MENU", game::showMenu));
        results.add(resultButtons);
        stage.addActor(results);

        smoke = game.options.smoke && session == null ? new SmokeDirector(game, this) : null;
        if (session != null) {
            session.setListener(new NetSession.Listener() {
                @Override
                public void notice(String text) {
                    Gdx.app.log("Net", text);
                    hud.toast(text, UiAssets.YELLOW);
                }

                @Override
                public void disconnected(String reason) {
                    Gdx.app.log("Net", "disconnected: " + reason);
                    game.showMenu(reason);
                }
            });
        }
        newMatch();
    }

    private Table settingsPanelHolder() {
        settingsPanel.sync();
        return settingsPanel;
    }

    private Table controlsHolder() { return controls; }

    private void showPausePanel(Table panel) {
        pauseContent.clearChildren();
        pauseContent.add(panel);
    }

    boolean isSettingsOpen() { return settingsPanel.hasParent(); }

    void openSettings() { showPausePanel(settingsPanelHolder()); }

    private void newMatch() {
        if (world != null) world.dispose();
        float duration = game.options.matchSeconds > 0f ? game.options.matchSeconds : Tuning.MATCH_DURATION;
        boolean tour = game.options.tour;
        if (session != null) {
            world = session.newWorld(game.level, Tuning.KILL_LIMIT, duration);
            StringBuilder names = new StringBuilder();
            for (Combatant c : world.combatants) names.append(c.id == world.localId ? "*" : "").append(c.name).append(' ');
            Gdx.app.log("Net", (host != null ? "hosting" : "joined") + " match: " + names.toString().trim());
        } else {
            world = new GameWorld(game.level, System.nanoTime() ^ (++matchCounter * 7919L), tour ? 0 : Tuning.BOT_COUNT,
                    game.options.autoplay && !tour, Tuning.KILL_LIMIT, duration);
        }
        renderer.bind(world);
        hud.bind(world, renderer.camera);
        accumulator = 0f;
        paused = false;
        resultsShown = false;
        shake = 0f;
        deathTime = 0f;
        results.setVisible(false);
        pauseMenu.setVisible(false);
        dim.setVisible(false);
        hud.setVisible(true);
        capture(true);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(multiplexer);
        capture(!paused && !resultsShown);
    }

    private void capture(boolean capture) {
        boolean human = !game.options.autoplay || session != null;
        input.enabled = capture && human;
        Gdx.input.setCursorCatched(capture && human);
        input.resetMouse();
    }

    // ------------------------------------------------------------------ pause / resume / restart

    @Override
    public void pauseRequested() {
        if (resultsShown) return;
        if (paused) {
            if (pauseContent.getChildren().first() != pauseButtons) showPausePanel(pauseButtons);
            else resumeGame();
        } else {
            pauseGame();
        }
    }

    public void pauseFromFocusLoss() {
        if (!paused && !resultsShown && (!game.options.autoplay || session != null)) pauseGame();
    }

    void pauseGame() {
        paused = true;
        showPausePanel(pauseButtons);
        pauseMenu.setVisible(true);
        dim.setVisible(true);
        hud.setVisible(false);
        capture(false);
    }

    void resumeGame() {
        paused = false;
        pauseMenu.setVisible(false);
        dim.setVisible(false);
        hud.setVisible(true);
        if (session == null) accumulator = 0f;
        capture(true);
    }

    void restart() {
        newMatch();
        if (smoke != null) smoke.onRestart();
    }

    boolean isPaused() { return paused; }
    boolean resultsShown() { return resultsShown; }
    void forceScoreboard(boolean on) { forcedScoreboard = on; }
    private boolean forcedScoreboard;

    private void showResults() {
        resultsShown = true;
        Match.Results r = world.match.results();
        boolean playerWon = !r.isDraw() && !r.winners.isEmpty() && r.winners.get(0).id == world.localId;
        resultsHeadline.setText(r.headline(world.localId));
        resultsHeadline.setColor(playerWon ? UiAssets.CYAN : r.isDraw() ? UiAssets.YELLOW : UiAssets.MAGENTA);
        int top = r.standings.isEmpty() ? 0 : r.standings.get(0).combatant.kills;
        resultsReason.setText((r.reason == Match.EndReason.KILL_LIMIT ? "KILL LIMIT REACHED" : "TIME EXPIRED")
                + "  •  TOP SCORE " + top + (r.isDraw() ? "  •  TIED FOR FIRST" : ""));
        resultsBoard.update(world);
        results.setVisible(true);
        pauseMenu.setVisible(false);
        dim.setVisible(true);
        hud.setScoreboardVisible(false);
        hud.setVisible(false);
        capture(false);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(float delta) {
        float scale = session != null ? 1f : game.options.timeScale;
        float frame = Math.min(delta, Tuning.MAX_FRAME_TIME) * scale;
        boolean running;
        float alpha;

        if (client != null) {
            client.update(world, delta);
            if (client.isClosed()) return; // disconnected; the menu is on its way
            if (client.matchPending()) newMatch();
            Combatant player = world.player();
            running = world.match.isRunning();
            input.applyLook(player); // always, so mouse motion while the menu is open is swallowed
            inputTimer += delta;
            if (inputTimer >= Protocol.INPUT_INTERVAL) {
                inputTimer = Math.min(inputTimer - Protocol.INPUT_INTERVAL, Protocol.INPUT_INTERVAL);
                input.fillControls(player);
                client.sendInput(player);
            }
            alpha = client.interpolationAlpha();
        } else {
            Combatant player = world.player();
            boolean human = !game.options.autoplay || host != null;
            // An online match keeps running behind the pause menu; input is disabled while it is open.
            running = world.match.isRunning() && (!paused || host != null);
            if (running) {
                input.applyLook(player);
                accumulator += frame;
                int steps = 0;
                int maxSteps = Math.round(Tuning.MAX_STEPS_PER_FRAME * Math.max(1f, scale));
                while (accumulator >= Tuning.FIXED_DT && steps < maxSteps) {
                    if (human) input.fillControls(player);
                    if (host != null) host.applyInputs(world);
                    world.step(Tuning.FIXED_DT);
                    accumulator -= Tuning.FIXED_DT;
                    steps++;
                }
                if (steps >= maxSteps) accumulator = 0f; // drop time we cannot catch up on
            } else {
                Gdx.input.getDeltaX(); // swallow mouse motion while paused
                Gdx.input.getDeltaY();
            }
            alpha = running ? accumulator / Tuning.FIXED_DT : 1f;
            // After the steps and before processEvents clears them, so this frame's events reach the clients.
            if (host != null) {
                host.update(world, delta);
                if (host.isClosed()) return;
            }
        }
        Combatant player = world.player();

        processEvents();
        updateCamera(alpha, delta);
        renderer.update(running ? frame : 0f);
        renderer.render(world, alpha, player.id);
        if (!resultsShown) renderer.renderViewModel(player, player.body.horizontalSpeed(), game.settings.reducedEffects());

        hud.setScoreboardVisible(!resultsShown && !paused && (input.scoreboardHeld() || forcedScoreboard));
        hud.update(running ? delta : 0f, game.settings.reducedEffects());
        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();

        if (!world.match.isRunning() && !resultsShown) showResults();
        if (smoke != null) smoke.update(delta);
    }

    private void updateCamera(float alpha, float delta) {
        Combatant player = world.player();
        boolean reduced = game.settings.reducedEffects();
        shake = Math.max(0f, shake - delta * 3f);
        if (player.alive) {
            deathTime = 0f;
            tmp.set(player.body.previousPosition).lerp(player.body.position, alpha).add(0f, Tuning.EYE_HEIGHT, 0f);
            renderer.camera.position.set(tmp);
            float sy = 0f, sp = 0f;
            if (!reduced && shake > 0f) {
                sy = MathUtils.random(-1f, 1f) * shake * 0.8f;
                sp = MathUtils.random(-1f, 1f) * shake * 0.8f;
            }
            Combatant.direction(player.yaw + sy, player.pitch + sp, renderer.camera.direction);
        } else {
            deathTime += delta;
            renderer.camera.position.set(player.body.position).add(0f, Tuning.EYE_HEIGHT + Math.min(2.5f, deathTime * 1.5f), 0f);
            Combatant killer = world.combatant(player.lastAttackerId);
            if (killer != null && killer.alive && killer != player) {
                killer.chest(tmp).sub(renderer.camera.position).nor();
                renderer.camera.direction.lerp(tmp, Math.min(1f, delta * 4f)).nor();
            } else {
                Combatant.direction(player.yaw, -35f, tmp);
                renderer.camera.direction.lerp(tmp, Math.min(1f, delta * 3f)).nor();
            }
        }
        renderer.camera.up.set(0f, 1f, 0f);
        float targetFov = BASE_FOV;
        if (!reduced && player.alive && player.powerUp.active() == PowerUpType.COKE) targetFov += 8f;
        fov += (targetFov - fov) * Math.min(1f, delta * 6f);
        renderer.camera.fieldOfView = fov;
        renderer.camera.update();
        audio.setListener(renderer.camera.position, player.yaw);
    }

    private void processEvents() {
        int me = world.player().id;
        boolean reduced = game.settings.reducedEffects();
        for (GameEvent e : world.events) {
            renderer.onEvent(e, world, me, reduced);
            if (smoke != null) smoke.onEvent(e);
            Combatant actor = world.combatant(e.actor);
            switch (e.type) {
                case SHOT: {
                    Sfx s = e.weapon == WeaponType.SCATTERGUN ? Sfx.SCATTERGUN : Sfx.RIFLE;
                    float pitch = MathUtils.random(0.94f, 1.06f);
                    if (e.actor == me) audio.play(s, e.weapon == WeaponType.SCATTERGUN ? 0.85f : 0.5f, pitch, 0f);
                    else audio.playAt(s, e.position, 0.75f, pitch);
                    break;
                }
                case IMPACT:
                    if (MathUtils.randomBoolean(0.5f)) audio.playAt(Sfx.IMPACT, e.position, 0.35f, MathUtils.random(0.8f, 1.3f));
                    break;
                case HIT:
                    if (e.actor == me) {
                        hud.hitConfirmed(e.flag);
                        audio.play(e.flag ? Sfx.KILL : Sfx.HIT, e.flag ? 0.8f : 0.45f);
                    }
                    if (e.target == me) {
                        hud.damaged(e.position2, e.value, reduced);
                        shake = Math.min(1f, shake + e.value / 35f);
                        audio.play(Sfx.HURT, 0.6f);
                    }
                    break;
                case KILL: {
                    Combatant victim = world.combatant(e.target);
                    hud.killFeed(actor.name.toUpperCase(), new Color(actor.rgba), victim.name.toUpperCase(), new Color(victim.rgba),
                            e.weapon == WeaponType.SCATTERGUN ? "SCATTERGUN" : "PULSE");
                    if (e.target == me) audio.play(Sfx.DEATH, 0.8f);
                    else audio.playAt(Sfx.DEATH, e.position, 0.6f, 1f);
                    if (e.actor == me) hud.toast("ELIMINATED " + victim.name.toUpperCase(), new Color(victim.rgba));
                    break;
                }
                case PICKUP: {
                    Sfx s = e.pickup.kind == PickupKind.HEALTH ? Sfx.PICKUP_HEALTH : e.pickup.kind == PickupKind.AMMO ? Sfx.PICKUP_AMMO : Sfx.PICKUP_POWER;
                    if (e.actor == me) {
                        audio.play(s, 0.8f);
                        if (e.powerUp != null) {
                            hud.toast((e.flag ? "REFRESHED " : "+ ") + e.powerUp.label + "  •  " + e.powerUp.description, new Color(e.powerUp.rgba));
                        } else if (e.pickup.kind == PickupKind.HEALTH) {
                            hud.toast("+" + Math.round(e.value) + " HEALTH", UiAssets.GREEN);
                        } else {
                            hud.toast("+ AMMO", new Color(0x4DA6FFFF));
                        }
                    } else {
                        audio.playAt(s, e.position, 0.5f, 1f);
                    }
                    break;
                }
                case PICKUP_RESPAWN:
                    audio.playAt(Sfx.PICKUP_SPAWN, e.position, 0.5f, 1f);
                    break;
                case POWERUP_EXPIRED:
                    if (e.actor == me) {
                        audio.play(Sfx.EXPIRE, 0.7f);
                        hud.toast(e.powerUp.label + " WORE OFF", UiAssets.DIM);
                    }
                    break;
                case RESPAWN:
                    if (e.actor == me) audio.play(Sfx.RESPAWN, 0.7f);
                    else audio.playAt(Sfx.RESPAWN, e.position, 0.5f, 1f);
                    break;
                case JUMP:
                    if (e.actor == me) audio.play(Sfx.JUMP, e.flag ? 0.5f : 0.3f, e.flag ? 1.3f : 1f, 0f);
                    break;
                case LAND:
                    if (e.actor == me && e.value > 4f) audio.play(Sfx.LAND, Math.min(0.7f, e.value / 20f));
                    break;
                case RELOAD_START:
                    if (e.actor == me) audio.play(Sfx.RELOAD, 0.6f);
                    else audio.playAt(Sfx.RELOAD, actor.body.position, 0.3f, 1f);
                    break;
                case RELOAD_DONE:
                    if (e.actor == me) audio.play(Sfx.RELOAD_DONE, 0.5f);
                    break;
                case DRY_FIRE:
                    if (e.actor == me) audio.play(Sfx.DRY, 0.6f);
                    break;
                case WEAPON_SWITCH:
                    if (e.actor == me) audio.play(Sfx.SWITCH, 0.5f);
                    break;
                case OUT_OF_BOUNDS:
                    if (e.actor == me) hud.toast("RETURNED TO THE ARENA", UiAssets.YELLOW);
                    break;
                case MATCH_END:
                    audio.play(Sfx.MATCH_END, 0.8f);
                    break;
                default:
                    break;
            }
        }
        world.events.clear();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        renderer.resize(width, height);
    }

    @Override
    public void hide() {
        Gdx.input.setCursorCatched(false);
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (world != null) world.dispose();
        world = null;
        stage.dispose();
    }
}
