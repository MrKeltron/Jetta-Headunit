package dev.kelton.drehzahl.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

/** Classic Bluetooth (SPP) link, the kind most ELM327 adapters use on Android. BLE-only adapters are not supported. */
public final class BtLink implements Link {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final BluetoothSocket socket;
    private final InputStream inStream;
    private final OutputStream outStream;
    private final String name;

    public BtLink(BluetoothAdapter adapter, String address) throws IOException {
        BluetoothDevice d;
        try { d = adapter.getRemoteDevice(address); }
        catch (IllegalArgumentException e) { throw new IOException("Bad Bluetooth address " + address); }
        String n;
        try { n = d.getName(); } catch (SecurityException e) { n = null; }
        name = n == null ? address : n;
        try { adapter.cancelDiscovery(); } catch (SecurityException ignored) { }
        BluetoothSocket s = null;
        IOException first = null;
        try {
            s = d.createRfcommSocketToServiceRecord(SPP);
            s.connect();
        } catch (IOException | SecurityException e) {
            first = e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
            try { if (s != null) s.close(); } catch (IOException ignored) { }
            s = null;
        }
        if (s == null) {
            // Some cheap adapters only answer on channel 1 without an SDP lookup.
            try {
                Method m = d.getClass().getMethod("createRfcommSocket", int.class);
                s = (BluetoothSocket) m.invoke(d, 1);
                s.connect();
            } catch (Exception e) {
                throw first != null ? first : new IOException("Bluetooth connect failed");
            }
        }
        socket = s;
        inStream = s.getInputStream();
        outStream = s.getOutputStream();
    }

    @Override public void write(byte[] data) throws IOException {
        outStream.write(data);
        outStream.flush();
    }

    @Override public int read(byte[] buf, int timeoutMs) throws IOException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (inStream.available() == 0) {
            if (System.currentTimeMillis() >= end) return 0;
            try { Thread.sleep(3); } catch (InterruptedException e) { return 0; }
        }
        return inStream.read(buf, 0, Math.min(buf.length, inStream.available()));
    }

    @Override public void setBaud(int baud) { }

    @Override public String describe() { return "Bluetooth " + name; }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
