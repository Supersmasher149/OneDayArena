package com.alteredarena.ui;

import com.alteredarena.level.LevelData.PickupKind;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.rules.WeaponState;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.sim.Match;
import com.alteredarena.sim.Pickup;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/** In-match heads-up display. Everything is a Scene2D actor positioned against the stage's virtual size. */
public final class Hud {
    private final UiAssets ui;
    private final Stage stage;
    private final Group root = new Group();
    private GameWorld world;

    // Effects overlay
    private final Image vignette, damageFlash, lowHealth;
    // Crosshair
    private final Image[] crossLines = new Image[4];
    private final Image crossDot, hitmarker;
    private float hitmarkerTime, hitmarkerKill;
    private final Array<Indicator> indicators = new Array<>();
    // Status
    private final Label timer, objective, scoreLine, leaderLine;
    private final Label healthValue, healthCaption, protection;
    private final Image healthBarBg, healthBar;
    private final Label weaponName, ammo, reserve;
    private final Image reloadBarBg, reloadBar;
    private final Table effectPanel;
    private final Image effectIcon, effectBar;
    private final Label effectName, effectDesc, effectTime;
    private final Label toast, centerMessage, centerSub;
    private float toastTime;
    private final Table feed = new Table();
    private final Array<FeedEntry> feedEntries = new Array<>();
    private final Scoreboard scoreboard;
    private final WorldLabels worldLabels = new WorldLabels();
    private Camera camera;
    private boolean showScoreboard;
    private float damageFlashAlpha;
    private float time;

    private static final class Indicator {
        final Image image;
        final Vector3 source = new Vector3();
        float life;
        Indicator(Image image) { this.image = image; }
    }

    private static final class FeedEntry {
        final Label label;
        float age;
        FeedEntry(Label label) { this.label = label; }
    }

    public Hud(UiAssets ui, Stage stage) {
        this.ui = ui;
        this.stage = stage;
        root.setTouchable(Touchable.disabled);

        vignette = image(ui.vignette, Color.CLEAR);
        damageFlash = image(ui.vignette, new Color(1f, 0.1f, 0.2f, 0f));
        lowHealth = image(ui.vignette, new Color(1f, 0f, 0.15f, 0f));
        root.addActor(worldLabels);

        for (int i = 0; i < 4; i++) {
            crossLines[i] = image(ui.white, Color.WHITE);
            crossLines[i].setSize(i % 2 == 0 ? 10 : 2, i % 2 == 0 ? 2 : 10);
        }
        crossDot = image(ui.white, Color.WHITE);
        crossDot.setSize(3, 3);
        hitmarker = image(ui.hitmarker, Color.CLEAR);
        hitmarker.setSize(34, 34);
        for (int i = 0; i < 6; i++) {
            Image img = image(ui.damageArc, Color.CLEAR);
            img.setSize(220, 55);
            img.setOrigin(110, -150);
            indicators.add(new Indicator(img));
        }

        timer = label("5:00", ui.h1, Color.WHITE);
        objective = label("FIRST TO 15", ui.small, UiAssets.DIM);
        scoreLine = label("", ui.h2, Color.WHITE);
        leaderLine = label("", ui.small, UiAssets.DIM);

        healthCaption = label("HEALTH", ui.small, UiAssets.DIM);
        healthValue = label("100", ui.hud, Color.WHITE);
        healthBarBg = image(ui.white, new Color(1f, 1f, 1f, 0.12f));
        healthBar = image(ui.white, UiAssets.GREEN);
        protection = label("", ui.body, UiAssets.CYAN);

        weaponName = label("", ui.small, UiAssets.DIM);
        ammo = label("", ui.hud, Color.WHITE);
        reserve = label("", ui.h2, UiAssets.DIM);
        reloadBarBg = image(ui.white, new Color(1f, 1f, 1f, 0.12f));
        reloadBar = image(ui.white, UiAssets.YELLOW);

        effectPanel = new Table();
        effectPanel.setBackground(ui.skin.getDrawable("panel"));
        effectIcon = new Image(ui.icon(PowerUpType.WEED));
        effectName = new Label("", new Label.LabelStyle(ui.h2, Color.WHITE));
        effectDesc = new Label("", new Label.LabelStyle(ui.small, Color.WHITE));
        effectTime = new Label("", new Label.LabelStyle(ui.h2, Color.WHITE));
        effectBar = new Image(ui.white);
        Table text = new Table();
        text.add(effectName).left();
        text.add(effectTime).right().expandX().padLeft(12);
        text.row();
        text.add(effectDesc).left().colspan(2);
        effectPanel.add(effectIcon).size(54).padRight(10);
        effectPanel.add(text).width(250);
        effectPanel.row();
        effectPanel.add(effectBar).colspan(2).height(4).fillX().padTop(6);
        effectPanel.pack();
        root.addActor(effectPanel);

        toast = label("", ui.h2, Color.WHITE);
        centerMessage = label("", ui.h1, Color.WHITE);
        centerSub = label("", ui.body, UiAssets.DIM);
        root.addActor(feed);

        scoreboard = new Scoreboard(ui);
        root.addActor(scoreboard.table);
        stage.addActor(root);
    }

