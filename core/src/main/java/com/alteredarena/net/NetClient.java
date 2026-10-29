package com.alteredarena.net;

import com.alteredarena.level.LevelData;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.Controls;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Joins a {@link NetHost}. Phase 1 does no prediction: the world shown is the host's latest snapshot,
 * drawn one snapshot behind so movement interpolates smoothly. Only the local aim is applied immediately.
 */
public final class NetClient extends NetSession {
    public enum State { CONNECTING, LOBBY, IN_MATCH }

    private final String requestedName;
    private volatile Link link;
    private volatile DatagramSocket udp;
    private State state = State.CONNECTING;
    private String name;
    private int token;
    private boolean udpConfirmed;
    private long lastHeard = System.nanoTime();
    private float pingTimer;

    private int matchId = -1, lastSnapshot;
    private long seed;
    private int killLimit, yourId;
    private float duration;
    private final List<String> matchNames = new ArrayList<>();
    private boolean startPending;
    private GameWorld world;
    private float sinceSnapshot;
    private int teleportAck = -1;
    private final WorldSync.Input input = new WorldSync.Input();

    /** Starts connecting in the background; progress arrives through the listener. */
    public NetClient(String name, String host, int port) {
        this.requestedName = name;
        Thread t = new Thread(() -> connect(host, port), "net-connect");
        t.setDaemon(true);
        t.start();
    }

    /** Splits "host" or "host:port"; the port defaults to {@link Protocol#DEFAULT_PORT}. Null if unusable. */
    public static InetSocketAddress parseAddress(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) return null;
        int port = Protocol.DEFAULT_PORT;
        int colon = s.lastIndexOf(':');
        if (colon >= 0 && s.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(s.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
            s = s.substring(0, colon).trim();
        }
        if (s.isEmpty() || port <= 0 || port > 65535) return null;
        return InetSocketAddress.createUnresolved(s, port);
    }

