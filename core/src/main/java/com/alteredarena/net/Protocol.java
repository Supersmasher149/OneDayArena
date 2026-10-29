package com.alteredarena.net;

/**
 * Wire constants. Reliable messages travel over TCP as length-prefixed frames; snapshots and inputs prefer UDP
 * and fall back to TCP when UDP does not get through. The first byte of every message is its type.
 */
public final class Protocol {
    private Protocol() {}

    /** Bump on any wire change; peers with a different version are refused. */
    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 27940;
    /** Humans per match, host included. */
    public static final int MAX_HUMANS = 4;
    /** Humans plus bots; the arena has eight spawn points. */
    public static final int MAX_COMBATANTS = 8;
    public static final int DEFAULT_TOTAL = 5;

    public static final float SNAPSHOT_INTERVAL = 1f / 30f;
    public static final float INPUT_INTERVAL = 1f / 60f;
    public static final float PING_INTERVAL = 1f;
    public static final float TIMEOUT = 10f;
    public static final int CONNECT_TIMEOUT_MS = 5000;
    static final int MAX_FRAME = 1 << 16;
    static final int MAX_DATAGRAM = 1400;

    // Client -> host
    static final int HELLO = 1;
    static final int INPUT = 2;
    // Host -> client
    static final int WELCOME = 10;
    static final int REJECT = 11;
    static final int LOBBY = 12;
    static final int START = 13;
    static final int SNAPSHOT = 14;
    static final int EVENTS = 15;
    static final int NOTICE = 16;
    static final int UDP_OK = 17;
    // Both ways
    static final int PING = 20;
}