    private Image image(com.badlogic.gdx.graphics.g2d.TextureRegion region, Color color) {
        Image img = new Image(new TextureRegionDrawable(region));
        img.setColor(color);
        root.addActor(img);
        return img;
    }

    private Label label(String text, com.badlogic.gdx.graphics.g2d.BitmapFont font, Color color) {
        Label l = new Label(text, new Label.LabelStyle(font, Color.WHITE));
        l.setColor(color);
        root.addActor(l);
        return l;
    }

    public void bind(GameWorld world, Camera camera) {
        this.world = world;
        this.camera = camera;
        feed.clear();
        feedEntries.clear();
        for (Indicator i : indicators) i.life = 0f;
        toastTime = 0f;
        hitmarkerTime = 0f;
        damageFlashAlpha = 0f;
    }

    public void setVisible(boolean visible) { root.setVisible(visible); }
    public void setScoreboardVisible(boolean visible) { showScoreboard = visible; }

    // ------------------------------------------------------------------ feedback hooks

    public void hitConfirmed(boolean kill) {
        hitmarkerTime = 0.18f;
        hitmarkerKill = kill ? 1f : 0f;
    }

    public void damaged(Vector3 from, float amount, boolean reducedEffects) {
        Indicator slot = indicators.first();
        for (Indicator i : indicators) if (i.life < slot.life) slot = i;
        slot.source.set(from);
        slot.life = 1.0f;
        if (!reducedEffects) damageFlashAlpha = Math.min(0.55f, damageFlashAlpha + amount / 60f);
    }

    public void toast(String text, Color color) {
        toast.setText(text);
        toast.setColor(color);
        toastTime = 2.6f;
    }

    public void killFeed(String killer, Color killerColor, String victim, Color victimColor, String weapon) {
        Label l = new Label("[#" + killerColor + "]" + killer + "[] [#9A93B8]" + weapon + "[] [#" + victimColor + "]" + victim + "[]",
                new Label.LabelStyle(ui.small, Color.WHITE));
        l.getStyle().font.getData().markupEnabled = true;
        FeedEntry entry = new FeedEntry(l);
        feedEntries.add(entry);
        if (feedEntries.size > 5) feedEntries.removeIndex(0);
        rebuildFeed();
    }

    private void rebuildFeed() {
        feed.clear();
        for (FeedEntry e : feedEntries) {
            feed.add(e.label).right();
            feed.row();
        }
        feed.pack();
    }

    // ------------------------------------------------------------------ per frame

