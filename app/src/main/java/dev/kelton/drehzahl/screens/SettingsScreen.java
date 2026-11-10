package dev.kelton.drehzahl.screens;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.icons.IconPack;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.ui.Controls;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.SplitScreen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Settings: display, time, units, CarPlay, home and apps, connections, tablet info, terms, credits, reset. */
public class SettingsScreen extends SplitScreen {
    public static final int DISPLAY = 0, TIME = 1, UNITS = 2, CARPLAY = 3, HOME = 4, CONNECTIONS = 5, INFO = 6, TERMS = 7, CREDITS = 8, RESET = 9;
    private static final String[] ITEMS = { "Display", "Time and date", "Units", "CarPlay", "Home and apps", "Connections",
            "Tablet info", "Terms and conditions", "Credits", "Reset" };
    private static final int[] ICONS = { Icons.DISPLAY, Icons.CLOCK, Icons.UNITS, Icons.CARPLAY, Icons.GRID, Icons.BLUETOOTH,
            Icons.INFO, Icons.DOC, Icons.STAR, Icons.RESET };

    @Override public String title() { return "Settings"; }
    @Override public int section() { return MainActivity.SETTINGS; }
    @Override protected String[] items() { return ITEMS; }
    @Override protected int icon(int i) { return ICONS[i]; }

    @Override protected void page(int i, FrameLayout hostView) {
        Context c = host;
        LinearLayout p = W.page(c, hostView);
        switch (i) {
            case DISPLAY: display(c, p); break;
            case TIME: time(c, p); break;
            case UNITS: units(c, p); break;
            case CARPLAY: carplay(c, p); break;
            case HOME: home(c, p); break;
            case CONNECTIONS: connections(c, p); break;
            case INFO: info(c, p); break;
            case TERMS: Legal.terms(c, p); break;
            case CREDITS: Legal.credits(c, p); break;
            default: reset(c, p);
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { host.startActivity(i); } catch (RuntimeException e) { W.toast(host, "This tablet does not have that settings page"); }
    }

    // ---- Display ----
    /** Follow car power: screen off when the ignition turns off, startup animation and home screen when it comes back. */
    private void powerRows(final Context c, LinearLayout p) {
        final boolean on = Prefs.b(Prefs.POWER_FOLLOW, false);
        String sub = "Turns the screen off when the car's power goes away. When power comes back the screen wakes, "
                + "the startup animation plays and the home screen opens.";
        if (dev.kelton.drehzahl.core.PowerFollow.secureLock(c)) sub += " This tablet has a screen lock, so it wakes to the lock screen.";
        else if (on && !dev.kelton.drehzahl.core.PowerFollow.canLock(c)) sub += " Not allowed to turn the screen off yet: switch this off and on again.";
        p.addView(W.toggleRow(c, "Follow car power", sub, on, new Controls.BoolCb() {
            public void on(boolean v) {
                Prefs.put(Prefs.POWER_FOLLOW, v);
                if (v && !dev.kelton.drehzahl.core.PowerFollow.canLock(host)) {
                    Intent i = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, dev.kelton.drehzahl.core.PowerFollow.admin(host))
                            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Lets Drehzahl turn the screen off when the car's power goes away. This is the only thing it is allowed to do.");
                    try { host.startActivity(i); } catch (RuntimeException e) { W.toast(host, "Android did not open the permission screen"); }
                }
                if (!v) dev.kelton.drehzahl.core.PowerFollow.dropAdmin(host);
                refreshPage();
            }
        }));
        if (!on) return;
        int wait = Prefs.i(Prefs.POWER_WAIT, 10), sel = 2;
        for (int k = 0; k < dev.kelton.drehzahl.core.PowerFollow.WAIT_S.length; k++) if (dev.kelton.drehzahl.core.PowerFollow.WAIT_S[k] == wait) sel = k;
        p.addView(W.choiceRow(c, "Wait before the screen turns off", "Keeps it on while the engine cranks and the car briefly cuts power",
                new String[] { "None", "5 s", "10 s", "30 s", "1 min" }, sel, new Controls.IntCb() {
                    public void on(int v) { Prefs.put(Prefs.POWER_WAIT, dev.kelton.drehzahl.core.PowerFollow.WAIT_S[v]); }
                }));
        p.addView(W.toggleRow(c, "Pause music when power is lost", null, Prefs.b(Prefs.POWER_PAUSE, true), new Controls.BoolCb() {
            public void on(boolean v) { Prefs.put(Prefs.POWER_PAUSE, v); }
        }));
    }

