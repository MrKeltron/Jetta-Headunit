package dev.kelton.drehzahl.obd;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.kelton.drehzahl.vehicle.VehicleData;

/**
 * Manufacturer readings: values a car only gives through its own diagnostic IDs (UDS service 22),
 * such as oil temperature on VW engines that do not report it over generic OBD2.
 *
 * The list lives in Android/data/dev.kelton.drehzahl/files/readings.txt, one reading per line:
 *   name  request-header  DID  formula
 *   oil    7E0  11F7  u8-40
 * Names "oil" and "torque" feed the gauges, "torque" and "torque_req" also go into recorded pulls; any other name is only logged. Formulas: u8, u8-40,
 * u16, s16, u16/10, s16/10, k10 (tenths of a kelvin to degrees C), u32. A line "engine 1.984" sets
 * the displacement in litres used to work out airflow when the car has no airflow sensor reading.
 * While any reading is listed, every 2 s a row goes to files/readings/readings-<time>.csv with the
 * generic values next to the raw manufacturer values, so unknown IDs can be matched up after a drive.
 */
final class MfrReadings {
    static final String FILE = "readings.txt";
    private static final long ROW_MS = 2000;
    private static final long REST_MS = 30000;

    static final class Item {
        final String name, tx, rx, formula;
        final int did;
        long raw = -1;
        float value = Float.NaN;
        long at;
        int misses;
        long restUntil;

        Item(String name, String tx, int did, String formula) {
            this.name = name;
            this.tx = tx;
            this.rx = Integer.toHexString(Integer.parseInt(tx, 16) + 8).toUpperCase(Locale.US);
            this.did = did;
            this.formula = formula;
        }
    }

    final List<Item> items = new ArrayList<>();
    float liters = 2.0f;
    private int next;
    private FileWriter csv;
    private long lastRow;
    private final File dir;

    private MfrReadings(File dir) { this.dir = dir; }

