package com.alteredarena.ui;

import com.alteredarena.settings.Settings;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/** Settings form shared by the main menu and the pause menu. Every change applies and saves immediately. */
public final class SettingsPanel extends Table {
    public interface Listener {
        void settingsChanged(boolean displayModeChanged);
        void back();
    }

    private final Settings settings;
    private final Slider volume, sensitivity;
    private final CheckBox invert, fullscreen, reduced;
    private final Label volumeValue, sensitivityValue;
    private boolean syncing;

    public SettingsPanel(UiAssets ui, Settings settings, Listener listener) {
        this.settings = settings;
        setBackground(ui.skin.getDrawable("panel"));
        pad(26, 36, 26, 36);
        defaults().pad(8);

        Label title = new Label("SETTINGS", ui.skin, "h1");
        title.setColor(UiAssets.MAGENTA);
        add(title).colspan(3).left().padBottom(14).row();

        volume = new Slider(0f, 1f, 0.05f, false, ui.skin);
        volumeValue = new Label("", ui.skin);
        row("Master volume", volume, volumeValue, ui);

        sensitivity = new Slider(Settings.MIN_SENSITIVITY, Settings.MAX_SENSITIVITY, 0.05f, false, ui.skin);
        sensitivityValue = new Label("", ui.skin);
        row("Mouse sensitivity", sensitivity, sensitivityValue, ui);

        invert = new CheckBox("  Invert mouse Y", ui.skin);
        fullscreen = new CheckBox("  Fullscreen", ui.skin);
        reduced = new CheckBox("  Reduced effects (no flashing, shake or pulsing)", ui.skin);
        add(invert).colspan(3).left().row();
        add(fullscreen).colspan(3).left().row();
        add(reduced).colspan(3).left().row();

        TextButton back = new TextButton("BACK", ui.skin);
        add(back).colspan(3).left().width(220).height(52).padTop(18);

        sync();

        ChangeListener onChange = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (syncing) return;
                boolean display = actor == fullscreen;
                settings.setMasterVolume(volume.getValue());
                settings.setSensitivity(sensitivity.getValue());
                settings.setInvertY(invert.isChecked());
                settings.setFullscreen(fullscreen.isChecked());
                settings.setReducedEffects(reduced.isChecked());
                updateValues();
                listener.settingsChanged(display);
            }
        };
        volume.addListener(onChange);
        sensitivity.addListener(onChange);
        invert.addListener(onChange);
        fullscreen.addListener(onChange);
        reduced.addListener(onChange);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listener.back();
            }
        });
    }

    private void row(String name, Slider slider, Label value, UiAssets ui) {
        Label label = new Label(name, ui.skin);
        label.setColor(Color.WHITE);
        add(label).left().width(220);
        add(slider).width(300);
        add(value).width(70).left();
        row();
    }

    /** Refreshes the widgets from the current settings (e.g. after fullscreen changed elsewhere). */
    public void sync() {
        syncing = true;
        volume.setValue(settings.masterVolume());
        sensitivity.setValue(settings.sensitivity());
        invert.setChecked(settings.invertY());
        fullscreen.setChecked(settings.fullscreen());
        reduced.setChecked(settings.reducedEffects());
        updateValues();
        syncing = false;
    }

    private void updateValues() {
        volumeValue.setText(Math.round(settings.masterVolume() * 100) + "%");
        sensitivityValue.setText(String.format("%.2f", settings.sensitivity()));
    }
}