    private void connect(String host, int port) {
        Socket socket = new Socket();
        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            if (address.isUnresolved()) throw new IOException("UNKNOWN HOST " + host.toUpperCase());
            socket.connect(address, Protocol.CONNECT_TIMEOUT_MS);
            DatagramSocket datagrams = new DatagramSocket();
            datagrams.connect(address);
            Link l = new Link(socket,
                    frame -> inbox.add(new Inbound(null, frame, false, false, false)),
                    () -> inbox.add(new Inbound(null, null, false, false, true)));
            synchronized (this) {
                if (closed) {
                    socket.close();
                    datagrams.close();
                    return;
                }
                link = l;
                udp = datagrams;
            }
            l.start("net-client");
            l.send(NetBuffer.message(Protocol.HELLO).i32(Protocol.VERSION).str(requestedName));
            Thread receive = new Thread(() -> udpLoop(datagrams), "net-udp");
            receive.setDaemon(true);
            receive.start();
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Never opened.
            }
            inbox.add(new Inbound("COULD NOT CONNECT: " + describe(e).toUpperCase(), null, false, false, true));
        }
    }

    private void udpLoop(DatagramSocket socket) {
        byte[] buffer = new byte[Protocol.MAX_DATAGRAM + 100];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!socket.isClosed()) {
            try {
                socket.receive(packet);
                inbox.add(new Inbound(null, Arrays.copyOf(packet.getData(), packet.getLength()), true, false, false));
            } catch (IOException e) {
                // Closed, or ICMP port unreachable before the host is listening; keep trying while open.
                if (socket.isClosed()) return;
            }
        }
    }

    @Override
    public boolean isHost() { return false; }

    public State state() { return state; }

    /** The name the host gave us (made unique), once welcomed. */
    public String name() { return name; }

    // ------------------------------------------------------------------ game thread

    @Override
    public void update(GameWorld world, float delta) {
        if (closed) return;
        sinceSnapshot += delta;
        long now = System.nanoTime();
        Inbound in;
        while (!startPending && (in = inbox.poll()) != null) {
            if (in.closed) {
                disconnect(in.from instanceof String ? (String) in.from : "LOST CONNECTION TO THE HOST");
                return;
            }
            lastHeard = now;
            try {
                handle(new NetBuffer(in.data, in.data.length), world);
            } catch (NetBuffer.Malformed e) {
                disconnect("BAD DATA FROM THE HOST");
                return;
            }
            if (closed) return;
        }
        if (state != State.CONNECTING && (now - lastHeard) / 1e9f > Protocol.TIMEOUT) {
            disconnect("CONNECTION TIMED OUT");
            return;
        }
        pingTimer += delta;
        if (pingTimer >= Protocol.PING_INTERVAL && link != null) {
            pingTimer = 0f;
            link.send(NetBuffer.message(Protocol.PING));
        }
    }

    private void handle(NetBuffer b, GameWorld world) {
        int type = b.readU8();
        switch (type) {
            case Protocol.WELCOME:
                token = b.readI32();
                name = b.readStr();
                state = State.LOBBY;
                listener.lobbyChanged();
                break;
            case Protocol.REJECT:
                disconnect(b.readStr());
                break;
            case Protocol.LOBBY: {
                botCount = b.readU8();
                int n = b.readU8();
                lobby.clear();
                for (int i = 0; i < n; i++) lobby.add(b.readStr());
                listener.lobbyChanged();
                break;
            }
            case Protocol.START: {
                matchId = b.readI32();
                seed = b.readI64();
                killLimit = b.readI16();
                duration = b.readF32();
                botCount = b.readU8();
                yourId = b.readU8();
                int n = b.readU8();
                matchNames.clear();
                for (int i = 0; i < n; i++) matchNames.add(b.readStr());
                if (yourId >= n || n + botCount > Protocol.MAX_COMBATANTS || killLimit <= 0 || !(duration > 0f)) {
                    throw new NetBuffer.Malformed("bad match settings");
                }
                // Stop reading: what follows belongs to the new world, which does not exist yet.
                startPending = true;
                listener.matchStarting();
                break;
            }
            case Protocol.SNAPSHOT: {
                if (world == null || world != this.world || WorldSync.readSnapshotMatch(b) != matchId) break;
                int sequence = b.readI32();
                if (sequence - lastSnapshot <= 0) break; // late or duplicated datagram
                lastSnapshot = sequence;
                teleportAck = WorldSync.applySnapshot(world, b, teleportAck);
                sinceSnapshot = 0f;
                break;
            }
            case Protocol.EVENTS: {
                if (world == null || world != this.world || b.readI32() != matchId) break;
                int first = world.events.size();
                WorldSync.readEvents(world, b);
                Combatant me = world.player();
                for (int i = first; i < world.events.size(); i++) {
                    GameEvent e = world.events.get(i);
                    if (e.type == GameEvent.Type.SHOT && e.actor == me.id && e.weapon != null) me.applyRecoil(e.weapon);
                }
                break;
            }
            case Protocol.NOTICE:
                listener.notice(b.readStr());
                break;
            case Protocol.UDP_OK:
                udpConfirmed = true;
                break;
            case Protocol.PING:
                break;
            default:
                throw new NetBuffer.Malformed("unknown message " + type);
        }
    }

    @Override
    public GameWorld newWorld(LevelData level, int ignoredKillLimit, float ignoredDuration) {
        if (!startPending && world != null) return world;
        startPending = false;
        state = State.IN_MATCH;
        lastSnapshot = 0;
        teleportAck = -1;
        sinceSnapshot = 0f;
        world = new GameWorld(level, seed, new ArrayList<>(matchNames), yourId, botCount, killLimit, duration);
        return world;
    }

    /** Whether a match has been announced and {@link #newWorld} should be called. */
    public boolean matchPending() { return startPending; }

    /** How far to blend from the previous snapshot to the latest (0..1). */
    public float interpolationAlpha() {
        return Math.min(1f, sinceSnapshot / Protocol.SNAPSHOT_INTERVAL);
    }

    /** Sends the local player's sampled controls and current aim. */
    public void sendInput(Combatant local) {
        Link l = link;
        if (closed || l == null || state != State.IN_MATCH) return;
        Controls c = local.controls;
        input.sequence++;
        input.forward = c.forward;
        input.strafe = c.strafe;
        input.fire = c.fire;
        input.yaw = local.yaw;
        input.pitch = local.pitch;
        if (c.jump) input.jumps = (input.jumps + 1) & 0xFF;
        if (c.reload) input.reloads = (input.reloads + 1) & 0xFF;
        if (c.selectWeapon >= 0) {
            input.weaponPresses = (input.weaponPresses + 1) & 0xFF;
            input.weaponSlot = c.selectWeapon;
        }
        input.teleportAck = teleportAck;
        NetBuffer message = input.write(token);
        DatagramSocket u = udp;
        if (u != null) {
            try {
                u.send(new DatagramPacket(message.toBytes(), message.size()));
            } catch (IOException e) {
                // UDP unavailable; TCP below still delivers.
            }
        }
        if (!udpConfirmed) l.send(message);
    }

    private void disconnect(String reason) {
        close();
        listener.disconnected(reason);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (link != null) link.close();
        if (udp != null) udp.close();
    }
}
