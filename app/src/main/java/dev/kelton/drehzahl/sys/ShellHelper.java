package dev.kelton.drehzahl.sys;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Runs on the tablet as the shell user, so Drehzahl can do the few things Android only lets
 * a computer do, without root. The PC toolkit starts it over adb:
 *   CLASSPATH=<Drehzahl apk> setsid nohup app_process /system/bin --nice-name=drehzahl_helper
 *       dev.kelton.drehzahl.sys.ShellHelper <port> <token>
 * It listens on 127.0.0.1 only, answers one line per connection (each on its own thread, so a slow
 * Wi-Fi call never blocks a ping), and needs the token.
 * Commands: "ping", "autojoin on|off|state", "lowlat on|off" (Wi-Fi low-latency mode),
 * "probe" (CarPlay audio loss and the age of the newest Wi-Fi scan). It keeps running until the tablet restarts.
 */
public final class ShellHelper {
    public static final String NAME = "drehzahl_helper";
    public static final String RECEIVER = "dev.kelton.drehzahl/dev.kelton.drehzahl.core.HelperReceiver";
    private static final String LOG = "/data/local/tmp/drehzahl_helper.log";

    private ShellHelper() { }

    public static void main(String[] args) {
        if (args.length < 2) { System.out.println("usage: ShellHelper <port> <token>"); return; }
        int port = Integer.parseInt(args[0]);
        String token = args[1];
        try {
            ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
            log("started, uid " + android.os.Process.myUid() + ", port " + port);
            final int fp = port;
            final String ft = token;
            new Thread(new Runnable() { public void run() { announce(fp, ft); } }).start();
            final String tk = token;
            while (true) {
                final Socket s = server.accept();
                new Thread(new Runnable() {
                    public void run() {
                        try { serve(s, tk); }
                        catch (Exception e) { log("request failed: " + e); }
                        finally { try { s.close(); } catch (Exception ignored) { } }
                    }
                }).start();
            }
        } catch (Exception e) {
            log("stopped: " + e);
        }
    }

    private static void serve(Socket s, String token) throws Exception {
        s.setSoTimeout(5000);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        String line = in.readLine();
        String reply;
        String[] p = line == null ? new String[0] : line.trim().split(" ");
        if (p.length < 2 || !same(p[0], token)) reply = "denied token";
        else if (p[1].equals("ping")) reply = "ok helper uid=" + android.os.Process.myUid();
        else if (p[1].equals("autojoin") && p.length > 2) reply = WifiAutojoin.command(p[2]);
        else if (p[1].equals("lowlat") && p.length > 2) reply = lowLatency(p[2].equals("on"));
        else if (p[1].equals("probe")) reply = probe();
        else reply = "error unknown command";
        if (p.length > 1 && !(p[1].equals("probe") && reply.startsWith("ok"))) log((p.length > 2 ? p[1] + " " + p[2] : p[1]) + " -> " + reply);
        OutputStream out = s.getOutputStream();
        out.write((reply + "\n").getBytes("UTF-8"));
        out.flush();
    }

    /** Keeps the Wi-Fi radio awake and cuts background scans while wireless CarPlay runs. */
    private static String lowLatency(boolean on) {
        String out = run(new String[] { "cmd", "wifi", "force-low-latency-mode", on ? "enabled" : "disabled" }).trim();
        String low = out.toLowerCase(Locale.US);
        if (low.contains("unknown") || low.contains("error") || low.contains("exception")) return "unsupported " + out;
        return "ok low-latency " + (on ? "on" : "off");
    }

    /**
     * "ok lost=<frames> gap=<ms> scan=<seconds>": CarLink's (DiPlay) running count of audio frames it had
     * to fill with silence, the longest gap between audio packets, and how long ago the newest Wi-Fi
     * scan result was seen. -1 means not found.
     */
    private static String probe() {
        long lost = -1, gap = -1;
        String log = run(new String[] { "logcat", "-d", "-t", "4000", "-s", "DiPlay-AudioStats:I" });
        for (String l : log.split("\n")) {
            long v = number(l, "lostFramesFilledTotal=");
            if (v >= 0) lost = v;
            if (l.contains("audioType=media")) { v = number(l, "maxGapMs="); if (v >= 0) gap = v; }
        }
        double newest = -1;
        String scans = run(new String[] { "cmd", "wifi", "list-scan-results" });
        for (String l : scans.split("\n")) {
            String[] f = l.trim().split("\\s+");
            if (f.length < 5 || f[0].indexOf(':') < 0) continue;
            try {
                double age = Double.parseDouble(f[3]);
                if (newest < 0 || age < newest) newest = age;
            } catch (NumberFormatException ignored) { }
        }
        return "ok lost=" + lost + " gap=" + gap + " scan=" + (newest < 0 ? "-1" : String.valueOf(Math.round(newest)));
    }

    private static long number(String line, String key) {
        int i = line.lastIndexOf(key);
        if (i < 0) return -1;
        i += key.length();
        int j = i;
        while (j < line.length() && Character.isDigit(line.charAt(j))) j++;
        if (j == i) return -1;
        try { return Long.parseLong(line.substring(i, j)); } catch (NumberFormatException e) { return -1; }
    }

    private static String run(String[] cmd) {
        try {
            Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder();
            for (String l; (l = r.readLine()) != null; ) b.append(l).append('\n');
            pr.waitFor();
            return b.toString();
        } catch (Exception e) {
            return "error " + e;
        }
    }

    /** Tells Drehzahl where to find this helper. Only senders with DUMP (the shell) reach the receiver. */
    private static void announce(int port, String token) {
        for (int i = 0; i < 5; i++) {
            try {
                Process pr = Runtime.getRuntime().exec(new String[] { "am", "broadcast", "-n", RECEIVER,
                        "--ei", "port", String.valueOf(port), "--es", "token", token });
                BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), "UTF-8"));
                StringBuilder b = new StringBuilder();
                for (String l; (l = r.readLine()) != null; ) b.append(l).append(' ');
                pr.waitFor();
                String out = b.toString().trim();
                log("told Drehzahl: " + out);
                if (out.contains("result=0") || out.contains("completed")) return;
            } catch (Exception e) {
                log("could not tell Drehzahl: " + e);
            }
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
        }
    }

    private static boolean same(String a, String b) {
        if (a.length() != b.length()) return false;
        int d = 0;
        for (int i = 0; i < a.length(); i++) d |= a.charAt(i) ^ b.charAt(i);
        return d == 0;
    }

    private static synchronized void log(String msg) {
        try (FileWriter w = new FileWriter(LOG, true)) {
            w.write(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + msg + "\n");
        } catch (Exception ignored) { }
    }
}
