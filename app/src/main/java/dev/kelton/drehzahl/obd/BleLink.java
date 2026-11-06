package dev.kelton.drehzahl.obd;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bluetooth LE link for adapters that work without pairing (the iPhone-compatible kind).
 * Commands go to a write characteristic, replies come back as notifications.
 * All calls happen on the OBD2 worker thread; each Bluetooth step waits for its answer.
 */
public final class BleLink implements Link {
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int MTU = 247;

    private final ByteQueue in = new ByteQueue(16384);
    private final Object lock = new Object();
    private final String name;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx, rx;
    private int payload = 20;
    private boolean writeNoResponse;

    private volatile boolean connected, attemptOver, discovered, closed;
    private volatile int lastStatus;
    private volatile boolean writeDone, descDone, mtuDone;

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
            lastStatus = status;
            if (state == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) connected = true;
            else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                boolean was = connected;
                connected = false;
                attemptOver = true;
                if (was) in.close("The Bluetooth LE adapter disconnected");
            }
            wake();
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            lastStatus = status;
            discovered = true;
            wake();
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) payload = Math.min(mtu - 3, 512);
            mtuDone = true;
            wake();
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            lastStatus = status;
            descDone = true;
            wake();
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            lastStatus = status;
            writeDone = true;
            wake();
        }

        // Android 13 and newer.
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            if (rx != null && c.getUuid().equals(rx.getUuid())) in.put(value);
        }

        // Android 12 and older.
        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (rx != null && c.getUuid().equals(rx.getUuid())) in.put(c.getValue());
        }
    };

    public BleLink(Context ctx, BluetoothAdapter adapter, String address) throws IOException {
        BluetoothDevice d;
        try { d = adapter.getRemoteDevice(address); }
        catch (IllegalArgumentException e) { throw new IOException("Bad Bluetooth address " + address); }
        String n;
        try { n = d.getName(); } catch (SecurityException e) { n = null; }
        name = n == null ? address : n;
        try { adapter.cancelDiscovery(); } catch (SecurityException ignored) { }
        try {
            open(ctx, d);
        } catch (IOException | RuntimeException e) {
            close();
            throw e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
        }
    }

    private void open(Context ctx, BluetoothDevice d) throws IOException {
        // A first attempt often fails with status 133 right after Bluetooth was busy; one retry fixes most of them.
        for (int attempt = 0; attempt < 2 && !connected; attempt++) {
            if (gatt != null) { gatt.close(); gatt = null; sleep(600); }
            attemptOver = false;
            gatt = d.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) throw new IOException("Android could not start a Bluetooth LE connection");
            waitFor(new Check() { public boolean done() { return connected || attemptOver || closed; } }, 12000);
        }
        if (!connected) throw new IOException("The Bluetooth LE adapter did not answer (status " + lastStatus + "). Is the ignition on?");

        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        sleep(150);
        if (!gatt.discoverServices()) throw new IOException("Bluetooth LE service search did not start");
        waitFor(new Check() { public boolean done() { return discovered || !connected; } }, 10000);
        if (!discovered) throw new IOException("The Bluetooth LE adapter did not list its services");

        List<BluetoothGattCharacteristic> all = new ArrayList<>();
        List<GattPick.Ch> desc = new ArrayList<>();
        for (BluetoothGattService s : gatt.getServices()) {
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                all.add(c);
                desc.add(new GattPick.Ch(s.getUuid().toString(), c.getUuid().toString(), c.getProperties()));
            }
        }
        int[] pick = GattPick.pick(desc);
        if (pick == null) throw new IOException("This Bluetooth LE device has no serial data channel. Is it an OBD2 adapter?");
        rx = all.get(pick[0]);
        tx = all.get(pick[1]);
        writeNoResponse = (tx.getProperties() & GattPick.WRITE_NO_RESPONSE) != 0;

        mtuDone = false;
        if (gatt.requestMtu(MTU)) waitFor(new Check() { public boolean done() { return mtuDone || !connected; } }, 3000);

        if (!gatt.setCharacteristicNotification(rx, true)) throw new IOException("Could not turn on Bluetooth LE replies");
        BluetoothGattDescriptor cccd = rx.getDescriptor(CCCD);
        if (cccd != null) {
            byte[] v = (rx.getProperties() & GattPick.NOTIFY) != 0
                    ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
            descDone = false;
            if (!writeDescriptor(cccd, v)) throw new IOException("Could not turn on Bluetooth LE replies");
            waitFor(new Check() { public boolean done() { return descDone || !connected; } }, 4000);
            if (!descDone) throw new IOException("The adapter did not confirm Bluetooth LE replies");
        }
        in.clear();
    }

    @SuppressWarnings("deprecation")
    private boolean writeDescriptor(BluetoothGattDescriptor dsc, byte[] v) {
        if (Build.VERSION.SDK_INT >= 33) return gatt.writeDescriptor(dsc, v) == BluetoothGatt.GATT_SUCCESS;
        dsc.setValue(v);
        return gatt.writeDescriptor(dsc);
    }

    @SuppressWarnings("deprecation")
    private boolean writeChunk(byte[] chunk) {
        int type = writeNoResponse ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        if (Build.VERSION.SDK_INT >= 33) return gatt.writeCharacteristic(tx, chunk, type) == BluetoothGatt.GATT_SUCCESS;
        tx.setWriteType(type);
        tx.setValue(chunk);
        return gatt.writeCharacteristic(tx);
    }

    @Override public void write(byte[] data) throws IOException {
        if (!connected) throw new IOException("The Bluetooth LE adapter disconnected");
        for (int off = 0; off < data.length; off += payload) {
            byte[] chunk = new byte[Math.min(payload, data.length - off)];
            System.arraycopy(data, off, chunk, 0, chunk.length);
            boolean started = false;
            for (int tries = 0; tries < 20 && !started; tries++) {
                writeDone = false;
                started = writeChunk(chunk);
                if (!started) sleep(5);
            }
            if (!started) throw new IOException("Bluetooth LE write was refused");
            waitFor(new Check() { public boolean done() { return writeDone || !connected; } }, 2000);
            if (!writeDone) throw new IOException(connected ? "Bluetooth LE write timed out" : "The Bluetooth LE adapter disconnected");
        }
    }

    @Override public int read(byte[] buf, int timeoutMs) throws IOException { return in.read(buf, timeoutMs); }

    @Override public void setBaud(int baud) { }

    @Override public String describe() { return "Bluetooth LE " + name; }

    @Override public void close() {
        closed = true;
        in.close("closed");
        if (gatt != null) {
            try { gatt.disconnect(); } catch (RuntimeException ignored) { }
            try { gatt.close(); } catch (RuntimeException ignored) { }
            gatt = null;
        }
        wake();
    }

    private interface Check { boolean done(); }

    private void waitFor(Check c, long ms) {
        long end = System.currentTimeMillis() + ms;
        synchronized (lock) {
            while (!c.done()) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return;
                try { lock.wait(Math.min(left, 50)); } catch (InterruptedException e) { return; }
            }
        }
    }

    private void wake() { synchronized (lock) { lock.notifyAll(); } }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
