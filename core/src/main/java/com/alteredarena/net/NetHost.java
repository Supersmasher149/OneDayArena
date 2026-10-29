package com.alteredarena.net;

import com.alteredarena.level.LevelData;
import com.alteredarena.settings.Settings;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.Controls;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Listen server: runs the authoritative {@link GameWorld} on this machine (the host is combatant 0), applies
 * each client's input before every step and broadcasts snapshots and events. Players who drop mid-match are
 * handed to a bot.
 */
public final class NetHost extends NetSession {
    /** One connected client. Fields are touched only on the game thread. */
    private static final class Peer {
        final Link link;
        final int token;
        String name;
        boolean welcomed;
        SocketAddress udpAddress;
        boolean udpConfirmed;
        long lastHeard;
        int combatantId = -1;
        final WorldSync.Input input = new WorldSync.Input();
        boolean hasInput;
        boolean pendingJump, pendingReload;
        int pendingWeapon = -1;

        Peer(Link link, int token) {
            this.link = link;
            this.token = token;
        }
    }

    private final ServerSocket server;
    private final DatagramSocket udp;
    private final SecureRandom random = new SecureRandom();
    private final List<Peer> peers = new ArrayList<>();
    /** Players who dropped mid-match; their bot keeps the slot until the next match. */
    private final List<String> leftDuringMatch = new ArrayList<>();
    private GameWorld world;
    private int matchId, snapshotSequence;
    /** False until the host picks a bot count; until then bots fill to {@link Protocol#DEFAULT_TOTAL}. */
    private boolean botsChosen;
    private float snapshotTimer, pingTimer;

    /**
     * Opens TCP and UDP on {@code port} (0 picks a free one) and starts accepting players.
     *
     * @throws IOException if the port is taken or cannot be opened
     */
    public NetHost(String hostName, int port) throws IOException {
        server = new ServerSocket(port);
        try {
            udp = new DatagramSocket(server.getLocalPort());
        } catch (IOException e) {
            server.close();
            throw e;
        }
        lobby.add(hostName);
        fitBots();
        Thread accept = new Thread(this::acceptLoop, "net-accept");
        accept.setDaemon(true);
        accept.start();
        Thread receive = new Thread(this::udpLoop, "net-udp");
        receive.setDaemon(true);
        receive.start();
    }

    @Override
    public boolean isHost() { return true; }

    public int port() { return server.getLocalPort(); }

    /** Sets the bots for the next match, limited by the free slots. */
    public void setBotCount(int bots) {
        botsChosen = true;
        botCount = bots;
        fitBots();
        broadcastLobby();
        listener.lobbyChanged();
    }

    private void fitBots() {
        if (!botsChosen) botCount = Math.max(1, Protocol.DEFAULT_TOTAL - lobby.size());
        botCount = Math.max(0, Math.min(maxBots(), botCount));
    }

    public int maxBots() { return Protocol.MAX_COMBATANTS - lobby.size(); }

