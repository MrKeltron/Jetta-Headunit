package dev.kelton.drehzahl.obd;

import android.content.Context;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.kelton.drehzahl.core.EventLog;

/**
 * Saves recorded pulls as two files in Android/data/dev.kelton.drehzahl/files/pulls:
 * pull-YYYYMMDD-HHMMSS.csv (every reading, one row each) and .txt (summary on line 1, findings after).
 * Keeps the newest 40.
 */
public final class PullStore {
    private static final int KEEP = 40;

    private PullStore() { }

    public static File dir(Context c) {
        File d = c.getExternalFilesDir("pulls");
        if (d == null) d = new File(c.getFilesDir(), "pulls");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    public static void save(Context c, PullRecorder.Pull p) {
        long wall = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - p.start);
        String name = "pull-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(wall));
        File d = dir(c);
        StringBuilder txt = new StringBuilder(p.summary()).append('\n');
        txt.append("#cuts");
        for (long t : p.cuts) txt.append(' ').append(String.format(Locale.US, "%.2f", (t - p.start) / 1000f));
        txt.append('\n');
        for (String f : p.findings) txt.append(f).append('\n');
        try {
            write(new File(d, name + ".csv"), p.csv());
            write(new File(d, name + ".txt"), txt.toString());
            EventLog.add("pull recorded: " + p.summary() + ". " + p.findings.get(0));
        } catch (IOException e) {
            EventLog.add("pull not saved: " + e.getMessage());
        }
        File[] old = list(c);
        for (int i = KEEP; i < old.length; i++) {
            String base = old[i].getName().replace(".txt", "");
            old[i].delete();
            new File(d, base + ".csv").delete();
        }
    }

    /** Summary files, newest first. */
    public static File[] list(Context c) {
        File[] f = dir(c).listFiles(new java.io.FilenameFilter() {
            public boolean accept(File dir, String n) { return n.startsWith("pull-") && n.endsWith(".txt"); }
        });
        if (f == null) return new File[0];
        Arrays.sort(f, new Comparator<File>() { public int compare(File a, File b) { return b.getName().compareTo(a.getName()); } });
        return f;
    }

    public static List<String> lines(File f) {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            for (String l; (l = r.readLine()) != null; ) if (!l.isEmpty()) out.add(l);
        } catch (IOException ignored) { }
        return out;
    }

    /** Torque cut times in seconds from the start of the pull, from the "#cuts" line. */
    public static float[] cuts(List<String> lines) {
        for (String l : lines) {
            if (!l.startsWith("#cuts")) continue;
            String[] f = l.substring(5).trim().split("\\s+");
            List<Float> out = new ArrayList<>();
            for (String x : f) { try { out.add(Float.parseFloat(x)); } catch (NumberFormatException ignored) { } }
            float[] a = new float[out.size()];
            for (int i = 0; i < a.length; i++) a[i] = out.get(i);
            return a;
        }
        return new float[0];
    }

    /** Lines meant for people: the summary and the findings. */
    public static List<String> readable(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) if (!l.startsWith("#")) out.add(l);
        return out;
    }

    /** Date and time from a file name like pull-20261108-152400.txt. */
    public static Date when(File f) {
        try {
            String n = f.getName().substring(5, 20);
            return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(n);
        } catch (Exception e) {
            return new Date(f.lastModified());
        }
    }

    /** The CSV as columns: header names, then a float[] per column (NaN where blank). */
    public static Object[] columns(File txt) {
        File csv = new File(txt.getParentFile(), txt.getName().replace(".txt", ".csv"));
        List<String> rows = lines(csv);
        if (rows.size() < 2) return null;
        String[] head = rows.get(0).split(",");
        float[][] cols = new float[head.length][rows.size() - 1];
        for (int r = 1; r < rows.size(); r++) {
            String[] f = rows.get(r).split(",", -1);
            for (int k = 0; k < head.length; k++) {
                float v = Float.NaN;
                if (k < f.length && !f[k].isEmpty()) { try { v = Float.parseFloat(f[k]); } catch (NumberFormatException ignored) { } }
                cols[k][r - 1] = v;
            }
        }
        return new Object[] { head, cols };
    }

    private static void write(File f, String s) throws IOException {
        try (OutputStream o = new FileOutputStream(f)) { o.write(s.getBytes("UTF-8")); }
    }
}