    private void display(Context c, LinearLayout p) {
        p.addView(W.header(c, "Theme"));
        for (int i = 0; i < Theme.STYLE_NAMES.length; i++) {
            final int idx = i;
            View mark = i == Theme.style ? W.icon(c, Icons.STAR, Theme.accent, 26) : null;
            p.addView(W.row(c, Theme.STYLE_NAMES[i], Theme.STYLE_INFO[i], mark, new View.OnClickListener() {
                public void onClick(View v) {
                    if (idx == Theme.style) return;
                    Prefs.put(Prefs.STYLE, idx);
                    host.styleChanged();
                }
            }));
        }
        p.addView(W.toggleRow(c, "Startup animation", "Plays when Drehzahl starts and after picking a theme. Tap to skip it.",
                Prefs.b(Prefs.INTRO, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.INTRO, v); } }));
        p.addView(W.chevronRow(c, "Startup text", Prefs.s(Prefs.INTRO_TEXT, "GLI"), new View.OnClickListener() {
            public void onClick(View v) {
                W.askText(host, "Startup text", Prefs.s(Prefs.INTRO_TEXT, "GLI"), new W.TextCb() {
                    public void on(String t) { Prefs.put(Prefs.INTRO_TEXT, t.trim().isEmpty() ? "GLI" : t.trim()); refreshPage(); }
                });
            }
        }));
        p.addView(W.chevronRow(c, "Play the startup animation", null, new View.OnClickListener() {
            public void onClick(View v) { host.playIntro(); }
        }));
        p.addView(W.header(c, "Screen"));
        String autoSub = host.hasLightSensor() ? "Auto switches with the tablet's light sensor" : "Auto switches at 7:00 and 19:00 (no light sensor found)";
        p.addView(W.choiceRow(c, "Day and night", autoSub, new String[] { "Night", "Day", "Auto" }, Prefs.i(Prefs.THEME, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.THEME, v); host.themeModeChanged(); }
        }));
        LinearLayout accent = W.choiceRow(c, "Accent color", Prefs.i(Prefs.PROFILE, 1) == 0 || Prefs.i(Prefs.PROFILE, 1) == 2
                ? "The Eco and Sport driving profiles set their own color" : null, Theme.ACCENT_NAMES, Theme.accentIndex(), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Theme.accentKey(), v); host.rebuild(); }
        });
        p.addView(accent);

        final boolean canWrite = Settings.System.canWrite(c);
        int cur = 128;
        try { cur = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS); } catch (Settings.SettingNotFoundException ignored) { }
        p.addView(W.sliderRow(c, "Brightness", 1, 100, Math.max(1, Math.round(cur / 2.55f)), false, new Controls.Fmt() {
            public String f(int v) { return v + "%"; }
        }, new Controls.IntCb() {
            public void on(int v) {
                if (canWrite) {
                    Settings.System.putInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(host.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.round(v * 2.55f));
                } else {
                    WindowManager.LayoutParams lp = host.getWindow().getAttributes();
                    lp.screenBrightness = v / 100f;
                    host.getWindow().setAttributes(lp);
                }
            }
        }));
        if (!canWrite) {
            p.addView(W.chevronRow(c, "Allow system brightness", "Without this, brightness only applies while Drehzahl is open", new View.OnClickListener() {
                public void onClick(View v) { open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + host.getPackageName()))); }
            }));
        }
        p.addView(W.toggleRow(c, "Hide Android bars", "Swipe from an edge to show them for a moment",
                Prefs.b(Prefs.HIDE_BARS, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.HIDE_BARS, v); host.applyWindow(); } }));
        p.addView(W.toggleRow(c, "Keep screen on while Drehzahl is open", null,
                Prefs.b(Prefs.KEEP_ON, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.KEEP_ON, v); host.applyWindow(); } }));
        int stay = Settings.Global.getInt(c.getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
        final boolean secure = c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
        View stayRow = W.toggleRow(c, "Keep screen on while charging", secure ? "Covers every app, including CarPlay" : "Needs the Install step in the PC toolkit",
                stay != 0, new Controls.BoolCb() {
                    public void on(boolean v) {
                        try { Settings.Global.putInt(host.getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, v ? 7 : 0); }
                        catch (RuntimeException e) { W.toast(host, "Not allowed yet. Run Install in the PC toolkit."); }
                    }
                });
        p.addView(stayRow);
        if (!secure) W.setEnabledDeep(stayRow, false);
        powerRows(c, p);
        p.addView(W.toggleRow(c, "Animations", "Fade between screens",
                Prefs.b(Prefs.ANIM, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.ANIM, v); } }));
        p.addView(W.chevronRow(c, "Screen off", "Turns the screen black. Tap anywhere to wake it. Long press the clock does the same.", new View.OnClickListener() {
            public void onClick(View v) { host.screenOff(); }
        }));
    }

    // ---- Time ----
    private void time(Context c, LinearLayout p) {
        p.addView(W.toggleRow(c, "24-hour clock", null, Prefs.b(Prefs.CLOCK24, false), new Controls.BoolCb() {
            public void on(boolean v) { Prefs.put(Prefs.CLOCK24, v); }
        }));
        p.addView(W.toggleRow(c, "Show seconds", "In the top bar", Prefs.b(Prefs.SECONDS, false), new Controls.BoolCb() {
            public void on(boolean v) { Prefs.put(Prefs.SECONDS, v); }
        }));
        p.addView(W.choiceRow(c, "Date format", null, new String[] { "Thu, Nov 5", "11/05/2026", "2026-11-05" }, Prefs.i(Prefs.DATE_FMT, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.DATE_FMT, v); }
        }));
        p.addView(W.chevronRow(c, "Time zone and network time", "Opens Android date and time settings", new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_DATE_SETTINGS)); }
        }));
    }

    // ---- Units ----
    private void units(Context c, LinearLayout p) {
        p.addView(W.choiceRow(c, "Speed and distance", null, new String[] { "mph, mi", "km/h, km" }, Prefs.i(Prefs.U_SPEED, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.U_SPEED, v); }
        }));
        p.addView(W.choiceRow(c, "Temperature", null, new String[] { "°F", "°C" }, Prefs.i(Prefs.U_TEMP, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.U_TEMP, v); }
        }));
        p.addView(W.choiceRow(c, "Boost pressure", null, new String[] { "psi", "bar", "kPa" }, Prefs.i(Prefs.U_PRESS, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.U_PRESS, v); }
        }));
        p.addView(W.choiceRow(c, "Consumption", null, new String[] { "mpg (US)", "L/100 km" }, Prefs.i(Prefs.U_CONS, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.U_CONS, v); }
        }));
        p.addView(W.note(c, "Power and torque follow the speed setting: mph shows hp and lb-ft, km/h shows kW and Nm."));
    }

    // ---- CarPlay ----
    private void carplay(Context c, LinearLayout p) {
        String v = CarLink.version(c);
        p.addView(W.valueRow(c, "CarPlay engine", v == null ? "Not installed" : "CarLink " + v));
        p.addView(W.toggleRow(c, "Open when an iPhone is plugged in", null, Prefs.b(Prefs.AUTO_CP, true), new Controls.BoolCb() {
            public void on(boolean x) { Prefs.put(Prefs.AUTO_CP, x); }
        }));
        p.addView(W.toggleRow(c, "Open when the tablet starts", null, Prefs.b(Prefs.BOOT_CP, false), new Controls.BoolCb() {
            public void on(boolean x) { Prefs.put(Prefs.BOOT_CP, x); }
        }));
        p.addView(W.toggleRow(c, "Pause Wi-Fi search during wireless CarPlay", "Stops music dropping out. Needs root.",
                dev.kelton.drehzahl.core.WifiGuard.enabled(), new Controls.BoolCb() {
            public void on(boolean x) {
                Prefs.put(Prefs.WIFI_GUARD, x);
                if (x && Build.VERSION.SDK_INT >= 33 && !dev.kelton.drehzahl.sys.Root.present()
                        && host.checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    host.requestPermissions(new String[] { "android.permission.NEARBY_WIFI_DEVICES" }, 41);
                }
                dev.kelton.drehzahl.core.WifiGuard.get(host).settingChanged();
                refreshPage();
            }
        }));
        p.addView(W.note(c, dev.kelton.drehzahl.core.WifiGuard.get(c).describe()));
        p.addView(W.chevronRow(c, "Open CarPlay", null, new View.OnClickListener() {
            public void onClick(View x) { if (!CarLink.launch(host)) W.toast(host, "CarLink is not installed"); }
        }));
        p.addView(W.chevronRow(c, "CarLink app info", null, new View.OnClickListener() {
            public void onClick(View x) { CarLink.openAppInfo(host); }
        }));
    }

    // ---- Home and apps ----
    private void home(Context c, LinearLayout p) {
        final Map<String, String> packs = IconPack.installed(c);
        final List<String> pkgs = new ArrayList<>(packs.keySet());
        final List<String> labels = new ArrayList<>();
        labels.add("None (original icons)");
        labels.addAll(packs.values());
        String cur = Prefs.s(Prefs.ICON_PACK, "");
        String curLabel = cur.isEmpty() ? labels.get(0) : (packs.containsKey(cur) ? packs.get(cur) : "Missing: " + cur);
        p.addView(W.chevronRow(c, "Icon pack", curLabel, new View.OnClickListener() {
            public void onClick(View v) {
                W.pick(host, "Icon pack", labels.toArray(new String[0]), new Controls.IntCb() {
                    public void on(int i) {
                        Prefs.put(Prefs.ICON_PACK, i == 0 ? "" : pkgs.get(i - 1));
                        refreshPage();
                    }
                });
            }
        }));
        if (packs.isEmpty()) {
            p.addView(W.note(c, "No icon packs installed. OnePro (Dark) is on the Play Store; the PC toolkit's Install step opens its page."));
        }
        p.addView(W.chevronRow(c, "Default home app", "Pick Drehzahl so the Home button always comes back here", new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        }));
        p.addView(W.chevronRow(c, "Android settings", "The tablet's full settings app", new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_SETTINGS)); }
        }));
    }

    // ---- Connections ----
    private void connections(Context c, LinearLayout p) {
        BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ba = bm == null ? null : bm.getAdapter();
        boolean btPerm = Build.VERSION.SDK_INT < 31 || c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED;
        p.addView(W.valueRow(c, "Bluetooth", ba == null ? "Not available" : (ba.isEnabled() ? "On" : "Off")));
        p.addView(W.chevronRow(c, "Bluetooth settings", "Pair the iPhone once for wireless CarPlay", new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        }));
        if (ba != null && ba.isEnabled() && btPerm) {
            try {
                Set<BluetoothDevice> bonded = ba.getBondedDevices();
                if (!bonded.isEmpty()) p.addView(W.header(c, "Paired devices"));
                for (BluetoothDevice d : bonded) p.addView(W.valueRow(c, d.getName() == null ? d.getAddress() : d.getName(), "paired"));
            } catch (SecurityException ignored) { }
        }
        p.addView(W.chevronRow(c, "Wi-Fi settings", null, new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        }));
        p.addView(W.chevronRow(c, "Hotspot and tethering", "If you want the tablet online from your phone's hotspot", new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        }));
        dockRows(c, p);
    }

    private void dockRows(final Context c, LinearLayout p) {
        p.addView(W.header(c, "USB-C dock"));
        final boolean on = dev.kelton.drehzahl.core.DockMode.on();
        p.addView(W.toggleRow(c, "Dock mode",
                "For a powered USB-C hub or dock in the car. When the dock gets power, the tablet charges from it and still sees "
                        + "a phone plugged into it, so wired CarPlay starts by itself. When the dock loses power, this tablet still "
                        + "switches to powering it (and a phone left on it). Needs root.",
                on, new Controls.BoolCb() {
            public void on(final boolean v) {
                if (!dev.kelton.drehzahl.sys.Root.present()) { W.toast(host, "Dock mode needs root"); refreshPage(); return; }
                W.toast(host, v ? "Switching dock mode on. If a Grant popup shows, tap Grant." : "Switching dock mode off");
                dev.kelton.drehzahl.core.DockMode.set(v, new dev.kelton.drehzahl.core.DockMode.Done() {
                    public void on(boolean ok, String out) {
                        W.toast(host, ok ? (v ? "Dock mode is on. It takes effect the next time the tablet is plugged in." : "Dock mode is off")
                                : "Could not change dock mode. Check that root is granted to Drehzahl.");
                        refreshPage();
                    }
                });
            }
        }));
        if (!on) return;
        final TextView log = W.note(c, "Reading the dock log...");
        p.addView(log);
        dev.kelton.drehzahl.core.DockMode.log(8, new dev.kelton.drehzahl.core.DockMode.Done() {
            public void on(boolean ok, String out) {
                String t = out == null ? "" : out.trim();
                boolean running = t.matches("(?s).*running=[0-9]+$");
                t = t.replaceAll("\\s*running=[0-9]*$", "").trim();
                log.setText((running ? "Running." : "Not running. Switch dock mode off and on again.")
                        + (t.isEmpty() ? "" : "\n" + t));
            }
        });
    }

    // ---- Tablet info ----
    private void info(Context c, LinearLayout p) {
        PackageManager pm = c.getPackageManager();
        String ver = "?";
        try { PackageInfo pi = pm.getPackageInfo(c.getPackageName(), 0); ver = pi.versionName; } catch (PackageManager.NameNotFoundException ignored) { }
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        p.addView(W.valueRow(c, "Drehzahl version", ver));
        p.addView(W.valueRow(c, "Tablet", Build.MANUFACTURER + " " + Build.MODEL));
        p.addView(W.valueRow(c, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        p.addView(W.valueRow(c, "Screen", dm.widthPixels + " x " + dm.heightPixels + " px, " + dm.densityDpi + " dpi"
                + (Theme.fit < 1f ? ", layout scaled to " + Math.round(Theme.fit * 100) + "%" : "")));
        p.addView(W.header(c, "Hardware Drehzahl can use"));
        p.addView(W.valueRow(c, "Accelerometer (g-meter)", yes(sm != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)));
        p.addView(W.valueRow(c, "Light sensor (auto night mode)", yes(sm != null && sm.getDefaultSensor(Sensor.TYPE_LIGHT) != null)));
        p.addView(W.valueRow(c, "Gyroscope", yes(sm != null && sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)));
        p.addView(W.valueRow(c, "GPS (speed)", yes(pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))));
        p.addView(W.valueRow(c, "USB host (wired CarPlay, USB audio)", yes(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST))));
        p.addView(W.valueRow(c, "Wi-Fi Direct (wireless CarPlay)", yes(pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT))));
        p.addView(W.valueRow(c, "Bluetooth", yes(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))));
    }

    private static String yes(boolean b) { return b ? "Yes" : "No"; }

    // ---- Reset ----
    private void reset(Context c, LinearLayout p) {
        final java.io.File f = Prefs.backupFile(c);
        p.addView(W.note(c, "Save every Drehzahl setting to a file before resetting the tablet, and load it again afterwards. "
                + "File: " + f.getAbsolutePath() + (f.isFile() ? " (saved " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(f.lastModified())) + ")" : " (not saved yet)")));
        LinearLayout row = new LinearLayout(c);
        row.addView(W.button(c, "Save settings to the file", false, new View.OnClickListener() {
            public void onClick(View v) { saveSettings(host, f); refreshPage(); }
        }), W.lp(W.WRAP, W.WRAP));
        if (f.isFile()) {
            row.addView(W.button(c, "Load settings from the file", false, new View.OnClickListener() {
                public void onClick(View v) {
                    W.confirm(host, "Load settings", "Settings in the file replace the current ones.", new Runnable() {
                        public void run() { loadSettings(host, f); host.themeModeChanged(); }
                    });
                }
            }), W.lp(W.WRAP, W.WRAP));
        }
        p.addView(row, W.lp(W.MATCH, W.WRAP));
        p.addView(W.note(c, "Puts every Drehzahl setting back to default: sound, units, gauges, trip memories, service data and display. "
                + "Apps, CarLink and the tablet's own settings are not touched."));
        p.addView(W.button(c, "Reset Drehzahl settings", true, new View.OnClickListener() {
            public void onClick(View v) {
                W.confirm(host, "Reset everything", "This cannot be undone.", new Runnable() {
                    public void run() { Prefs.resetAll(); host.themeModeChanged(); }
                });
            }
        }), W.lp(W.WRAP, W.WRAP));
    }

    public static void saveSettings(Context c, java.io.File f) {
        try {
            int n = Prefs.export(f);
            dev.kelton.drehzahl.core.EventLog.add("settings: saved " + n + " to " + f.getAbsolutePath());
            W.toast(c, "Saved " + n + " settings");
        } catch (java.io.IOException e) {
            dev.kelton.drehzahl.core.EventLog.add("settings: save failed, " + e.getMessage());
            W.toast(c, "Could not save: " + e.getMessage());
        }
    }

    public static boolean loadSettings(Context c, java.io.File f) {
        try {
            int n = Prefs.restore(f);
            dev.kelton.drehzahl.core.EventLog.add("settings: loaded " + n + " from " + f.getAbsolutePath());
            W.toast(c, "Loaded " + n + " settings");
            return true;
        } catch (java.io.IOException e) {
            dev.kelton.drehzahl.core.EventLog.add("settings: load failed, " + e.getMessage());
            W.toast(c, "Could not load: " + e.getMessage());
            return false;
        }
    }
}
