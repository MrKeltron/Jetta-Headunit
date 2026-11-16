package dev.kelton.drehzahl.obd;

import java.util.Random;

/**
 * Throws thousands of broken, cut-off and random adapter replies at the parsers and the pull recorder.
 * Nothing may throw: a bad reply from a cheap adapter must never end the OBD2 session or crash the app.
 * Fixed seed, so a failure can be repeated.
 */
public class FuzzTest {
    static final String[] PIECES = { "41", "0C", "1A", "F8", "7E8", "7E9", "06", "10", "14", "21", "22", "49", "02", "01", "43", "47",
            "00", "FF", "AA", "0:", "1:", "2:", "00A", "014", "\r", "\n", " ", ">", "NO DATA", "SEARCHING...", "CAN ERROR", "BUS INIT: ERROR",
            "UNABLE TO CONNECT", "?", "STOPPED", "BUFFER FULL", "12.6V", "ELM327 v2.3", "A6", "7F", "62", "59", "é", "\u0000", ":", "::", "G", "z" };

    static String junk(Random r) {
        StringBuilder b = new StringBuilder();
        int n = r.nextInt(14);
        for (int i = 0; i < n; i++) {
            if (r.nextInt(12) == 0) b.append((char) r.nextInt(300));
            else b.append(PIECES[r.nextInt(PIECES.length)]);
        }
        return b.toString();
    }

    public static void main(String[] a) {
        Random r = new Random(4156);
        int fails = 0;
        String lastInput = "";
        String step = "";
        int runs = 60000;
        for (int n = 0; n < runs; n++) {
            String s = junk(r);
            lastInput = s;
            try {
                step = "dataLines"; ObdParse.dataLines(s);
                step = "hexBytes"; ObdParse.hexBytes(s);
                step = "modeResponse"; ObdParse.modeResponse(s, 1 + r.nextInt(9), r.nextInt(256));
                step = "engineHeader"; String h = ObdParse.engineHeader(s);
                if (h != null) { step = "requestHeader"; ObdParse.requestHeader(h); }
                step = "supported"; ObdParse.supported(s, r.nextInt(8) * 0x20, new boolean[256]);
                step = "isoTp"; ObdParse.isoTp(s);
                step = "multiPid"; ObdParse.multiPid(s, new int[] { 0x0C, 0x0B, 0x11, r.nextInt(256) });
                step = "dtcs"; ObdParse.dtcs(s, 3); ObdParse.dtcs(s, 7);
                step = "vin"; ObdParse.vin(s);
                step = "volts"; ObdParse.volts(s);
                step = "noCar"; ObdParse.noCar(s);
                step = "protocolName"; ObdParse.protocolName(s);
                step = "clean"; Elm327.clean(s, "0100");
                step = "payload"; ObdParse.payload(s);
            } catch (Throwable t) {
                fails++;
                if (fails <= 5) System.out.println("FAIL  " + step + " threw " + t + " on \"" + s.replace("\r", "\\r").replace("\n", "\\n") + "\"");
            }
        }
        for (String s : new String[] { null }) {
            try {
                step = "null input";
                ObdParse.dataLines(s); ObdParse.modeResponse(s, 1, 0); ObdParse.engineHeader(s); ObdParse.isoTp(s);
                ObdParse.multiPid(s, new int[0]); ObdParse.dtcs(s, 3); ObdParse.vin(s); ObdParse.volts(s); ObdParse.noCar(s);
                ObdParse.protocolName(s); ObdParse.payload(s);
            } catch (Throwable t) { fails++; System.out.println("FAIL  null input threw " + t); }
        }
        System.out.println((fails == 0 ? "ok    " : "FAIL  ") + "parsers survive " + runs + " broken replies" + (fails == 0 ? "" : " (" + fails + " threw)"));

        // The pull recorder: random readings, times that jump backwards, absurd values. It must never throw,
        // and every pull it keeps must be at least the minimum length and have a finding.
        int recFails = 0;
        final int[] saved = { 0 };
        final int[] bad = { 0 };
        int[] pids = { PullRecorder.RPM, PullRecorder.SPEED, PullRecorder.MAP, PullRecorder.THROTTLE, PullRecorder.THROTTLE_CMD,
                PullRecorder.PEDAL, PullRecorder.TIMING, PullRecorder.RAIL, PullRecorder.LAMBDA, PullRecorder.LAMBDA_CMD, PullRecorder.LOAD,
                PullRecorder.TQ_REQ, PullRecorder.TQ_ACT, 0x24, 0x00, 0xFF, 0x1FF };
        for (int round = 0; round < 300; round++) {
            PullRecorder rec = new PullRecorder();
            rec.setSink(new PullRecorder.Sink() {
                public void saved(PullRecorder.Pull p) {
                    saved[0]++;
                    if (p.end - p.start < PullRecorder.MIN_PULL_MS || p.findings.isEmpty() || p.summary() == null || p.csv() == null) bad[0]++;
                }
            });
            long t = 1000 + r.nextInt(100000);
            try {
                for (int i = 0; i < 4000; i++) {
                    t += r.nextInt(20) == 0 ? -r.nextInt(3000) : r.nextInt(120);
                    int pid = pids[r.nextInt(pids.length)];
                    float v;
                    switch (r.nextInt(8)) {
                        case 0: v = Float.NaN; break;
                        case 1: v = Float.POSITIVE_INFINITY; break;
                        case 2: v = -r.nextFloat() * 1e6f; break;
                        case 3: v = r.nextFloat() * 8000f; break;
                        default: v = r.nextFloat() * 100f;
                    }
                    rec.sample(t, pid, v);
                    if (r.nextInt(500) == 0) rec.setBaro(r.nextFloat() * 200f);
                    if (r.nextInt(1500) == 0) rec.setEnabled(r.nextBoolean());
                    rec.active();
                }
            } catch (Throwable th) {
                recFails++;
                if (recFails <= 3) { System.out.println("FAIL  pull recorder threw " + th); th.printStackTrace(System.out); }
            }
        }
        System.out.println((recFails == 0 ? "ok    " : "FAIL  ") + "pull recorder survives random readings (" + saved[0] + " pulls kept)");
        System.out.println((bad[0] == 0 ? "ok    " : "FAIL  ") + "every kept pull is long enough and has a finding" + (bad[0] == 0 ? "" : " (" + bad[0] + " bad)"));
        fails += recFails + bad[0];
        System.out.println(fails == 0 ? "FuzzTest: all passed" : "FuzzTest: " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
