package dev.kelton.drehzahl.obd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BleTest {
    static int fails = 0;
    static void eq(String name, Object got, Object want) {
        boolean ok = Objects.equals(String.valueOf(got), String.valueOf(want));
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  got=" + got + (ok ? "" : "  want=" + want));
    }
    static String sig(String id) { return "0000" + id + "-0000-1000-8000-00805F9B34FB"; }
    static GattPick.Ch ch(String s, String c, int p) { return new GattPick.Ch(s, c, p); }
    static String pick(List<GattPick.Ch> l) {
        int[] r = GattPick.pick(l);
        return r == null ? "none" : l.get(r[0]).uuid.substring(0, 8) + "/" + l.get(r[1]).uuid.substring(0, 8);
    }

    public static void main(String[] a) throws Exception {
        int RW = GattPick.WRITE | 0x02, N = GattPick.NOTIFY, ALL = 0x02 | GattPick.WRITE | GattPick.WRITE_NO_RESPONSE | GattPick.NOTIFY | GattPick.INDICATE;
        // Vgate iCar Pro BLE as reported: battery, 18F0 service, then the E7810A71 service
        List<GattPick.Ch> vgate = new ArrayList<>(Arrays.asList(
                ch(sig("180f"), sig("2a19"), 0x02 | N),
                ch(sig("18f0"), sig("2af0"), N),
                ch(sig("18f0"), sig("2af1"), RW),
                ch("E7810A71-73AE-499D-8C15-FAA9AEF0C3F2", "BEF8D6C9-9C21-4C9E-B632-BD58C1009F9F", ALL)));
        eq("vgate uses its own service", pick(vgate), "bef8d6c9/bef8d6c9");
        List<GattPick.Ch> veepeak = Arrays.asList(
                ch(sig("180a"), sig("2a29"), 0x02),
                ch(sig("fff0"), sig("fff2"), GattPick.WRITE_NO_RESPONSE),
                ch(sig("fff0"), sig("fff1"), N));
        eq("fff0 layout", pick(veepeak), "0000fff1/0000fff2");
        List<GattPick.Ch> hm10 = Arrays.asList(ch(sig("ffe0"), sig("ffe1"), GattPick.WRITE_NO_RESPONSE | N | 0x02));
        eq("hm-10 layout", pick(hm10), "0000ffe1/0000ffe1");
        List<GattPick.Ch> custom = Arrays.asList(
                ch(sig("1800"), sig("2a00"), 0x02 | GattPick.WRITE),
                ch(sig("180f"), sig("2a19"), N),
                ch("49535343-FE7D-4AE5-8FA9-9FAFD205E455", "49535343-1E4D-4BD9-BA61-23C647249616", N),
                ch("49535343-FE7D-4AE5-8FA9-9FAFD205E455", "49535343-8841-43F4-A8D4-ECBE34729BB3", GattPick.WRITE));
        int[] u = GattPick.pick(custom);
        eq("unknown adapter found by shape", custom.get(u[0]).uuid + " " + custom.get(u[1]).uuid,
                "49535343-1e4d-4bd9-ba61-23c647249616 49535343-8841-43f4-a8d4-ecbe34729bb3");
        List<GattPick.Ch> notObd = Arrays.asList(ch(sig("180d"), sig("2a37"), N), ch(sig("1800"), sig("2a00"), RW));
        eq("heart rate strap is not an adapter", pick(notObd), "none");

        ByteQueue q = new ByteQueue(8);
        byte[] buf = new byte[32];
        eq("empty read times out", q.read(buf, 30), 0);
        q.put("41 0C".getBytes());
        q.put("1AF8\r>".getBytes());
        int n = q.read(buf, 10);
        eq("overflow keeps the newest bytes", new String(buf, 0, n).replace("\r", "|"), "0C1AF8|>");
        q.clear();
        q.put("OK\r>".getBytes());
        n = q.read(buf, 10);
        eq("reads what arrived", new String(buf, 0, n), "OK\r>");
        final ByteQueue q2 = new ByteQueue(64);
        new Thread(new Runnable() { public void run() {
            try { Thread.sleep(40); } catch (InterruptedException ignored) { }
            q2.put(">".getBytes());
        } }).start();
        long t0 = System.currentTimeMillis();
        n = q2.read(buf, 1000);
        eq("wakes when bytes arrive", n + " " + (System.currentTimeMillis() - t0 < 500), "1 true");
        q2.close("The Bluetooth LE adapter disconnected");
        String err = "";
        try { q2.read(buf, 10); } catch (java.io.IOException e) { err = e.getMessage(); }
        eq("closed queue reports why", err, "The Bluetooth LE adapter disconnected");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
    }
}
