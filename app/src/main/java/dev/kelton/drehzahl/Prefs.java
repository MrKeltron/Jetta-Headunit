package dev.kelton.drehzahl;

import android.content.Context;
import android.content.SharedPreferences;

/** Every saved setting lives here. Keys are grouped by screen. */
public final class Prefs {
    private static SharedPreferences sp;

    // Display
    public static final String THEME = "theme";            // 0 night, 1 day, 2 auto (light sensor)
    public static final String STYLE = "style";            // ui.Theme.GLI, CARBON, SIMPLE, CODE, CLASSIC
    public static final String INTRO = "intro";
    public static final String INTRO_TEXT = "intro_text";
    public static final String ACCENT = "accent";
    public static final String HIDE_BARS = "hide_bars";
    public static final String KEEP_ON = "keep_on";
    public static final String ANIM = "anim";
    public static final String CLICK = "click";
    // Time
    public static final String CLOCK24 = "clock24";
    public static final String SECONDS = "seconds";
    public static final String DATE_FMT = "date_fmt";
    // Units
    public static final String U_SPEED = "u_speed";        // 0 mph, 1 km/h
    public static final String U_TEMP = "u_temp";          // 0 F, 1 C
    public static final String U_PRESS = "u_press";        // 0 psi, 1 bar, 2 kPa
    public static final String U_CONS = "u_cons";          // 0 mpg, 1 L/100 km
    // Vehicle
    public static final String SOURCE = "source";          // 0 demo, 1 tablet sensors, 2 OBD2, 3 tuner
    public static final String PROFILE = "profile";        // 0 eco, 1 normal, 2 sport, 3 individual
    public static final String GAUGES = "gauges";
    public static final String G_CAL = "g_cal";
    public static final String ODO_KM = "odo_km";
    public static final String OIL_KM = "oil_km";
    public static final String OIL_DAY = "oil_day";
    public static final String OIL_INT_KM = "oil_int_km";
    public static final String OIL_INT_DAYS = "oil_int_days";
    public static final String INSP_DAY = "insp_day";
    // Sound
    public static final String EQ_ON = "eq_on";
    public static final String EQ_PRESET = "eq_preset";
    public static final String BASS = "tone_bass";
    public static final String MID = "tone_mid";
    public static final String TREBLE = "tone_treble";
    public static final String BALANCE = "balance";
    public static final String FADER = "fader";
    public static final String FOCUS = "focus";            // 0 all, 1 front, 2 driver
    public static final String BASS_BOOST = "bass_boost";  // 0..10
    public static final String SURROUND = "surround";      // 0..10
    public static final String LOUDNESS = "loudness";      // 0..10
    public static final String COMPRESS = "compress";
    public static final String LIMITER = "limiter";
    public static final String STAGE = "stage";            // reverb preset 0..6
    public static final String SPEED_VOL = "speed_vol";    // 0 off .. 3 high
    public static final String START_VOL = "start_vol";    // max switch-on volume, percent
    public static final String POWER_FOLLOW = "power_follow"; // screen off without power, intro and home when it returns
    public static final String PULLS = "pulls";               // record full-throttle pulls
    public static final String WIFI_GUARD = "wifi_guard";     // pause Wi-Fi network search during wireless CarPlay (root)
    public static final String WIFI_GUARD_PAUSED = "wifi_guard_paused"; // set before a pause, cleared once the search is back on
    public static final String POWER_WAIT = "power_wait";     // seconds to wait before the screen turns off
    public static final String POWER_PAUSE = "power_pause";   // pause music when power is lost
    public static final String DOCK_MODE = "dock_mode";    // root module: charge from a USB-C dock and stay USB host
    public static final String BT_MODULE = "bt_module";    // Drehzahl's Bluetooth receiver module is installed
    public static final String BT_REBOOT = "bt_reboot";    // the receiver module changed and needs a restart
    public static final String BT_NUDGE = "bt_nudge";      // press play when the phone plays but nothing is heard
    public static final String FX_MODE = "fx_mode";        // 0 apps that ask, 1 all players, 2 whole tablet
    // CarPlay / home
    public static final String AUTO_CP = "auto_carplay";
    public static final String BOOT_CP = "boot_carplay";
    public static final String ICON_PACK = "icon_pack";
    // OBD2
    public static final String OBD_LINK = "obd_link";       // 0 USB, 1 Bluetooth
    public static final String OBD_BT_ADDR = "obd_bt_addr";
    public static final String OBD_BT_NAME = "obd_bt_name";
    public static final String OBD_BT_LE = "obd_bt_le";     // the picked adapter talks Bluetooth LE (no pairing)
    public static final String OBD_TRAFFIC = "obd_traffic";

    private Prefs() { }

    public static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("drehzahl", Context.MODE_PRIVATE);
    }

    public static boolean has(String k) { return sp.contains(k); }
    public static int i(String k, int def) { return sp.getInt(k, def); }
    public static boolean b(String k, boolean def) { return sp.getBoolean(k, def); }
    public static float f(String k, float def) { return sp.getFloat(k, def); }
    public static long l(String k, long def) { return sp.getLong(k, def); }
    public static String s(String k, String def) { return sp.getString(k, def); }

    public static void put(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public static void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public static void put(String k, float v) { sp.edit().putFloat(k, v).apply(); }
    public static void put(String k, long v) { sp.edit().putLong(k, v).apply(); }
    public static void put(String k, String v) { sp.edit().putString(k, v).apply(); }

    public static void resetAll() { sp.edit().clear().apply(); }

    /** Writes every setting to a text file, one per line: type, key, value (values URL-encoded). */
    public static int export(java.io.File f) throws java.io.IOException {
        StringBuilder out = new StringBuilder("# Drehzahl settings\n");
        int n = 0;
        for (java.util.Map.Entry<String, ?> e : new java.util.TreeMap<>(sp.getAll()).entrySet()) {
            Object v = e.getValue();
            String t;
            if (v instanceof Integer) t = "i";
            else if (v instanceof Boolean) t = "b";
            else if (v instanceof Float) t = "f";
            else if (v instanceof Long) t = "l";
            else if (v instanceof String) t = "s";
            else continue;
            out.append(t).append('\t').append(enc(e.getKey())).append('\t').append(enc(String.valueOf(v))).append('\n');
            n++;
        }
        java.io.File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("cannot make " + dir);
        try (java.io.OutputStream o = new java.io.FileOutputStream(f)) { o.write(out.toString().getBytes("UTF-8")); }
        return n;
    }

    /** Reads a file written by export. Settings not in the file are left as they are. */
    public static int restore(java.io.File f) throws java.io.IOException {
        SharedPreferences.Editor ed = sp.edit();
        int n = 0;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\t", 3);
                if (p.length < 3) continue;
                String k = dec(p[1]), v = dec(p[2]);
                try {
                    switch (p[0]) {
                        case "i": ed.putInt(k, Integer.parseInt(v)); break;
                        case "b": ed.putBoolean(k, Boolean.parseBoolean(v)); break;
                        case "f": ed.putFloat(k, Float.parseFloat(v)); break;
                        case "l": ed.putLong(k, Long.parseLong(v)); break;
                        case "s": ed.putString(k, v); break;
                        default: continue;
                    }
                    n++;
                } catch (NumberFormatException ignored) { }
            }
        }
        ed.commit();
        return n;
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return s; }
    }

    private static String dec(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    public static java.io.File backupFile(Context c) {
        java.io.File d = c.getExternalFilesDir(null);
        return new java.io.File(d != null ? d : c.getFilesDir(), "settings.txt");
    }
}
