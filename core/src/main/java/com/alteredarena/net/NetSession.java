package com.alteredarena.net;

import com.alteredarena.level.LevelData;
import com.alteredarena.sim.GameWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An online game from the menu lobby through any number of matches. Network threads only queue what they
 * receive; everything else, including all world access, happens in {@link #update} on the game thread.
 */
public abstract class NetSession {
    /** Callbacks fired from {@link #update} on the game thread. */
    public interface Listener {
        default void lobbyChanged() {}
        /** A match is starting: call {@link #newWorld} (clients wait for this; the host starts it itself). */
        default void matchStarting() {}
        default void notice(String text) {}
        /** The session is over and already closed. */
        default void disconnected(String reason) {}
    }

    private static final Listener NONE = new Listener() {};

    /** What a network thread received, handled later on the game thread. */
    static final class Inbound {
        final Object from;
        final byte[] data;
        final boolean udp, opened, closed;

        Inbound(Object from, byte[] data, boolean udp, boolean opened, boolean closed) {
            this.from = from;
            this.data = data;
            this.udp = udp;
            this.opened = opened;
            this.closed = closed;
        }
    }

    final ConcurrentLinkedQueue<Inbound> inbox = new ConcurrentLinkedQueue<>();
    Listener listener = NONE;
    final List<String> lobby = new ArrayList<>();
    int botCount;
    boolean closed;

    public void setListener(Listener l) { listener = l == null ? NONE : l; }

    public abstract boolean isHost();

    /** Player names in combatant order: the host first, then clients in join order. */
    public List<String> lobbyNames() { return Collections.unmodifiableList(lobby); }

    /** Bots that will fill the next match. */
    public int botCount() { return botCount; }

    public boolean isClosed() { return closed; }

    /**
     * Handles everything received since the last call and sends what is due. On the host this must run after
     * the frame's simulation steps and before {@code world.events} is cleared, so the events go out.
     *
     * @param world the running match, or null in the lobby
     */
    public abstract void update(GameWorld world, float delta);

    /** Builds the world for the match that is starting (the host also announces it). */
    public abstract GameWorld newWorld(LevelData level, int killLimit, float duration);

    /** Leaves the game; no listener calls follow. */
    public abstract void close();

    static String describe(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }
}
