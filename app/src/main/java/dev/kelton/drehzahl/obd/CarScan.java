package dev.kelton.drehzahl.obd;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A read-only look at what the car's computers will tell us, written to
 * Android/data/dev.kelton.drehzahl/files/scans/scan-<time>.txt for the PC toolkit to read.
 *
 * Only reading services are ever sent: 01, 03, 07, 0A, 09 (standard OBD), 22 (read a value by
 * its number) and 19 (read the fault memory). Never 10, 11, 14, 27, 2E, 2F or 31, so nothing in
 * the car changes, no session is opened and no codes are cleared.
 *
 * Steps: standard PID list and generic codes; which modules answer UDS on the OBD addresses
 * (7E0 engine, 7E1 gearbox and the rest of 7E0-7E7) with their part numbers and fault memory;
 * then every value number in the chosen ranges on the engine, keeping the ones it answers.
 * Coolant temperature is read now and then during the walk so temperature values can be matched.
 */
public final class CarScan implements ObdSource.ElmTask {
    public static final int QUICK = 0, FULL = 1;

    /** Progress for the screen. */
    public static volatile String phase = "";
    public static volatile int done, total, found;
    public static volatile boolean busy;
    public static volatile String lastFile = "";

    private final Context ctx;
    private final int depth;
    private FileWriter out;

    public CarScan(Context ctx, int depth) { this.ctx = ctx.getApplicationContext(); this.depth = depth; }

    private static final String[] IDENT = { "F190", "F187", "F189", "F191", "F19E", "F1A3", "F197", "F1AA" };
    private static final String[] IDENT_NAMES = { "VIN", "part number", "software version", "hardware number",
            "data set name", "hardware version", "system name", "workshop system name" };

