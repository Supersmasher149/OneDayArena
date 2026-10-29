package com.alteredarena.net;

import com.alteredarena.level.ArenaLayout;
import com.alteredarena.level.LevelData;
import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.Tuning;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** A real host and clients over loopback TCP/UDP, driven frame by frame like the game loop drives them. */
class NetLoopbackTest {
    private static final LevelData LEVEL = ArenaLayout.build();
    private static final float FRAME = 1f / 60f;

    private final List<NetSession> sessions = new ArrayList<>();
    private final List<GameWorld> worlds = new ArrayList<>();

    private final class Recorder implements NetSession.Listener {
        final List<String> notices = new ArrayList<>();
        String disconnected;
        boolean starting;

        @Override public void notice(String text) { notices.add(text); }
        @Override public void disconnected(String reason) { disconnected = reason; }
        @Override public void matchStarting() { starting = true; }
    }

    @AfterEach
    void tearDown() {
        for (NetSession s : sessions) s.close();
        for (GameWorld w : worlds) w.dispose();
    }

    private NetHost host() throws Exception {
        NetHost host = new NetHost("HOST", 0);
        sessions.add(host);
        return host;
    }

    private NetClient join(NetHost host, String name, Recorder rec) {
        NetClient c = new NetClient(name, "127.0.0.1", host.port());
        c.setListener(rec);
        sessions.add(c);
        return c;
    }

    private GameWorld track(GameWorld w) {
        worlds.add(w);
        return w;
    }

