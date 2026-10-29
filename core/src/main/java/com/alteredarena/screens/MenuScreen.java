package com.alteredarena.screens;

import com.alteredarena.AlteredArenaGame;
import com.alteredarena.LaunchOptions;
import com.alteredarena.net.NetClient;
import com.alteredarena.net.NetHost;
import com.alteredarena.net.NetSession;
import com.alteredarena.net.Protocol;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.ui.Menus;
import com.alteredarena.ui.SettingsPanel;
import com.alteredarena.ui.UiAssets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

/** Main menu over a live bot match seen from an orbiting camera; also hosts and joins online games. */
public final class MenuScreen extends ScreenAdapter {
    private final AlteredArenaGame game;
    private final UiAssets ui;
    private final Stage stage;
    private final Table mainColumn;
    private final Table legend;
    private final SettingsPanel settingsPanel;
    private final Table controls;
    private final Table content = new Table();
    private final Label notice;
    private final Table hostPanel, joinPanel, lobbyPanel;
    private final TextField hostName, hostPort, joinName, joinAddress;
    private final Label hostStatus, joinStatus, lobbyInfo, lobbyStatus;
    private final TextButton connectButton;
    private final Table lobbyPlayers = new Table();
    private final Table lobbyActions = new Table();
    private GameWorld attract;
    private float accumulator, time;
    /** --host/--join act once per run, not every time the menu reopens. */
    private static boolean launchActionDone;

    public MenuScreen(AlteredArenaGame game) {
        this(game, null);
    }

