package com.alteredarena.net;

import com.alteredarena.rules.PowerUpType;
import com.alteredarena.rules.WeaponState;
import com.alteredarena.rules.WeaponType;
import com.alteredarena.sim.Body;
import com.alteredarena.sim.Combatant;
import com.alteredarena.sim.GameEvent;
import com.alteredarena.sim.GameWorld;
import com.alteredarena.sim.Match;
import com.alteredarena.sim.Pickup;
import com.badlogic.gdx.math.Vector3;

import java.util.List;

/** Encodes the host's world into snapshots and events, and applies them to a client's mirror of the world. */
final class WorldSync {
    private WorldSync() {}

    private static final Match.Phase[] PHASES = Match.Phase.values();
    private static final Match.EndReason[] REASONS = Match.EndReason.values();
    private static final PowerUpType[] POWER_UPS = PowerUpType.values();
    private static final WeaponType[] WEAPONS = WeaponType.values();
    private static final GameEvent.Type[] EVENT_TYPES = GameEvent.Type.values();
    /** Events per message, so a burst never exceeds the frame limit (~40 bytes each). */
    static final int EVENTS_PER_MESSAGE = 1000;

    // ------------------------------------------------------------------ snapshots

    static NetBuffer snapshot(GameWorld w, int matchId, int sequence) {
        NetBuffer b = NetBuffer.message(Protocol.SNAPSHOT);
        b.i32(matchId).i32(sequence).f32(w.time());
        b.f32(w.match.elapsed()).writeEnum(w.match.phase()).writeEnum(w.match.endReason());
        b.u8(w.combatants.size());
        for (Combatant c : w.combatants) {
            Body body = c.body;
            vec(b, body.position);
            vec(b, body.velocity);
            b.f32(c.yaw).f32(c.pitch);
            b.u8((c.alive ? 1 : 0) | (body.grounded ? 2 : 0));
            b.i32(body.teleportCount);
            b.f32(c.health.current()).f32(c.protection).f32(c.respawnTimer);
            b.u8(c.weaponIndex).f32(c.switchTimer);
            for (WeaponState ws : c.weapons) {
                b.i16(ws.magazine()).i16(ws.reserve()).f32(ws.cooldown()).f32(ws.reloadRemaining());
            }
            b.writeEnum(c.powerUp.active()).f32(c.powerUp.remaining());
            b.i16(c.kills).i16(c.deaths).u8(c.lastAttackerId);
            b.f32(c.timeSinceDamaged).f32(c.timeSinceFired);
        }
        b.u8(w.pickups.size());
        for (Pickup p : w.pickups) b.bool(p.active).f32(p.timer);
        return b;
    }

    /** Header fields read before deciding whether a snapshot is worth applying. */
    static int readSnapshotMatch(NetBuffer b) { return b.readI32(); }

    /**
     * Applies a snapshot body (after the match id and sequence) to a client world. The local combatant keeps
     * its own aim unless the host teleported it.
     *
     * @return the local combatant's teleport count from the host
     */
    static int applySnapshot(GameWorld w, NetBuffer b, int localTeleportAck) {
        w.restoreTime(b.readF32());
        float elapsed = b.readF32();
        Match.Phase phase = b.readEnum(PHASES);
        Match.EndReason reason = b.readEnum(REASONS);
        if (phase == null) throw new NetBuffer.Malformed("no phase");
        w.match.restore(elapsed, phase, reason);

        int count = b.readU8();
        if (count != w.combatants.size()) throw new NetBuffer.Malformed("combatant count " + count);
        int localTeleports = localTeleportAck;
        Vector3 pos = new Vector3();
        for (Combatant c : w.combatants) {
            Body body = c.body;
            readVec(b, pos);
            readVec(b, body.velocity);
            float yaw = b.readF32(), pitch = b.readF32();
            int flags = b.readU8();
            boolean alive = (flags & 1) != 0;
            int teleports = b.readI32();
            // Interpolate from the last snapshot unless the host moved the body discontinuously.
            boolean jump = teleports != body.teleportCount || alive != c.alive;
            body.previousPosition.set(jump ? pos : body.position);
            body.position.set(pos);
            body.teleportCount = teleports;
            body.grounded = (flags & 2) != 0;
            c.alive = alive;
            if (c.id != w.localId) {
                c.yaw = yaw;
                c.pitch = pitch;
            } else {
                localTeleports = teleports;
                if (teleports != localTeleportAck) {
                    c.yaw = yaw;
                    c.pitch = pitch;
                }
            }
            c.health.set(b.readF32());
            c.protection = b.readF32();
            c.respawnTimer = b.readF32();
            int weaponIndex = b.readU8();
            if (weaponIndex >= c.weapons.length) throw new NetBuffer.Malformed("weapon index " + weaponIndex);
            c.weaponIndex = weaponIndex;
            c.switchTimer = b.readF32();
            for (WeaponState ws : c.weapons) {
                int magazine = b.readI16(), reserve = b.readI16();
                ws.restore(magazine, reserve, b.readF32(), b.readF32());
            }
            PowerUpType power = b.readEnum(POWER_UPS);
            c.powerUp.restore(power, b.readF32());
            c.kills = b.readI16();
            c.deaths = b.readI16();
            c.lastAttackerId = b.readI8();
            c.timeSinceDamaged = b.readF32();
            c.timeSinceFired = b.readF32();
        }
        int pickups = b.readU8();
        if (pickups != w.pickups.size()) throw new NetBuffer.Malformed("pickup count " + pickups);
        for (Pickup p : w.pickups) {
            p.active = b.readBool();
            p.timer = b.readF32();
        }
        return localTeleports;
    }