    public void update(float delta, boolean reducedEffects) {
        time += delta;
        Combatant player = world.player();
        float w = stage.getViewport().getWorldWidth(), h = stage.getViewport().getWorldHeight();
        float cx = w / 2f, cy = h / 2f;

        // Overlays
        vignette.setBounds(0, 0, w, h);
        damageFlash.setBounds(0, 0, w, h);
        lowHealth.setBounds(0, 0, w, h);
        PowerUpType power = player.alive ? player.powerUp.active() : null;
        if (power != null) {
            Color c = new Color(power.rgba);
            float alpha;
            if (reducedEffects) alpha = 0.22f;
            else {
                alpha = 0.32f + 0.1f * MathUtils.sin(time * 3f);
                if (power == PowerUpType.ACID) c.fromHsv((time * 90f) % 360f, 0.8f, 1f);
                if (power == PowerUpType.MUSHROOMS) alpha += 0.08f * MathUtils.sin(time * 1.3f);
            }
            if (player.powerUp.remaining() < 3f && !reducedEffects) alpha *= 0.6f + 0.4f * MathUtils.sin(time * 14f);
            vignette.setColor(c.r, c.g, c.b, alpha);
        } else {
            vignette.setColor(Color.CLEAR);
        }
        damageFlashAlpha = Math.max(0f, damageFlashAlpha - delta * 1.6f);
        damageFlash.getColor().a = damageFlashAlpha;
        float hp = player.health.current() / player.health.max();
        float low = player.alive && hp < 0.35f ? (0.35f - hp) / 0.35f : 0f;
        lowHealth.getColor().a = reducedEffects ? low * 0.4f : low * (0.35f + 0.15f * MathUtils.sin(time * 5f));

        // Crosshair: gap reflects current spread
        boolean alive = player.alive;
        WeaponState weapon = player.weapon();
        boolean moving = !player.body.grounded || player.body.horizontalSpeed() > 2f;
        float spread = (moving ? weapon.type.movingSpreadDegrees : weapon.type.spreadDegrees) * player.modifiers().spread;
        float gap = 5f + spread * 3.2f;
        crossLines[0].setPosition(cx + gap, cy - 1);
        crossLines[2].setPosition(cx - gap - 10, cy - 1);
        crossLines[1].setPosition(cx - 1, cy + gap);
        crossLines[3].setPosition(cx - 1, cy - gap - 10);
        crossDot.setPosition(cx - 1.5f, cy - 1.5f);
        Color crossColor = player.protection > 0f ? UiAssets.CYAN : Color.WHITE;
        for (Image l : crossLines) {
            l.setVisible(alive);
            l.setColor(crossColor);
        }
        crossDot.setVisible(alive);
        hitmarkerTime -= delta;
        hitmarker.setPosition(cx - 17, cy - 17);
        if (hitmarkerTime > 0f) hitmarker.setColor(hitmarkerKill > 0f ? UiAssets.RED : Color.WHITE);
        hitmarker.getColor().a = Math.max(0f, hitmarkerTime / 0.18f);

        // Damage direction indicators
        for (Indicator i : indicators) {
            i.life -= delta;
            if (i.life <= 0f) {
                i.image.setColor(Color.CLEAR);
                continue;
            }
            float dx = i.source.x - player.body.position.x, dz = i.source.z - player.body.position.z;
            float angleToSource = MathUtils.atan2(dx, -dz) * MathUtils.radiansToDegrees;
            float relative = angleToSource - player.yaw;
            i.image.setPosition(cx - 110, cy + 150);
            i.image.setRotation(-relative);
            i.image.setColor(1f, 0.2f, 0.3f, Math.min(1f, i.life * 1.5f));
        }

        // Match status
        Match match = world.match;
        int seconds = (int) Math.ceil(match.remaining());
        timer.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
        timer.setColor(seconds <= 30 ? UiAssets.YELLOW : Color.WHITE);
        timer.pack();
        timer.setPosition(cx - timer.getWidth() / 2f, h - 16 - timer.getHeight());
        objective.setText("FIRST TO " + match.killLimit);
        objective.pack();
        objective.setPosition(cx - objective.getWidth() / 2f, timer.getY() - objective.getHeight() + 4);

        Combatant leader = null;
        for (Combatant c : world.combatants) if (c != player && (leader == null || c.kills > leader.kills)) leader = c;
        scoreLine.setText("YOU  " + player.kills + "   [" + player.deaths + " deaths]");
        scoreLine.pack();
        scoreLine.setPosition(24, h - 18 - scoreLine.getHeight());
        if (leader != null) {
            int diff = player.kills - leader.kills;
            String lead = diff > 0 ? "LEADING BY " + diff : diff == 0 ? "TIED WITH " + leader.name.toUpperCase() : leader.name.toUpperCase() + " LEADS WITH " + leader.kills;
            leaderLine.setText(lead + "   •   TAB: SCOREBOARD");
            leaderLine.pack();
            leaderLine.setPosition(24, scoreLine.getY() - leaderLine.getHeight());
        }

        // Health
        healthCaption.pack();
        healthCaption.setPosition(28, 92);
        healthValue.setText(String.valueOf((int) Math.ceil(player.health.current())));
        healthValue.pack();
        healthValue.setPosition(26, 34);
        healthValue.setColor(hp < 0.35f ? UiAssets.RED : Color.WHITE);
        healthBarBg.setBounds(28, 26, 220, 8);
        healthBar.setBounds(28, 26, 220 * hp, 8);
        healthBar.setColor(hp < 0.35f ? UiAssets.RED : hp < 0.6f ? UiAssets.YELLOW : UiAssets.GREEN);
        if (player.alive && player.isProtected()) {
            protection.setText(String.format("SPAWN PROTECTED  %.1f", player.protection));
            protection.pack();
            protection.setPosition(cx - protection.getWidth() / 2f, cy - 90);
            protection.getColor().a = 1f;
        } else {
            protection.getColor().a = 0f;
        }

        // Ammo
        weaponName.setText(weapon.type.label + (player.switchTimer > 0f ? "  (SWITCHING)" : ""));
        weaponName.pack();
        ammo.setText(String.valueOf(weapon.magazine()));
        ammo.setColor(weapon.magazine() == 0 ? UiAssets.RED : weapon.magazine() <= weapon.type.magazineSize / 4 ? UiAssets.YELLOW : Color.WHITE);
        ammo.pack();
        reserve.setText("/ " + weapon.reserve());
        reserve.pack();
        float right = w - 28;
        reserve.setPosition(right - reserve.getWidth(), 40);
        ammo.setPosition(reserve.getX() - ammo.getWidth() - 8, 34);
        weaponName.setPosition(right - weaponName.getWidth(), 92);
        boolean reloading = weapon.isReloading();
        reloadBarBg.setVisible(reloading);
        reloadBar.setVisible(reloading);
        reloadBarBg.setBounds(right - 220, 26, 220, 8);
        reloadBar.setBounds(right - 220, 26, 220 * weapon.reloadProgress(), 8);
        if (alive && weapon.isEmpty()) {
            centerSub.setText("OUT OF AMMO  •  SWITCH WEAPONS OR FIND AMMO");
        }

        // Active effect
        if (power != null) {
            effectPanel.setVisible(true);
            Color c = new Color(power.rgba);
            effectIcon.setDrawable(new TextureRegionDrawable(ui.icon(power)));
            effectName.setText(power.label);
            effectName.setColor(c);
            effectDesc.setText(power.description);
            effectTime.setText(String.format("%.1fs", player.powerUp.remaining()));
            effectBar.setColor(c);
            effectBar.setScaleX(player.powerUp.fraction());
            effectPanel.pack();
            effectPanel.setPosition(24, 130);
        } else {
            effectPanel.setVisible(false);
        }

        // Toasts and center messages
        toastTime -= delta;
        toast.pack();
        toast.setPosition(cx - toast.getWidth() / 2f, cy - 118);
        toast.getColor().a = MathUtils.clamp(toastTime / 0.5f, 0f, 1f);
        if (!player.alive) {
            Combatant killer = world.combatant(player.lastAttackerId);
            centerMessage.setText(killer != null ? "ELIMINATED BY " + killer.name.toUpperCase() : "ELIMINATED");
            centerMessage.setColor(killer != null ? new Color(killer.rgba) : UiAssets.RED);
            centerSub.setText(String.format("RESPAWNING IN %.1f", Math.max(0f, player.respawnTimer)));
        } else {
            centerMessage.setText("");
            if (!weapon.isEmpty()) centerSub.setText("");
        }
        centerMessage.pack();
        centerMessage.setPosition(cx - centerMessage.getWidth() / 2f, cy + 70);
        centerSub.pack();
        centerSub.setPosition(cx - centerSub.getWidth() / 2f, cy + 40 - (player.alive ? 150 : 0));

        // Kill feed
        for (int i = feedEntries.size - 1; i >= 0; i--) {
            FeedEntry e = feedEntries.get(i);
            e.age += delta;
            e.label.getColor().a = MathUtils.clamp((6f - e.age) / 1f, 0f, 1f);
            if (e.age > 6f) {
                feedEntries.removeIndex(i);
                rebuildFeed();
            }
        }
        feed.setPosition(w - 24 - feed.getWidth(), h - 20 - feed.getHeight());

        scoreboard.table.setVisible(showScoreboard);
        if (showScoreboard) {
            scoreboard.update(world);
            scoreboard.table.setPosition(cx - scoreboard.table.getWidth() / 2f, cy - scoreboard.table.getHeight() / 2f + 20);
        }
    }