    /** @param noticeText shown under the title, e.g. why an online game ended; may be null */
    public MenuScreen(AlteredArenaGame game, String noticeText) {
        this.game = game;
        this.ui = game.ui;
        stage = new Stage(new ExtendViewport(1280, 720));

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        mainColumn = new Table();
        mainColumn.setBackground(ui.skin.getDrawable("panel"));
        mainColumn.pad(30, 40, 30, 40);
        Label title1 = Menus.title(ui, "ALTERED", UiAssets.MAGENTA);
        Label title2 = Menus.title(ui, "ARENA", UiAssets.CYAN);
        mainColumn.add(title1).left().row();
        mainColumn.add(title2).left().padTop(-24).row();
        Label tagline = new Label("NEON UNDERGROUND DEATHMATCH  •  v0.1", ui.skin, "small");
        tagline.setColor(UiAssets.DIM);
        mainColumn.add(tagline).left().padBottom(noticeText == null ? 26 : 8).row();
        notice = new Label(noticeText == null ? "" : noticeText, ui.skin, "small");
        notice.setColor(UiAssets.YELLOW);
        notice.setWrap(true);
        if (noticeText != null) mainColumn.add(notice).width(300).left().padBottom(18).row();
        mainColumn.defaults().width(300).height(56).padBottom(12).left();
        mainColumn.add(Menus.button(ui, game.audio, "PLAY", game::startMatch)).row();
        mainColumn.add(Menus.button(ui, game.audio, "HOST GAME", this::showHost)).row();
        mainColumn.add(Menus.button(ui, game.audio, "JOIN GAME", this::showJoin)).row();
        mainColumn.add(Menus.button(ui, game.audio, "SETTINGS", this::showSettings)).row();
        mainColumn.add(Menus.button(ui, game.audio, "CONTROLS", this::showControls)).row();
        mainColumn.add(Menus.button(ui, game.audio, "QUIT", () -> Gdx.app.exit())).row();
        Label footer = new Label("Solo: you vs 4 bots  •  first to 15 or best after 5:00", ui.skin, "small");
        footer.setColor(UiAssets.DIM);
        mainColumn.add(footer).height(24).padTop(8).row();

        legend = new Table();
        legend.setBackground(ui.skin.getDrawable("panel"));
        legend.pad(20, 24, 20, 24);
        Label legendTitle = new Label("POWER-UPS", ui.skin, "h2");
        legendTitle.setColor(UiAssets.YELLOW);
        legend.add(legendTitle).colspan(2).left().padBottom(10).row();
        for (PowerUpType t : PowerUpType.values()) {
            legend.add(new Image(ui.icon(t))).size(40).padRight(12).padBottom(8);
            Table text = new Table();
            Label name = new Label(t.label + "  " + (int) t.duration + "s", ui.skin, "h2");
            name.setColor(new Color(t.rgba));
            text.add(name).left().row();
            Label desc = new Label(t.description, ui.skin, "small");
            desc.setColor(UiAssets.DIM);
            text.add(desc).left();
            legend.add(text).left().padBottom(8).row();
        }
        Label note = new Label("Fictional arcade abilities. One at a time.", ui.skin, "small");
        note.setColor(UiAssets.DIM);
        legend.add(note).colspan(2).left().padTop(6);

        settingsPanel = new SettingsPanel(ui, game.settings, new SettingsPanel.Listener() {
            @Override
            public void settingsChanged(boolean displayModeChanged) {
                game.saveSettings();
                if (displayModeChanged) game.applyDisplayMode();
            }

            @Override
            public void back() {
                showMain();
            }
        });
        controls = Menus.controlsTable(ui, this::showMain, game.audio);

        // Host setup
        hostName = nameField();
        hostPort = new TextField(String.valueOf(Protocol.DEFAULT_PORT), ui.skin);
        hostPort.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        hostPort.setMaxLength(5);
        hostStatus = statusLabel();
        hostPanel = panel("HOST GAME", UiAssets.MAGENTA);
        fieldRow(hostPanel, "NAME", hostName);
        fieldRow(hostPanel, "PORT", hostPort);
        hostPanel.add(help("Friends join with one of your addresses and this port. Over the internet, use Tailscale or\n"
                + "ZeroTier, or forward this port (TCP and UDP) on your router.")).colspan(2).left().padTop(6).row();
        hostPanel.add(hostStatus).colspan(2).left().width(560).padTop(8).row();
        hostPanel.add(buttons(Menus.button(ui, game.audio, "OPEN LOBBY", this::startHosting),
                Menus.button(ui, game.audio, "BACK", this::showMain))).colspan(2).left().padTop(14);

        // Join setup
        joinName = nameField();
        joinAddress = new TextField(game.settings.lastAddress(), ui.skin);
        joinAddress.setMessageText("address or address:port");
        joinAddress.setMaxLength(80);
        joinAddress.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') startJoining();
        });
        joinStatus = statusLabel();
        connectButton = Menus.button(ui, game.audio, "CONNECT", this::startJoining);
        joinPanel = panel("JOIN GAME", UiAssets.CYAN);
        fieldRow(joinPanel, "NAME", joinName);
        fieldRow(joinPanel, "ADDRESS", joinAddress);
        joinPanel.add(help("The host's address, e.g. 192.168.1.20 on the same network or 100.x.y.z with Tailscale.\n"
                + "Add :port if the host changed it from " + Protocol.DEFAULT_PORT + ".")).colspan(2).left().padTop(6).row();
        joinPanel.add(joinStatus).colspan(2).left().width(560).padTop(8).row();
        joinPanel.add(buttons(connectButton, Menus.button(ui, game.audio, "BACK", this::leaveSession))).colspan(2).left().padTop(14);

        // Lobby
        lobbyInfo = new Label("", ui.skin, "small");
        lobbyInfo.setColor(UiAssets.DIM);
        lobbyStatus = statusLabel();
        lobbyPanel = panel("LOBBY", UiAssets.YELLOW);
        lobbyPanel.add(lobbyInfo).colspan(2).left().padBottom(12).row();
        lobbyPanel.add(lobbyPlayers).colspan(2).left().width(560).padBottom(10).row();
        lobbyPanel.add(lobbyActions).colspan(2).left().padTop(4).row();
        lobbyPanel.add(lobbyStatus).colspan(2).left().width(560).padTop(8);

        root.add(content).expand().fill().pad(40);
        showMain();
        if (!launchActionDone) {
            launchActionDone = true;
            LaunchOptions o = game.options;
            if (o.name != null) game.settings.setPlayerName(o.name);
            if (o.host) {
                showHost();
                if (o.hostPort > 0) hostPort.setText(String.valueOf(o.hostPort));
                startHosting();
            } else if (o.join != null) {
                showJoin();
                joinAddress.setText(o.join);
                startJoining();
            }
        }
    }

    // ------------------------------------------------------------------ widgets

    private TextField nameField() {
        TextField f = new TextField(game.settings.playerName(), ui.skin);
        f.setMaxLength(14);
        return f;
    }

    private Label statusLabel() {
        Label l = new Label("", ui.skin, "small");
        l.setColor(UiAssets.YELLOW);
        l.setWrap(true);
        return l;
    }

    private Label help(String text) {
        Label l = new Label(text, ui.skin, "small");
        l.setColor(UiAssets.DIM);
        return l;
    }

    private Table panel(String title, Color color) {
        Table t = new Table();
        t.setBackground(ui.skin.getDrawable("panel"));
        t.pad(26, 36, 26, 36);
        Label l = new Label(title, ui.skin, "h1");
        l.setColor(color);
        t.add(l).colspan(2).left().padBottom(16).row();
        return t;
    }

    private void fieldRow(Table t, String label, TextField field) {
        Label l = new Label(label, ui.skin, "h2");
        l.setColor(UiAssets.YELLOW);
        t.add(l).left().padRight(24).padBottom(10);
        t.add(field).width(380).height(46).left().padBottom(10).row();
    }

    private Table buttons(TextButton... list) {
        Table t = new Table();
        t.defaults().width(240).height(54).padRight(12);
        for (TextButton b : list) t.add(b);
        return t;
    }

    // ------------------------------------------------------------------ panels

    private void showMain() {
        content.clearChildren();
        content.add(mainColumn).expand().left().bottom();
        content.add(legend).expand().right().bottom();
    }

    private void showSettings() { show(settingsPanel); }

    private void showControls() { show(controls); }

    private void showHost() {
        hostName.setText(game.settings.playerName());
        hostStatus.setText("");
        show(hostPanel);
        stage.setKeyboardFocus(hostName);
    }

    private void showJoin() {
        joinName.setText(game.settings.playerName());
        joinStatus.setText("");
        connectButton.setDisabled(false);
        show(joinPanel);
        stage.setKeyboardFocus(joinAddress.getText().isEmpty() ? joinAddress : null);
    }

    private void show(Table panel) {
        if (panel == settingsPanel) settingsPanel.sync();
        content.clearChildren();
        content.add(panel).expand().center();
    }

    private boolean showing(Table panel) {
        return content.getChildren().size > 0 && content.getChildren().first() == panel;
    }

    // ------------------------------------------------------------------ online

    private void saveName(TextField field) {
        game.settings.setPlayerName(field.getText());
        field.setText(game.settings.playerName());
        game.saveSettings();
    }

    private void startHosting() {
        saveName(hostName);
        int port;
        try {
            port = Integer.parseInt(hostPort.getText().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 1024 || port > 65535) {
            hostStatus.setText("CHOOSE A PORT FROM 1024 TO 65535");
            return;
        }
        try {
            game.endSession();
            NetHost host = new NetHost(game.settings.playerName(), port);
            Gdx.app.log("Net", "hosting on port " + port + ", addresses " + NetHost.localAddresses());
            game.session = host;
            listen(host);
            showLobby();
        } catch (IOException e) {
            hostStatus.setText("COULD NOT OPEN PORT " + port + ": " + String.valueOf(e.getMessage()).toUpperCase()
                    + "\nIs another copy of the game already hosting?");
        }
    }

    private void startJoining() {
        if (game.session != null) return;
        saveName(joinName);
        InetSocketAddress address = NetClient.parseAddress(joinAddress.getText());
        if (address == null) {
            joinStatus.setText("ENTER THE HOST'S ADDRESS, e.g. 192.168.1.20 or 100.101.102.103:" + Protocol.DEFAULT_PORT);
            return;
        }
        game.settings.setLastAddress(joinAddress.getText());
        game.saveSettings();
        NetClient client = new NetClient(game.settings.playerName(), address.getHostString(), address.getPort());
        game.session = client;
        listen(client);
        connectButton.setDisabled(true);
        joinStatus.setText("CONNECTING TO " + address.getHostString().toUpperCase() + ":" + address.getPort() + "...");
    }

    private void listen(NetSession session) {
        session.setListener(new NetSession.Listener() {
            @Override
            public void lobbyChanged() {
                Gdx.app.log("Net", "lobby " + session.lobbyNames() + " + " + session.botCount() + " bots");
                if (!showing(lobbyPanel)) showLobby();
                else refreshLobby();
            }

            @Override
            public void matchStarting() {
                game.startMatch();
            }

            @Override
            public void notice(String text) {
                Gdx.app.log("Net", text);
                lobbyStatus.setText(text);
            }

            @Override
            public void disconnected(String reason) {
                Gdx.app.log("Net", "disconnected: " + reason);
                game.session = null;
                showJoin();
                joinStatus.setText(reason);
            }
        });
    }

    private void leaveSession() {
        game.endSession();
        showMain();
    }

    private void showLobby() {
        lobbyStatus.setText("");
        refreshLobby();
        show(lobbyPanel);
    }

    private void refreshLobby() {
        NetSession session = game.session;
        if (session == null) return;
        NetHost host = session instanceof NetHost ? (NetHost) session : null;
        if (host != null) {
            StringBuilder sb = new StringBuilder("HOSTING ON PORT " + host.port() + "  •  YOUR ADDRESSES: ");
            List<String> addresses = NetHost.localAddresses();
            if (addresses.isEmpty()) sb.append("NONE FOUND");
            for (int i = 0; i < addresses.size(); i++) {
                String a = addresses.get(i);
                if (i > 0) sb.append("   ");
                sb.append(a).append(isTailscale(a) ? " (TAILSCALE)" : "");
            }
            lobbyInfo.setText(sb.toString());
        } else {
            lobbyInfo.setText("CONNECTED  •  THE HOST STARTS THE MATCH");
        }

        lobbyPlayers.clearChildren();
        List<String> names = session.lobbyNames();
        for (int i = 0; i < names.size(); i++) {
            Label n = new Label((i + 1) + "   " + names.get(i) + (i == 0 ? "   (HOST)" : ""), ui.skin, "h2");
            n.setColor(new Color(GameWorld.COLORS[i % GameWorld.COLORS.length]));
            lobbyPlayers.add(n).left().padBottom(4).row();
        }
        int bots = session.botCount();
        Label botLine = new Label("+ " + bots + (bots == 1 ? " BOT" : " BOTS"), ui.skin, "h2");
        botLine.setColor(UiAssets.DIM);
        lobbyPlayers.add(botLine).left().padTop(4).row();
        Label room = new Label(names.size() + " / " + Protocol.MAX_HUMANS + " PLAYERS", ui.skin, "small");
        room.setColor(UiAssets.DIM);
        lobbyPlayers.add(room).left().padTop(6).row();

        lobbyActions.clearChildren();
        lobbyActions.defaults().height(54).padRight(12);
        if (host != null) {
            Label botsLabel = new Label("BOTS  " + bots, ui.skin, "h2");
            lobbyActions.add(Menus.button(ui, game.audio, "-", () -> host.setBotCount(host.botCount() - 1))).width(60);
            lobbyActions.add(botsLabel).width(120).center();
            lobbyActions.add(Menus.button(ui, game.audio, "+", () -> host.setBotCount(host.botCount() + 1))).width(60);
            lobbyActions.add(Menus.button(ui, game.audio, "START MATCH", game::startMatch)).width(240);
        } else {
            Label waiting = new Label("WAITING FOR THE HOST", ui.skin, "h2");
            waiting.setColor(UiAssets.DIM);
            lobbyActions.add(waiting).width(360).left();
        }
        lobbyActions.add(Menus.button(ui, game.audio, "LEAVE", this::leaveSession)).width(160);
    }

    /** Tailscale hands out addresses from 100.64.0.0/10. */
    private static boolean isTailscale(String ipv4) {
        String[] parts = ipv4.split("\\.");
        if (parts.length != 4 || !parts[0].equals("100")) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 64 && second <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ screen

    @Override
    public void show() {
        attract = new GameWorld(game.level, MathUtils.random(Long.MAX_VALUE), Tuning.BOT_COUNT, true);
        game.renderer.bind(attract);
        Gdx.input.setCursorCatched(false);
        Gdx.input.setInputProcessor(new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE && !showing(lobbyPanel)) {
                    if (showing(joinPanel)) leaveSession();
                    else showMain();
                    return true;
                }
                return false;
            }
        }));
    }

    @Override
    public void render(float delta) {
        if (game.session != null) {
            game.session.update(null, delta);
            int startWhen = game.options.startWhen;
            if (startWhen > 0 && game.session instanceof NetHost && showing(lobbyPanel)
                    && game.session.lobbyNames().size() >= startWhen) {
                content.clearChildren();
                game.startMatch();
            }
        }

        float frame = Math.min(delta, Tuning.MAX_FRAME_TIME);
        time += frame;
        accumulator += frame;
        int steps = 0;
        while (accumulator >= Tuning.FIXED_DT && steps < Tuning.MAX_STEPS_PER_FRAME) {
            attract.step(Tuning.FIXED_DT);
            accumulator -= Tuning.FIXED_DT;
            steps++;
        }
        if (!attract.match.isRunning()) {
            attract.dispose();
            attract = new GameWorld(game.level, MathUtils.random(Long.MAX_VALUE), Tuning.BOT_COUNT, true);
            game.renderer.bind(attract);
        }
        for (GameEvent e : attract.events) game.renderer.onEvent(e, attract, -1, game.settings.reducedEffects());
        attract.events.clear();

        float angle = time * 0.06f;
        game.renderer.camera.position.set(MathUtils.cos(angle) * 30f, 15f, MathUtils.sin(angle) * 30f);
        game.renderer.camera.lookAt(0f, 2.5f, 0f);
        game.renderer.camera.up.set(0f, 1f, 0f);
        game.renderer.camera.fieldOfView = 60f;
        game.renderer.camera.update();
        game.renderer.update(frame);
        game.renderer.render(attract, accumulator / Tuning.FIXED_DT, -1);

        stage.getViewport().apply();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        game.renderer.resize(width, height);
    }

    @Override
    public void hide() {
        if (attract != null) {
            attract.dispose();
            attract = null;
        }
    }

    @Override
    public void dispose() {
        hide();
        stage.dispose();
    }
}