    // ------------------------------------------------------------------ events

    /** Writes events [from, to) into one message. */
    static NetBuffer events(int matchId, List<GameEvent> events, int from, int to) {
        NetBuffer b = NetBuffer.message(Protocol.EVENTS);
        b.i32(matchId).i16(to - from);
        for (int i = from; i < to; i++) {
            GameEvent e = events.get(i);
            b.u8(e.type.ordinal()).u8(e.actor).u8(e.target);
            vec(b, e.position);
            vec(b, e.position2);
            b.f32(e.value).bool(e.flag).writeEnum(e.weapon).writeEnum(e.powerUp).u8(e.pickup == null ? -1 : e.pickup.id);
        }
        return b;
    }

    /** Appends the events in a message body (after the match id) to the world's event list. */
    static void readEvents(GameWorld w, NetBuffer b) {
        int n = b.readI16();
        for (int i = 0; i < n; i++) {
            int type = b.readU8();
            if (type >= EVENT_TYPES.length) throw new NetBuffer.Malformed("event type " + type);
            GameEvent e = new GameEvent(EVENT_TYPES[type]);
            e.actor = combatantId(w, b.readI8());
            e.target = combatantId(w, b.readI8());
            readVec(b, e.position);
            readVec(b, e.position2);
            e.value = b.readF32();
            e.flag = b.readBool();
            e.weapon = b.readEnum(WEAPONS);
            e.powerUp = b.readEnum(POWER_UPS);
            int pickup = b.readI8();
            if (pickup >= w.pickups.size()) throw new NetBuffer.Malformed("pickup " + pickup);
            e.pickup = pickup < 0 ? null : w.pickups.get(pickup);
            if (!valid(e)) throw new NetBuffer.Malformed("incomplete " + e.type + " event");
            w.events.add(e);
        }
    }

    private static int combatantId(GameWorld w, int id) {
        if (id >= w.combatants.size()) throw new NetBuffer.Malformed("combatant " + id);
        return id;
    }

    /** The fields each event type's consumers dereference without checking. */
    private static boolean valid(GameEvent e) {
        switch (e.type) {
            case KILL: return e.actor >= 0 && e.target >= 0 && e.weapon != null;
            case PICKUP: return e.pickup != null;
            case POWERUP_EXPIRED: return e.powerUp != null;
            case RELOAD_START: return e.actor >= 0;
            default: return true;
        }
    }

    // ------------------------------------------------------------------ input

    /** Client input: held state plus counters for presses, so a lost or repeated packet never drops or doubles a press. */
    static final class Input {
        int sequence;
        float forward, strafe, yaw, pitch;
        boolean fire;
        int jumps, reloads, weaponPresses, weaponSlot;
        /** The host teleport count this aim belongs to; aim from before a respawn is ignored. */
        int teleportAck;

        NetBuffer write(int token) {
            NetBuffer b = NetBuffer.message(Protocol.INPUT);
            b.i32(token).i32(sequence).f32(forward).f32(strafe).f32(yaw).f32(pitch).bool(fire);
            b.u8(jumps).u8(reloads).u8(weaponPresses).u8(weaponSlot).i32(teleportAck);
            return b;
        }

        /** Reads the body after the type and token. */
        void read(NetBuffer b) {
            sequence = b.readI32();
            forward = clampUnit(b.readF32());
            strafe = clampUnit(b.readF32());
            yaw = b.readF32() % 360f;
            pitch = Math.max(-88f, Math.min(88f, b.readF32()));
            fire = b.readBool();
            jumps = b.readU8();
            reloads = b.readU8();
            weaponPresses = b.readU8();
            weaponSlot = b.readU8();
            teleportAck = b.readI32();
        }

        private static float clampUnit(float v) { return Math.max(-1f, Math.min(1f, v)); }
    }

    // ------------------------------------------------------------------ helpers

    private static void vec(NetBuffer b, Vector3 v) {
        b.f32(v.x).f32(v.y).f32(v.z);
    }

    private static void readVec(NetBuffer b, Vector3 out) {
        out.set(b.readF32(), b.readF32(), b.readF32());
    }
}