    /** Reads the list, writing the starter list the first time. Null when nothing is listed. */
    static MfrReadings load(Context ctx, StringBuilder log) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) return null;
        File f = new File(base, FILE);
        if (!f.exists()) {
            try (FileWriter w = new FileWriter(f)) { w.write(STARTER); }
            catch (IOException e) { log.append("could not write ").append(FILE).append(": ").append(e.getMessage()); return null; }
        }
        MfrReadings m = new MfrReadings(new File(base, "readings"));
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            for (String line; (line = r.readLine()) != null; ) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                String[] p = line.trim().split("\\s+");
                if (p.length == 2 && p[0].equalsIgnoreCase("engine")) {
                    try { m.liters = Float.parseFloat(p[1]); } catch (NumberFormatException ignored) { }
                    continue;
                }
                if (p.length < 3) continue;
                try {
                    m.items.add(new Item(p[0].toLowerCase(Locale.US), p[1].toUpperCase(Locale.US),
                            Integer.parseInt(p[2], 16), p.length > 3 ? p[3].toLowerCase(Locale.US) : "raw"));
                } catch (NumberFormatException e) {
                    log.append("skipped line \"").append(line.trim()).append("\" ");
                }
            }
        } catch (IOException e) {
            log.append("could not read ").append(FILE).append(": ").append(e.getMessage());
            return null;
        }
        return m;
    }

    /** The next reading to ask for, skipping ones resting after repeated refusals. */
    Item nextDue(long now) {
        for (int k = 0; k < items.size(); k++) {
            Item it = items.get((next + k) % items.size());
            if (now >= it.restUntil) { next = (next + k + 1) % items.size(); return it; }
        }
        return null;
    }

    /** Takes the joined hex reply ("62" + DID + data). True when it held the reading. */
    boolean handle(Item it, String hex, long now) {
        String want = String.format(Locale.US, "62%04X", it.did);
        int i = hex.indexOf(want);
        if (i < 0 || hex.length() <= i + 6) {
            if (++it.misses >= 3) { it.restUntil = now + REST_MS; it.misses = 0; }
            return false;
        }
        String data = hex.substring(i + 6);
        if (data.length() > 8) data = data.substring(0, 8);
        if (data.length() % 2 != 0 || !data.matches("^[0-9A-F]+$")) return false;
        it.misses = 0;
        it.raw = Long.parseLong(data, 16);
        it.value = convert(it.formula, it.raw, data.length() / 2);
        it.at = now;
        return true;
    }

    static float convert(String formula, long raw, int bytes) {
        switch (formula) {
            case "u8": return raw & 0xFF;
            case "u8-40": return (raw & 0xFF) - 40f;
            case "u16": return raw & 0xFFFF;
            case "s16": return (short) (raw & 0xFFFF);
            case "u16/10": return (raw & 0xFFFF) / 10f;
            case "s16/10": return (short) (raw & 0xFFFF) / 10f;
            case "k10": return (raw & 0xFFFF) / 10f - 273.15f;
            case "u32": return raw & 0xFFFFFFFFL;
            default: return raw;
        }
    }

    Item named(String name) {
        for (Item it : items) if (it.name.equals(name)) return it;
        return null;
    }

    /** A row of the drive log every 2 s. */
    void row(long now, VehicleData d, float absLoad) {
        if (now - lastRow < ROW_MS) return;
        lastRow = now;
        try {
            if (csv == null) {
                if (!dir.exists() && !dir.mkdirs()) return;
                csv = new FileWriter(new File(dir, "readings-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".csv"));
                StringBuilder h = new StringBuilder("time,rpm,speed_kmh,coolant_c,iat_c,load_pct,abs_load_pct,boost_kpa,maf_gs");
                for (Item it : items) h.append(',').append(it.name).append('_').append(String.format(Locale.US, "%04X", it.did));
                csv.write(h.append('\n').toString());
            }
            StringBuilder b = new StringBuilder(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
            b.append(String.format(Locale.US, ",%.0f,%.0f,%.0f,%.0f,%.1f,%.1f,%.0f,%.1f", d.rpm, d.speedKmh, d.coolantC, d.iatC,
                    d.load * 100f, absLoad, d.boostKpa, d.mafGs));
            for (Item it : items) b.append(',').append(it.raw < 0 || now - it.at > 15000 ? "" : String.valueOf(it.raw));
            csv.write(b.append('\n').toString());
            csv.flush();
        } catch (IOException ignored) { }
    }

    void close() {
        try { if (csv != null) csv.close(); } catch (IOException ignored) { }
        csv = null;
    }

    /**
     * Starter list for a VW/Audi engine computer (7E0), from a Car scan of a 2015 Jetta GLI (EA888,
     * data set EV_ECM20TFS02006K997071H), matched against drives on Nov 15, 2026.
     */
    private static final String STARTER =
            "# Drehzahl manufacturer readings. One per line: name header DID formula\n"
            + "# Names oil, torque and torque_req are used; anything else is only logged to files/readings/*.csv.\n"
            + "# Formulas: u8 u8-40 u16 s16 u16/10 s16/10 k10 u32 raw. Delete every line to turn this off.\n"
            + "engine 1.984\n"
            + "# Engine oil temperature from the ECU (22 202F, tenths of a kelvin). Runs hotter than the dash's oil reading on the road.\n"
            + "oil 7E0 202F k10\n"
            + "# Torque made and torque asked for, tenths of Nm (22 2061 and 22 20EB). The pull recorder reads both during pulls.\n"
            + "torque 7E0 2061 u16/10\ntorque_req 7E0 20EB u16/10\n"
            + "# Candidates for the dash's oil temperature, only logged:\n"
            + "t 7E0 11CD u8-40\nt 7E0 16B0 u8-40\nt 7E0 1432 u8-40\nt 7E0 14A6 u8-40\nt 7E0 143E u8-40\nk 7E0 1133 k10\nk 7E0 2065 k10\n"
            + "# Pedal and throttle in tenths of a percent (likely), only logged:\n"
            + "n 7E0 20BA u16\nn 7E0 20E6 u16\n";
}