    /** Runs lobby frames (no worlds) until the condition holds. */
    private void pump(BooleanSupplier until, NetSession... all) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!until.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out waiting for the network");
            for (NetSession s : all) s.update(null, FRAME);
            Thread.sleep(2);
        }
    }

    /** One game frame on the host (simulate, then send) and on the client (receive, then send input). */
    private void frame(NetHost host, GameWorld hw, NetClient client, GameWorld cw) {
        for (int i = 0; i < 2; i++) {
            host.applyInputs(hw);
            hw.step(Tuning.FIXED_DT);
        }
        host.update(hw, FRAME);
        hw.events.clear();
        if (client != null && !client.matchPending()) {
            client.update(cw, FRAME);
            client.sendInput(cw.player());
        }
    }

    @Test
    void joinStartReplicateAndDrive() throws Exception {
        NetHost host = host();
        Recorder rec = new Recorder();
        NetClient client = join(host, "guest", rec);
        pump(() -> client.state() == NetClient.State.LOBBY && client.lobbyNames().size() == 2, host, client);
        assertEquals(Arrays.asList("HOST", "GUEST"), client.lobbyNames());
        assertEquals("GUEST", client.name());
        assertEquals(Protocol.DEFAULT_TOTAL - 2, client.botCount(), "until the host chooses, bots fill to five combatants");
        host.setBotCount(2);
        pump(() -> client.botCount() == 2, host, client);

        GameWorld hw = track(host.newWorld(LEVEL, Tuning.KILL_LIMIT, 60f));
        pump(() -> rec.starting, host, client);
        GameWorld cw = track(client.newWorld(LEVEL, 0, 0f));
        assertEquals(1, cw.localId);
        assertEquals(hw.combatants.size(), cw.combatants.size());
        assertEquals(4, cw.combatants.size());
        assertEquals("GUEST", cw.player().name);
        assertFalse(cw.player().bot);
        assertTrue(cw.combatant(2).bot);

        // Hold forward: the host moves the guest's combatant from its spawn.
        Combatant hostGuest = hw.combatant(1);
        for (Combatant c : hw.combatants) c.protection = 0f;
        Vector3 spawn = new Vector3(hostGuest.body.position);
        cw.player().yaw = hostGuest.yaw;
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (hostGuest.body.position.dst(spawn) < 2f) {
            assertTrue(System.nanoTime() < deadline, "guest input never moved its combatant on the host");
            cw.player().controls.clear();
            cw.player().controls.forward = 1f;
            frame(host, hw, client, cw);
            Thread.sleep(2);
        }

        // Stop and let the client catch up; its mirror then matches the host.
        for (int i = 0; i < 40; i++) {
            cw.player().controls.clear();
            frame(host, hw, client, cw);
            Thread.sleep(3);
        }
        deadline = System.nanoTime() + 5_000_000_000L;
        while (cw.player().body.position.dst(hostGuest.body.position) > 0.05f) {
            assertTrue(System.nanoTime() < deadline, "client mirror never converged on the host state");
            cw.player().controls.clear();
            frame(host, hw, client, cw);
            Thread.sleep(3);
        }
        for (int i = 0; i < hw.combatants.size(); i++) {
            assertEquals(hw.combatant(i).kills, cw.combatant(i).kills);
            assertEquals(hw.combatant(i).health.current(), cw.combatant(i).health.current(), 1e-3f);
        }
        assertEquals(hw.match.elapsed(), cw.match.elapsed(), 0.25f);
    }

    @Test
    void droppedPlayerIsTakenOverByABotAndLeavesTheLobbyAfterTheMatch() throws Exception {
        NetHost host = host();
        Recorder hostRec = new Recorder();
        host.setListener(hostRec);
        NetClient client = join(host, "guest", new Recorder());
        pump(() -> client.state() == NetClient.State.LOBBY, host, client);
        GameWorld hw = track(host.newWorld(LEVEL, Tuning.KILL_LIMIT, 60f));
        assertNull(hw.brain(1), "a connected guest is not AI-driven");

        client.close();
        pump(() -> hostRec.notices.stream().anyMatch(n -> n.contains("LEFT")), host);
        assertNotNull(hw.brain(1), "a bot took over the guest's combatant");
        assertEquals(2, host.lobbyNames().size(), "the slot stays for the rest of the match");

        hw.dispose();
        worlds.remove(hw);
        GameWorld next = track(host.newWorld(LEVEL, Tuning.KILL_LIMIT, 60f));
        assertEquals(Arrays.asList("HOST"), host.lobbyNames());
        assertEquals("HOST", next.combatant(0).name);
        assertTrue(next.combatant(1).bot);
    }

    @Test
    void refusesJoinsDuringAMatchAndWhenFull() throws Exception {
        NetHost host = host();
        List<NetClient> guests = new ArrayList<>();
        for (int i = 0; i < Protocol.MAX_HUMANS - 1; i++) guests.add(join(host, "g" + i, new Recorder()));
        NetSession[] all = new NetSession[guests.size() + 1];
        all[0] = host;
        for (int i = 0; i < guests.size(); i++) all[i + 1] = guests.get(i);
        pump(() -> host.lobbyNames().size() == Protocol.MAX_HUMANS, all);

        Recorder full = new Recorder();
        NetClient extra = join(host, "late", full);
        pump(() -> full.disconnected != null, host, extra);
        assertEquals("LOBBY IS FULL", full.disconnected);

        guests.get(0).close();
        pump(() -> host.lobbyNames().size() == Protocol.MAX_HUMANS - 1, host);
        track(host.newWorld(LEVEL, Tuning.KILL_LIMIT, 60f));
        Recorder late = new Recorder();
        NetClient tooLate = join(host, "late", late);
        pump(() -> late.disconnected != null, host, tooLate);
        assertEquals("MATCH ALREADY IN PROGRESS", late.disconnected);
    }

    @Test
    void duplicateNamesAreMadeUnique() throws Exception {
        NetHost host = host();
        NetClient a = join(host, "host", new Recorder());
        pump(() -> a.state() == NetClient.State.LOBBY, host, a);
        assertEquals("HOST 2", a.name());
    }

    @Test
    void connectionFailureIsReported() throws Exception {
        NetHost host = host();
        int port = host.port();
        host.close();
        Recorder rec = new Recorder();
        NetClient c = new NetClient("x", "127.0.0.1", port);
        c.setListener(rec);
        sessions.add(c);
        pump(() -> rec.disconnected != null, c);
        assertTrue(rec.disconnected.startsWith("COULD NOT CONNECT"), rec.disconnected);
    }

    @Test
    void snapshotAndEventsRoundTrip() {
        List<String> names = Arrays.asList("A", "B");
        GameWorld hw = track(new GameWorld(LEVEL, 7, names, 0, 2, Tuning.KILL_LIMIT, 60f));
        GameWorld cw = track(new GameWorld(LEVEL, 99, names, 1, 2, Tuning.KILL_LIMIT, 60f));
        for (int i = 0; i < 600; i++) hw.step(Tuning.FIXED_DT);
        Combatant b = hw.combatant(1);
        b.powerUp.apply(PowerUpType.COKE);
        b.weapons[1].restore(3, 11, 0.2f, 0.4f);
        b.weaponIndex = 1;
        b.kills = 4;
        b.yaw = 123f;

        NetBuffer snap = WorldSync.snapshot(hw, 1, 1);
        NetBuffer read = new NetBuffer(snap.toBytes(), snap.size());
        assertEquals(Protocol.SNAPSHOT, read.readU8());
        assertEquals(1, WorldSync.readSnapshotMatch(read));
        read.readI32();
        cw.player().yaw = 5f;
        int ack = WorldSync.applySnapshot(cw, read, -1);
        assertEquals(0, read.remaining());
        assertEquals(b.body.teleportCount, ack);

        Combatant mb = cw.combatant(1);
        assertEquals(123f, mb.yaw, "first snapshot adopts the host's aim for the local player");
        assertEquals(PowerUpType.COKE, mb.powerUp.active());
        assertEquals(1, mb.weaponIndex);
        assertEquals(3, mb.weapons[1].magazine());
        assertEquals(11, mb.weapons[1].reserve());
        assertEquals(4, mb.kills);
        for (int i = 0; i < hw.combatants.size(); i++) {
            assertEquals(hw.combatant(i).body.position, cw.combatant(i).body.position);
            assertEquals(hw.combatant(i).alive, cw.combatant(i).alive);
        }
        assertEquals(hw.match.elapsed(), cw.match.elapsed());
        for (int i = 0; i < hw.pickups.size(); i++) assertEquals(hw.pickups.get(i).active, cw.pickups.get(i).active);

        // Later snapshots keep the client's own aim unless the host teleported it.
        mb.yaw = 40f;
        NetBuffer again = WorldSync.snapshot(hw, 1, 2);
        NetBuffer read2 = new NetBuffer(again.toBytes(), again.size());
        read2.readU8();
        read2.readI32();
        read2.readI32();
        WorldSync.applySnapshot(cw, read2, ack);
        assertEquals(40f, mb.yaw);

        GameEvent kill = new GameEvent(GameEvent.Type.KILL);
        kill.actor = 0;
        kill.target = 1;
        kill.weapon = com.alteredarena.rules.WeaponType.SCATTERGUN;
        kill.position.set(1, 2, 3);
        GameEvent pickup = new GameEvent(GameEvent.Type.PICKUP);
        pickup.actor = 1;
        pickup.pickup = hw.pickups.get(3);
        pickup.powerUp = PowerUpType.ACID;
        pickup.flag = true;
        NetBuffer ev = WorldSync.events(1, Arrays.asList(kill, pickup), 0, 2);
        NetBuffer evRead = new NetBuffer(ev.toBytes(), ev.size());
        assertEquals(Protocol.EVENTS, evRead.readU8());
        assertEquals(1, evRead.readI32());
        cw.events.clear();
        WorldSync.readEvents(cw, evRead);
        assertEquals(2, cw.events.size());
        GameEvent k = cw.events.get(0), p = cw.events.get(1);
        assertEquals(GameEvent.Type.KILL, k.type);
        assertEquals(1, k.target);
        assertEquals(new Vector3(1, 2, 3), k.position);
        assertSame(cw.pickups.get(3), p.pickup);
        assertEquals(PowerUpType.ACID, p.powerUp);
        assertTrue(p.flag);
        assertEquals(-1, cw.events.get(1).target);
    }

    @Test
    void truncatedAndHostileMessagesAreRejected() {
        GameWorld cw = track(new GameWorld(LEVEL, 1, Arrays.asList("A"), 0, 1, Tuning.KILL_LIMIT, 60f));
        GameWorld hw = track(new GameWorld(LEVEL, 1, Arrays.asList("A"), 0, 1, Tuning.KILL_LIMIT, 60f));
        NetBuffer snap = WorldSync.snapshot(hw, 1, 1);
        byte[] bytes = snap.toBytes();
        NetBuffer cut = new NetBuffer(bytes, bytes.length - 3);
        cut.readU8();
        cut.readI32();
        cut.readI32();
        assertThrows(NetBuffer.Malformed.class, () -> WorldSync.applySnapshot(cw, cut, -1));

        NetBuffer badKill = NetBuffer.message(Protocol.EVENTS).i32(1).i16(1);
        badKill.u8(GameEvent.Type.KILL.ordinal()).u8(-1).u8(-1);
        for (int i = 0; i < 7; i++) badKill.f32(0f);
        badKill.bool(false).u8(-1).u8(-1).u8(-1);
        NetBuffer r = new NetBuffer(badKill.toBytes(), badKill.size());
        r.readU8();
        r.readI32();
        assertThrows(NetBuffer.Malformed.class, () -> WorldSync.readEvents(cw, r), "a kill without combatants is refused");

        assertNull(NetClient.parseAddress(""));
        assertNull(NetClient.parseAddress("host:notaport"));
        assertNull(NetClient.parseAddress("host:70000"));
        assertEquals(Protocol.DEFAULT_PORT, NetClient.parseAddress("100.64.1.2").getPort());
        assertEquals(1234, NetClient.parseAddress(" friend.example:1234 ").getPort());
        assertEquals("friend.example", NetClient.parseAddress("friend.example:1234").getHostString());
    }
}
