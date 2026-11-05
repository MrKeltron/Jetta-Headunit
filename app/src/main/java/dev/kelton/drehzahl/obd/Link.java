package dev.kelton.drehzahl.obd;

import java.io.IOException;

/** A byte pipe to the OBD adapter: USB serial or Bluetooth. */
public interface Link {
    void write(byte[] data) throws IOException;

    /** Reads whatever arrives within timeoutMs. Returns 0 when nothing came. */
    int read(byte[] buf, int timeoutMs) throws IOException;

    /** Change speed (USB only; Bluetooth ignores it). */
    void setBaud(int baud) throws IOException;

    String describe();

    void close();
}
