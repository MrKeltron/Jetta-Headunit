package dev.kelton.drehzahl.obd;

import java.io.IOException;

/** Bytes that arrive on one thread (Bluetooth LE notifications) and are read on another, with a timeout. */
final class ByteQueue {
    private final byte[] buf;
    private int head, count;
    private boolean closed;
    private String why = "closed";

    ByteQueue(int capacity) { buf = new byte[capacity]; }

    /** Adds bytes. When full, the oldest bytes are dropped (an ELM327 reply never gets close). */
    synchronized void put(byte[] data) {
        if (data == null) return;
        for (byte b : data) {
            if (count == buf.length) { head = (head + 1) % buf.length; count--; }
            buf[(head + count) % buf.length] = b;
            count++;
        }
        notifyAll();
    }

    /** Waits up to timeoutMs for at least one byte. Returns 0 when nothing came. Throws once closed and empty. */
    synchronized int read(byte[] out, int timeoutMs) throws IOException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (count == 0) {
            if (closed) throw new IOException(why);
            long left = end - System.currentTimeMillis();
            if (left <= 0) return 0;
            try { wait(left); } catch (InterruptedException e) { return 0; }
        }
        int n = Math.min(out.length, count);
        for (int i = 0; i < n; i++) out[i] = buf[(head + i) % buf.length];
        head = (head + n) % buf.length;
        count -= n;
        return n;
    }

    synchronized void clear() { head = 0; count = 0; }

    synchronized void close(String reason) {
        closed = true;
        why = reason;
        notifyAll();
    }
}
