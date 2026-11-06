package dev.kelton.drehzahl.obd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns ELM327 text replies into numbers. Pure Java with no Android imports so it can be tested on a PC.
 * Expects the adapter set up with ATE0 ATL0 ATS0 (no echo, no line feeds, no spaces).
 */
public final class ObdParse {
    private ObdParse() { }

    public static boolean isHex(char c) { return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'); }

    /** Lines of the reply with ISO-TP frame markers ("0:", "1:") and length-only lines removed. */
    static List<String> dataLines(String resp) {
        List<String> out = new ArrayList<>();
        if (resp == null) return out;
        StringBuilder joined = null;
        for (String raw : resp.split("[\\r\\n]+")) {
            String l = raw.trim().replace(" ", "").toUpperCase(Locale.US);
            if (l.isEmpty()) continue;
            if (l.matches("^[0-9A-F]{3}$")) { joined = new StringBuilder(); continue; }
            if (l.matches("^[0-9A-F]:.*")) {
                if (joined == null) joined = new StringBuilder();
                joined.append(l.substring(2));
                continue;
            }
            if (joined != null && joined.length() > 0) { out.add(joined.toString()); joined = null; }
            out.add(l);
        }
        if (joined != null && joined.length() > 0) out.add(joined.toString());
        return out;
    }

    public static int[] hexBytes(String s) {
        StringBuilder h = new StringBuilder();
        for (char c : s.toCharArray()) if (isHex(c)) h.append(c);
        int[] b = new int[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    /** Data bytes after "41 0C" (mode + 0x40, pid) in a reply, or null when the car had nothing. */
    public static int[] modeResponse(String resp, int mode, int pid) {
        String key = String.format(Locale.US, "%02X%02X", mode + 0x40, pid);
        for (String l : dataLines(resp)) {
            boolean hex = true;
            for (char c : l.toCharArray()) if (!isHex(c)) { hex = false; break; }
            if (!hex) continue;
            int at = l.indexOf(key);
            if (at < 0 || at % 2 != 0) continue;
            return hexBytes(l.substring(at + key.length()));
        }
        return null;
    }

    /**
     * Finds the engine computer in an 11-bit CAN reply to 010C read with headers on (ATH1 ATS0),
     * for example "7E804410C0BB8" gives "7E8". Returns null when no module answered with engine speed.
     */
    public static String engineHeader(String resp) {
        if (resp == null) return null;
        for (String raw : resp.split("[\\r\\n]+")) {
            String l = raw.trim().replace(" ", "").toUpperCase(Locale.US);
            if (l.length() < 9 || !l.matches("^[0-9A-F]+$")) continue;
            if (l.startsWith("410C", 5) && l.charAt(0) == '7') return l.substring(0, 3);
        }
        return null;
    }

    /** The request address that goes with a reply address on 11-bit CAN: 7E8 answers requests sent to 7E0. */
    public static String requestHeader(String replyHeader) {
        int rx = Integer.parseInt(replyHeader, 16);
        return String.format(Locale.US, "%03X", rx - 8);
    }

    /** Fills supported[] from a 0100 / 0120 / 0140... reply. Returns true if the next range is also supported. */
    public static boolean supported(String resp, int base, boolean[] supported) {
        int[] d = modeResponse(resp, 1, base);
        if (d == null || d.length < 4) return false;
        for (int i = 0; i < 32; i++) {
            boolean on = ((d[i / 8] >> (7 - (i % 8))) & 1) == 1;
            if (on) supported[base + i + 1] = true;
        }
        return supported[base + 0x20];
    }

    /** Reassembles CAN replies read with headers on (ATH1 ATS0): "7E8064302..." lines grouped per module. */
    public static Map<String, int[]> isoTp(String resp) {
        Map<String, List<Integer>> acc = new LinkedHashMap<>();
        Map<String, Integer> want = new LinkedHashMap<>();
        for (String raw : resp.split("[\\r\\n]+")) {
            String l = raw.trim().replace(" ", "").toUpperCase(Locale.US);
            if (l.length() < 5 || !l.matches("^[0-9A-F]+$")) continue;
            String head = l.substring(0, 3);
            int[] b = hexBytes(l.substring(3));
            if (b.length == 0) continue;
            int type = b[0] >> 4;
            List<Integer> data = acc.get(head);
            if (type == 0) {
                data = new ArrayList<>();
                int len = b[0] & 0x0F;
                for (int i = 1; i <= len && i < b.length; i++) data.add(b[i]);
                acc.put(head, data);
                want.put(head, len);
            } else if (type == 1 && b.length > 1) {
                data = new ArrayList<>();
                int len = ((b[0] & 0x0F) << 8) | b[1];
                for (int i = 2; i < b.length; i++) data.add(b[i]);
                acc.put(head, data);
                want.put(head, len);
            } else if (type == 2 && data != null) {
                for (int i = 1; i < b.length; i++) data.add(b[i]);
            }
        }
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : acc.entrySet()) {
            int len = Math.min(want.get(e.getKey()), e.getValue().size());
            int[] a = new int[len];
            for (int i = 0; i < len; i++) a[i] = e.getValue().get(i);
            out.put(e.getKey(), a);
        }
        return out;
    }

    public static String module(String header) {
        switch (header) {
            case "7E8": return "Engine";
            case "7E9": return "Transmission";
            default: return "Module " + header;
        }
    }

    public static String dtc(int a, int b) {
        char letter = "PCBU".charAt((a >> 6) & 3);
        return String.format(Locale.US, "%c%d%X%X%X", letter, (a >> 4) & 3, a & 0x0F, (b >> 4) & 0x0F, b & 0x0F);
    }

    /** Trouble codes from a mode 03 / 07 / 0A reply read with headers on. Each entry: "P0771|Transmission". */
    public static List<String> dtcs(String resp, int mode) {
        List<String> out = new ArrayList<>();
        int key = mode + 0x40;
        for (Map.Entry<String, int[]> e : isoTp(resp).entrySet()) {
            int[] d = e.getValue();
            if (d.length < 2 || d[0] != key) continue;
            int n = d[1];
            for (int i = 0; i < n && 2 + i * 2 + 1 < d.length; i++) {
                int a = d[2 + i * 2], b = d[3 + i * 2];
                if (a == 0 && b == 0) continue;
                out.add(dtc(a, b) + "|" + module(e.getKey()));
            }
        }
        return out;
    }

    /** VIN from a 0902 reply (headers off). */
    public static String vin(String resp) {
        StringBuilder all = new StringBuilder();
        for (String l : dataLines(resp)) all.append(l);
        String s = all.toString();
        int at = s.indexOf("4902");
        if (at < 0) return null;
        int[] b = hexBytes(s.substring(at + 4));
        StringBuilder v = new StringBuilder();
        for (int i = 1; i < b.length; i++) {
            char c = (char) b[i];
            if (c >= '0' && c <= 'Z' && Character.isLetterOrDigit(c)) v.append(c);
        }
        String r = v.toString();
        return r.length() >= 17 ? r.substring(r.length() - 17) : (r.isEmpty() ? null : r);
    }

    /** Volts from an ATRV reply like "12.6V". */
    public static float volts(String resp) {
        if (resp == null) return Float.NaN;
        StringBuilder n = new StringBuilder();
        for (char c : resp.toCharArray()) if ((c >= '0' && c <= '9') || c == '.') n.append(c);
        try { return Float.parseFloat(n.toString()); } catch (NumberFormatException e) { return Float.NaN; }
    }

    public static boolean noCar(String resp) {
        String r = resp == null ? "" : resp.toUpperCase(Locale.US);
        return r.contains("UNABLE TO CONNECT") || r.contains("CAN ERROR") || r.contains("BUS ERROR")
                || r.contains("BUS INIT") && r.contains("ERROR") || r.contains("NO DATA") && !r.contains("41");
    }

    public static String protocolName(String dpn) {
        String n = dpn == null ? "" : dpn.trim().toUpperCase(Locale.US).replace("A", "");
        String[] names = { "Auto", "SAE J1850 PWM", "SAE J1850 VPW", "ISO 9141-2", "ISO 14230-4 KWP (5 baud)",
                "ISO 14230-4 KWP (fast)", "ISO 15765-4 CAN 11 bit 500k", "ISO 15765-4 CAN 29 bit 500k",
                "ISO 15765-4 CAN 11 bit 250k", "ISO 15765-4 CAN 29 bit 250k", "SAE J1939 CAN" };
        try {
            int i = Integer.parseInt(n.isEmpty() ? "0" : n.substring(n.length() - 1), 16);
            return i < names.length ? names[i] : "Protocol " + n;
        } catch (NumberFormatException e) { return dpn; }
    }
}
