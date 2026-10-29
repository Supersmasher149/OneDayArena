package com.alteredarena.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * One TCP connection carrying length-prefixed frames. A reader thread hands each frame to {@code onFrame};
 * a writer thread drains an outbox so the game thread never blocks on a slow peer. {@code onClose} runs once.
 */
final class Link {
    private static final byte[] CLOSE = new byte[0];

    private final Socket socket;
    private final Consumer<byte[]> onFrame;
    private final Runnable onClose;
    private final LinkedBlockingQueue<byte[]> outbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    Link(Socket socket, Consumer<byte[]> onFrame, Runnable onClose) throws IOException {
        this.socket = socket;
        this.onFrame = onFrame;
        this.onClose = onClose;
        socket.setTcpNoDelay(true);
    }

    void start(String name) {
        Thread reader = new Thread(this::readLoop, name + "-read");
        Thread writer = new Thread(this::writeLoop, name + "-write");
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
    }

    void send(NetBuffer message) {
        if (!closed) outbox.add(message.toBytes());
    }

    /** Sends a last message, then closes once it has been written. */
    void sendAndClose(NetBuffer message) {
        send(message);
        outbox.add(CLOSE);
    }

    boolean isClosed() { return closed; }

    private void readLoop() {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            while (!closed) {
                int length = in.readInt();
                if (length <= 0 || length > Protocol.MAX_FRAME) throw new IOException("bad frame length " + length);
                byte[] frame = new byte[length];
                in.readFully(frame);
                onFrame.accept(frame);
            }
        } catch (IOException e) {
            // Socket closed or peer went away.
        } finally {
            close();
        }
    }

    private void writeLoop() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            while (true) {
                byte[] frame = outbox.take();
                if (frame == CLOSE) break;
                out.writeInt(frame.length);
                out.write(frame);
                if (outbox.isEmpty()) out.flush();
            }
            out.flush();
        } catch (IOException | InterruptedException e) {
            // Socket closed.
        } finally {
            close();
        }
    }

    void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        outbox.add(CLOSE);
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
        onClose.run();
    }
}