    // ------------------------------------------------------------------ network threads

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Peer[] holder = new Peer[1];
                Link link = new Link(socket,
                        frame -> inbox.add(new Inbound(holder[0], frame, false, false, false)),
                        () -> inbox.add(new Inbound(holder[0], null, false, false, true)));
                holder[0] = new Peer(link, random.nextInt());
                inbox.add(new Inbound(holder[0], null, false, true, false));
                link.start("net-peer");
            } catch (IOException e) {
                // Server closed, or one bad accept; keep going while open.
            }
        }
    }

    private void udpLoop() {
        byte[] buffer = new byte[Protocol.MAX_DATAGRAM + 100];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!udp.isClosed()) {
            try {
                udp.receive(packet);
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                inbox.add(new Inbound(packet.getSocketAddress(), data, true, false, false));
            } catch (IOException e) {
                // Socket closed, or a transient error.
            }
        }
    }

    // ------------------------------------------------------------------ game thread

    @Override
    public void update(GameWorld world, float delta) {
        if (closed) return;
        long now = System.nanoTime();
        Inbound in;
        while ((in = inbox.poll()) != null) {
            if (in.udp) {
                handleDatagram((SocketAddress) in.from, in.data, now);
                continue;
            }
            Peer peer = (Peer) in.from;
            if (in.opened) {
                peer.lastHeard = now;
                peers.add(peer);
            } else if (in.closed) {
                dropped(peer);
            } else {
                peer.lastHeard = now;
                try {
                    handle(peer, new NetBuffer(in.data, in.data.length));
                } catch (NetBuffer.Malformed e) {
                    peer.link.close();
                }
            }
        }

        for (Peer p : peers) {
            if ((now - p.lastHeard) / 1e9f > Protocol.TIMEOUT) p.link.close();
        }
        pingTimer += delta;
        if (pingTimer >= Protocol.PING_INTERVAL) {
            pingTimer = 0f;
            for (Peer p : peers) p.link.send(NetBuffer.message(Protocol.PING));
        }

        if (world == null || world != this.world) return;
        List<GameEvent> events = world.events;
        for (int from = 0; from < events.size(); from += WorldSync.EVENTS_PER_MESSAGE) {
            NetBuffer message = WorldSync.events(matchId, events, from, Math.min(events.size(), from + WorldSync.EVENTS_PER_MESSAGE));
            for (Peer p : players()) p.link.send(message);
        }
        snapshotTimer += delta;
        if (snapshotTimer >= Protocol.SNAPSHOT_INTERVAL) {
            snapshotTimer = Math.min(snapshotTimer - Protocol.SNAPSHOT_INTERVAL, Protocol.SNAPSHOT_INTERVAL);
            NetBuffer snapshot = WorldSync.snapshot(world, matchId, ++snapshotSequence);
            for (Peer p : players()) sendUnreliable(p, snapshot);
        }
    }

    private void handle(Peer peer, NetBuffer b) {
        int type = b.readU8();
        if (!peer.welcomed && type != Protocol.HELLO) {
            peer.link.close();
            return;
        }
        switch (type) {
            case Protocol.HELLO:
                hello(peer, b);
                break;
            case Protocol.INPUT:
                b.readI32(); // token: the TCP connection already identifies the peer
                readInput(peer, b);
                break;
            case Protocol.PING:
                break;
            default:
                peer.link.close();
                break;
        }
    }

    private void hello(Peer peer, NetBuffer b) {
        if (peer.welcomed) return;
        int version = b.readI32();
        String name = b.readStr();
        String refusal = null;
        if (version != Protocol.VERSION) refusal = "VERSION MISMATCH (HOST v" + Protocol.VERSION + ", YOU v" + version + ")";
        else if (world != null) refusal = "MATCH ALREADY IN PROGRESS";
        else if (lobby.size() >= Protocol.MAX_HUMANS) refusal = "LOBBY IS FULL";
        if (refusal != null) {
            peer.link.sendAndClose(NetBuffer.message(Protocol.REJECT).str(refusal));
            return;
        }
        peer.name = uniqueName(Settings.sanitizeName(name));
        peer.welcomed = true;
        lobby.add(peer.name);
        fitBots();
        peer.link.send(NetBuffer.message(Protocol.WELCOME).i32(peer.token).str(peer.name));
        broadcastLobby();
        listener.lobbyChanged();
        notice(peer.name + " JOINED");
    }

    private String uniqueName(String name) {
        String base = name.isEmpty() ? "PLAYER" : name;
        String candidate = base;
        for (int n = 2; lobby.contains(candidate); n++) candidate = base + " " + n;
        return candidate;
    }

    private void handleDatagram(SocketAddress from, byte[] data, long now) {
        NetBuffer b = new NetBuffer(data, data.length);
        try {
            if (b.readU8() != Protocol.INPUT) return;
            int token = b.readI32();
            for (Peer p : peers) {
                if (!p.welcomed || p.token != token) continue;
                p.lastHeard = now;
                p.udpAddress = from; // follows NAT rebinding
                if (!p.udpConfirmed) {
                    p.udpConfirmed = true;
                    p.link.send(NetBuffer.message(Protocol.UDP_OK));
                }
                readInput(p, b);
                return;
            }
        } catch (NetBuffer.Malformed e) {
            // Stray or corrupt datagram.
        }
    }

    private void readInput(Peer p, NetBuffer b) {
        WorldSync.Input fresh = new WorldSync.Input();
        fresh.read(b);
        if (p.hasInput && fresh.sequence - p.input.sequence <= 0) return; // stale or duplicate
        if (p.hasInput) {
            if (fresh.jumps != p.input.jumps) p.pendingJump = true;
            if (fresh.reloads != p.input.reloads) p.pendingReload = true;
            if (fresh.weaponPresses != p.input.weaponPresses) p.pendingWeapon = fresh.weaponSlot;
        }
        WorldSync.Input held = p.input;
        held.sequence = fresh.sequence;
        held.forward = fresh.forward;
        held.strafe = fresh.strafe;
        held.yaw = fresh.yaw;
        held.pitch = fresh.pitch;
        held.fire = fresh.fire;
        held.jumps = fresh.jumps;
        held.reloads = fresh.reloads;
        held.weaponPresses = fresh.weaponPresses;
        held.weaponSlot = fresh.weaponSlot;
        held.teleportAck = fresh.teleportAck;
        p.hasInput = true;
    }

    /** Writes each remote player's latest input into their combatant; call before every simulation step. */
    public void applyInputs(GameWorld world) {
        if (world != this.world) return;
        for (Peer p : players()) {
            Combatant c = world.combatant(p.combatantId);
            Controls controls = c.controls;
            controls.clear();
            if (!p.hasInput) continue;
            WorldSync.Input in = p.input;
            controls.forward = in.forward;
            controls.strafe = in.strafe;
            controls.fire = in.fire;
            controls.jump = p.pendingJump;
            controls.reload = p.pendingReload;
            if (p.pendingWeapon >= 0 && p.pendingWeapon < c.weapons.length) controls.selectWeapon = p.pendingWeapon;
            p.pendingJump = p.pendingReload = false;
            p.pendingWeapon = -1;
            // Aim the client chose before its latest respawn would undo the spawn facing.
            if (c.alive && in.teleportAck == c.body.teleportCount) {
                c.yaw = in.yaw;
                c.pitch = in.pitch;
            }
        }
    }

    @Override
    public GameWorld newWorld(LevelData level, int killLimit, float duration) {
        if (!leftDuringMatch.isEmpty()) {
            lobby.removeAll(leftDuringMatch);
            leftDuringMatch.clear();
            fitBots();
            broadcastLobby();
            listener.lobbyChanged();
        }
        long seed = random.nextLong();
        matchId++;
        snapshotTimer = Protocol.SNAPSHOT_INTERVAL; // send the first snapshot straight away
        int bots = botCount;
        world = new GameWorld(level, seed, new ArrayList<>(lobby), 0, bots, killLimit, duration);
        for (Peer p : peers) {
            if (!p.welcomed) continue;
            p.combatantId = lobby.indexOf(p.name);
            p.hasInput = false;
            p.pendingJump = p.pendingReload = false;
            p.pendingWeapon = -1;
            NetBuffer start = NetBuffer.message(Protocol.START);
            start.i32(matchId).i64(seed).i16(killLimit).f32(duration).u8(bots).u8(p.combatantId).u8(lobby.size());
            for (String name : lobby) start.str(name);
            p.link.send(start);
        }
        return world;
    }

    private List<Peer> players() {
        List<Peer> out = new ArrayList<>(peers.size());
        for (Peer p : peers) if (p.welcomed && p.combatantId >= 0 && world != null) out.add(p);
        return out;
    }

    private void sendUnreliable(Peer p, NetBuffer message) {
        if (p.udpAddress != null && message.size() <= Protocol.MAX_DATAGRAM) {
            try {
                udp.send(new DatagramPacket(message.toBytes(), message.size(), p.udpAddress));
                return;
            } catch (IOException e) {
                // Fall back to TCP for this one.
            }
        }
        p.link.send(message);
    }

    private void dropped(Peer peer) {
        if (!peers.remove(peer) || !peer.welcomed) return;
        if (world != null && peer.combatantId >= 0) {
            world.giveToBot(peer.combatantId);
            leftDuringMatch.add(peer.name);
            notice(peer.name + " LEFT  •  A BOT TOOK OVER");
        } else {
            lobby.remove(peer.name);
            fitBots();
            broadcastLobby();
            listener.lobbyChanged();
            notice(peer.name + " LEFT");
        }
    }

    private void broadcastLobby() {
        NetBuffer b = NetBuffer.message(Protocol.LOBBY).u8(botCount).u8(lobby.size());
        for (String name : lobby) b.str(name);
        for (Peer p : peers) if (p.welcomed) p.link.send(b);
    }

    /** Shows a message here and on every client. */
    private void notice(String text) {
        NetBuffer b = NetBuffer.message(Protocol.NOTICE).str(text);
        for (Peer p : peers) if (p.welcomed) p.link.send(b);
        listener.notice(text);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Peer p : peers) p.link.sendAndClose(NetBuffer.message(Protocol.REJECT).str("THE HOST CLOSED THE GAME"));
        peers.clear();
        try {
            server.close();
        } catch (IOException ignored) {
            // Already closed.
        }
        udp.close();
    }

    /** This machine's IPv4 addresses that friends could connect to, Tailscale/ZeroTier style ones included. */
    public static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return out;
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLinkLocalAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (SocketException e) {
            // No interfaces to list.
        }
        Collections.sort(out);
        return out;
    }
}