    @Override public void run(Elm327 elm) throws IOException {
        busy = true;
        found = 0;
        done = 0;
        total = 0;
        File dir = new File(ctx.getExternalFilesDir(null), "scans");
        dir.mkdirs();
        File f = new File(dir, "scan-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
        lastFile = f.getName();
        try {
            out = new FileWriter(f);
            line("# Drehzahl car scan " + (depth == FULL ? "full" : "quick") + ", read only");
            elm.cmd("ATH0", 1000);
            elm.cmd("ATCAF1", 1000);
            elm.cmd("ATST64", 1000);
            standard(elm);
            Map<String, String> uds = modules(elm);
            if (uds.containsKey("7E0")) values(elm, "7E0", "7E8");
            else line("## engine 7E0 did not answer UDS reads; its values need a transport Drehzahl does not speak yet");
            line("# done");
            phase = "Done: " + found + " values found";
        } catch (IOException e) {
            phase = "Stopped: " + e.getMessage();
            try { line("# stopped: " + e.getMessage()); } catch (Exception ignored) { }
            throw e;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            busy = false;
        }
    }

    private void standard(Elm327 elm) throws IOException {
        phase = "Standard OBD";
        elm.cmd("ATSH7DF", 1000);
        elm.cmd("ATAR", 1000);
        line("## standard OBD (all modules)");
        for (String c : new String[] { "0100", "0120", "0140", "0160", "0180", "01A0", "01C0", "0902", "03", "07", "0A" })
            line(c + " = " + oneLine(elm.cmd(c, 3000)));
    }

    private Map<String, String> modules(Elm327 elm) throws IOException {
        Map<String, String> answering = new LinkedHashMap<>();
        line("## modules on the OBD addresses (UDS reads)");
        for (int tx = 0x7E0; tx <= 0x7E7; tx++) {
            String t = String.format(Locale.US, "%03X", tx), r = String.format(Locale.US, "%03X", tx + 8);
            phase = "Asking module " + t;
            target(elm, t, r);
            String first = payload(elm.cmd("22" + IDENT[0], 2000));
            boolean ok = first.startsWith("62");
            if (!ok) {
                String alt = payload(elm.cmd("22" + IDENT[1], 2000));
                ok = alt.startsWith("62") || alt.startsWith("7F22");
                if (!ok && !first.startsWith("7F")) { line(t + ": no answer"); continue; }
            }
            answering.put(t, r);
            line("# module " + t + " (answers from " + r + ")");
            for (int i = 0; i < IDENT.length; i++) {
                String p = payload(elm.cmd("22" + IDENT[i], 2000));
                line(t + " 22" + IDENT[i] + " " + IDENT_NAMES[i] + " = " + p + text(p, 6));
            }
            line(t + " 1902FF faults = " + payload(elm.cmd("1902FF", 4000)));
            line(t + " 190A supported faults = " + payload(elm.cmd("190A", 4000)));
        }
        return answering;
    }

    private void values(Elm327 elm, String t, String r) throws IOException {
        int[][] ranges = depth == FULL
                ? new int[][] { { 0x0100, 0x0FFF }, { 0x1000, 0x2FFF }, { 0x3000, 0x3FFF }, { 0xF400, 0xF4FF }, { 0xF180, 0xF1FF } }
                : new int[][] { { 0x1000, 0x1FFF }, { 0x2000, 0x20FF }, { 0xF400, 0xF4FF } };
        total = 0;
        for (int[] g : ranges) total += g[1] - g[0] + 1;
        line("## values on " + t + " (only the ones it answered)");
        Map<String, Integer> refusals = new LinkedHashMap<>();
        target(elm, t, r);
        long lastRef = 0;
        for (int[] g : ranges) {
            for (int did = g[0]; did <= g[1]; did++) {
                done++;
                if (System.currentTimeMillis() - lastRef > 20000) {
                    lastRef = System.currentTimeMillis();
                    line("ref t=" + lastRef + " 0105 coolant = " + payload(elm.cmd("0105", 1500))
                            + " 015C oil = " + payload(elm.cmd("015C", 1500)) + " 010C rpm = " + payload(elm.cmd("010C", 1500)));
                }
                String id = String.format(Locale.US, "%04X", did);
                phase = "Reading value " + id + " (" + done + " of " + total + ", " + found + " found)";
                String p = payload(elm.cmd("22" + id, 1500));
                if (p.startsWith("62" + id)) {
                    found++;
                    line(t + " 22" + id + " = " + p.substring(6) + text(p, 6));
                } else if (p.startsWith("7F22") && p.length() >= 6) {
                    String nrc = p.substring(4, 6);
                    Integer n = refusals.get(nrc);
                    refusals.put(nrc, n == null ? 1 : n + 1);
                } else {
                    Integer n = refusals.get(p.isEmpty() ? "empty" : "other");
                    refusals.put(p.isEmpty() ? "empty" : "other", n == null ? 1 : n + 1);
                    if (!p.isEmpty() && !p.contains("NODATA")) line(t + " 22" + id + " odd reply = " + p);
                }
            }
        }
        StringBuilder s = new StringBuilder("refusals:");
        for (Map.Entry<String, Integer> e : refusals.entrySet()) s.append(' ').append(e.getKey()).append('x').append(e.getValue());
        line(s.toString());
    }

    private static void target(Elm327 elm, String tx, String rx) throws IOException {
        elm.cmd("ATSH" + tx, 1000);
        elm.cmd("ATCRA" + rx, 1000);
    }

    /** Joins an ELM reply into one hex string (see ObdParse.payload). */
    static String payload(String resp) { return ObdParse.payload(resp); }

    /** Printable text inside a reply (part numbers and names are ASCII), as a hint after the hex. */
    private static String text(String hex, int skip) {
        if (hex.length() <= skip || !hex.matches("^[0-9A-F]+$")) return "";
        StringBuilder s = new StringBuilder();
        int printable = 0, n = 0;
        for (int i = skip; i + 1 < hex.length(); i += 2) {
            int v = Integer.parseInt(hex.substring(i, i + 2), 16);
            n++;
            if (v >= 0x20 && v < 0x7F) { s.append((char) v); printable++; } else s.append('.');
        }
        return n >= 3 && printable * 4 >= n * 3 ? "  \"" + s.toString().trim() + "\"" : "";
    }

    private static String oneLine(String s) { return s.replace('\n', ' '); }

    private void line(String s) throws IOException {
        out.write(s + "\n");
        out.flush();
    }
}