    // ------------------------------------------------------------------ labels over pickups and opponents

    private final class WorldLabels extends Actor {
        private final Vector3 p = new Vector3(), eye = new Vector3();
        private final GlyphLayout layout = new GlyphLayout();

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (world == null || camera == null) return;
            eye.set(camera.position);
            for (Pickup pickup : world.pickups) {
                if (!pickup.active) continue;
                p.set(pickup.position).add(0f, 1.85f, 0f);
                float dist = p.dst(eye);
                if (dist > 26f || !world.physics.lineOfSight(eye, p)) continue;
                Color c = pickup.kind == PickupKind.HEALTH ? UiAssets.RED : pickup.kind == PickupKind.AMMO ? new Color(0x4DA6FFFF)
                        : new Color(pickup.powerUp.rgba);
                drawAt(batch, pickup.label(), c, Math.max(0.25f, 1f - dist / 26f));
            }
            Combatant player = world.player();
            for (Combatant c : world.combatants) {
                if (c == player || !c.alive) continue;
                p.set(c.body.position).add(0f, 2.15f, 0f);
                float dist = p.dst(eye);
                if (dist > 40f || !world.physics.lineOfSight(eye, p)) continue;
                String name = c.name.toUpperCase();
                if (c.powerUp.isActive()) name += "  • " + c.powerUp.active().label;
                drawAt(batch, name, new Color(c.rgba), Math.max(0.35f, 1f - dist / 40f));
            }
        }

        private void drawAt(Batch batch, String text, Color color, float alpha) {
            Vector3 forward = camera.direction;
            if (p.cpy().sub(eye).dot(forward) <= 0.1f) return;
            camera.project(p, 0, 0, getStage().getViewport().getScreenWidth(), getStage().getViewport().getScreenHeight());
            // projected coordinates are in screen pixels; convert to stage units
            float sx = p.x * getStage().getViewport().getWorldWidth() / getStage().getViewport().getScreenWidth();
            float sy = p.y * getStage().getViewport().getWorldHeight() / getStage().getViewport().getScreenHeight();
            layout.setText(ui.small, text);
            ui.small.setColor(color.r, color.g, color.b, alpha);
            ui.small.draw(batch, layout, sx - layout.width / 2f, sy);
            ui.small.setColor(Color.WHITE);
        }
    }
}
