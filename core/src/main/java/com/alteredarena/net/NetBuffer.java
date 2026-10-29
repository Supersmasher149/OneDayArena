package com.alteredarena.net;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Growable big-endian byte buffer for building and parsing messages. Reads past the end throw
 * {@link Malformed}, so a truncated or hostile packet is rejected instead of crashing the game.
 */
final class NetBuffer {
    static final class Malformed extends RuntimeException {
        Malformed(String message) { super(message); }
    }

    private byte[] data;
    private int size;
    private int pos;

    NetBuffer() {
        data = new byte[256];
    }

    /** Wraps received bytes for reading. */
    NetBuffer(byte[] bytes, int length) {
        data = bytes;
        size = length;
    }

    static NetBuffer message(int type) {
        NetBuffer b = new NetBuffer();
        b.u8(type);
        return b;
    }

    byte[] toBytes() { return Arrays.copyOf(data, size); }
    int size() { return size; }
    int remaining() { return size - pos; }

    // ------------------------------------------------------------------ write

    private void ensure(int extra) {
        if (size + extra > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + extra));
    }

    NetBuffer u8(int v) {
        ensure(1);
        data[size++] = (byte) v;
        return this;
    }

    NetBuffer bool(boolean v) { return u8(v ? 1 : 0); }

    NetBuffer i16(int v) {
        ensure(2);
        data[size++] = (byte) (v >> 8);
        data[size++] = (byte) v;
        return this;
    }

    NetBuffer i32(int v) {
        ensure(4);
        data[size++] = (byte) (v >> 24);
        data[size++] = (byte) (v >> 16);
        data[size++] = (byte) (v >> 8);
        data[size++] = (byte) v;
        return this;
    }

    NetBuffer i64(long v) {
        i32((int) (v >> 32));
        return i32((int) v);
    }

    NetBuffer f32(float v) { return i32(Float.floatToRawIntBits(v)); }

    /** UTF-8 string of at most 255 bytes. */
    NetBuffer str(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(255, b.length);
        u8(n);
        ensure(n);
        System.arraycopy(b, 0, data, size, n);
        size += n;
        return this;
    }

    // ------------------------------------------------------------------ read

    private void need(int n) {
        if (pos + n > size) throw new Malformed("truncated message");
    }

    int readU8() {
        need(1);
        return data[pos++] & 0xFF;
    }

    /** Signed byte, used for small enums and ids where -1 means none. */
    int readI8() {
        need(1);
        return data[pos++];
    }

    boolean readBool() { return readU8() != 0; }

    int readI16() {
        need(2);
        int v = (short) (((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF));
        pos += 2;
        return v;
    }

    int readI32() {
        need(4);
        int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16) | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
        pos += 4;
        return v;
    }

    long readI64() {
        long hi = readI32();
        return (hi << 32) | (readI32() & 0xFFFFFFFFL);
    }

    /** Non-finite values are replaced so a bad packet cannot put NaN into the world. */
    float readF32() {
        float f = Float.intBitsToFloat(readI32());
        return Float.isFinite(f) ? f : 0f;
    }

    String readStr() {
        int n = readU8();
        need(n);
        String s = new String(data, pos, n, StandardCharsets.UTF_8);
        pos += n;
        return s;
    }

    /** Reads an enum ordinal written with {@code u8(ordinal)} or -1 for null. */
    <E extends Enum<E>> E readEnum(E[] values) {
        int i = readI8();
        if (i < 0) return null;
        if (i >= values.length) throw new Malformed("bad enum " + i);
        return values[i];
    }

    NetBuffer writeEnum(Enum<?> e) { return u8(e == null ? -1 : e.ordinal()); }
}
