package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.core.EventLog;
import dev.kelton.drehzahl.media.MediaHub;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.SparkView;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.TileView;
import dev.kelton.drehzahl.ui.W;
import dev.kelton.drehzahl.vehicle.Metric;
import dev.kelton.drehzahl.vehicle.Units;
import dev.kelton.drehzahl.vehicle.VehicleData;
import dev.kelton.drehzahl.vehicle.VehicleHub;

/** Head unit home: a status strip on top and a grid of app buttons. The Code theme gets a terminal layout instead. */
public class HomeGridScreen extends Screen {
    private static final class App {
        final int icon; final String label; final Runnable go;
        App(int icon, String label, Runnable go) { this.icon = icon; this.label = label; this.go = go; }
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView clock, date, status, npTitle, npArtist, telemetry, logView, appsHeader;
    private ImageView npArt, npPlay;
    private final List<TextView> readouts = new ArrayList<>();
    private SparkView spark;
    private final String[] readoutIds = { "boost", "oil", "coolant" };
    private int lastGear;
    private final long started = SystemClock.elapsedRealtime();

    @Override public String title() { return "Home"; }
    @Override public int section() { return MainActivity.HOME; }

    private List<App> apps() {
        List<App> a = new ArrayList<>();
        a.add(new App(Icons.MEDIA, "Media", nav(MainActivity.MEDIA)));
        a.add(new App(Icons.CARPLAY, "CarPlay", new Runnable() {
            public void run() { if (CarLink.launch(host)) EventLog.add("carplay: opened CarLink"); else host.go(MainActivity.CARPLAY); }
        }));
        a.add(new App(Icons.NAV, "Navigation", new Runnable() { public void run() { openNavigation(host); } }));
        a.add(new App(Icons.GAUGE, "Performance", nav(MainActivity.PERFORMANCE)));
        a.add(new App(Icons.CAR, "Car", nav(MainActivity.CAR)));
        a.add(new App(Icons.SOUND, "Sound", nav(MainActivity.SOUND)));
        a.add(new App(Icons.WIDGETS, "Widgets", nav(MainActivity.WIDGETS)));
        a.add(new App(Icons.APPS, "Apps", nav(MainActivity.APPS)));
        a.add(new App(Icons.SETTINGS, "Settings", nav(MainActivity.SETTINGS)));
        a.add(new App(Icons.TABLET, "Android", new Runnable() {
            public void run() {
                Intent i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { host.startActivity(i); } catch (RuntimeException ignored) { }
            }
        }));
        return a;
    }

    private Runnable nav(final int section) {
        return new Runnable() { public void run() { host.go(section); } };
    }

    /** CarPlay maps when CarLink is installed, otherwise the first navigation app found. */
    public static void openNavigation(Context c) {
        if (CarLink.launch(c)) return;
        String[] pkgs = { "com.google.android.apps.maps", "com.waze", "app.organicmaps", "net.osmand", "com.here.app.maps" };
        for (String p : pkgs) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); return; }
        }
        W.toast(c, "No navigation app found. Install CarLink for CarPlay maps.");
    }

    @Override protected View build() {
        return Theme.style == Theme.CODE ? buildCode() : buildTiles();
    }

    // ---------- GLI / Carbon / Simple ----------

    private View buildTiles() {
        Context c = host;
        boolean simple = Theme.style == Theme.SIMPLE;
        LinearLayout root = W.vbox(c);
        root.setPadding(Theme.dp(26), Theme.dp(18), Theme.dp(26), Theme.dp(22));

        LinearLayout strip = W.hbox(c);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tbox = W.vbox(c);
        clock = W.text(c, "", simple ? 92 : 76, Theme.TEXT, Theme.light());
        date = W.text(c, "", 18, Theme.DIM, Theme.medium());
        if (Theme.upper) date.setLetterSpacing(0.1f);
        status = W.text(c, "", 14, Theme.DIM);
        tbox.addView(clock);
        tbox.addView(date);
        status.setPadding(0, Theme.dp(6), 0, 0);
        tbox.addView(status);
        strip.addView(tbox, W.lp(W.WRAP, W.WRAP));
        strip.addView(W.gap(c, 28));

        LinearLayout np = W.hbox(c);
        np.setBackground(Theme.round(Theme.PANEL, 16));
        np.setPadding(Theme.dp(12), Theme.dp(12), Theme.dp(16), Theme.dp(12));
        npArt = new ImageView(c);
        npArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        npArt.setBackground(Theme.round(Theme.PANEL2, 10));
        npArt.setClipToOutline(true);
        np.addView(npArt, new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84)));
        LinearLayout npText = W.vbox(c);
        npText.setPadding(Theme.dp(14), 0, Theme.dp(8), 0);
        npTitle = W.text(c, "", 20, Theme.TEXT, Theme.medium());
        npTitle.setMaxLines(1);
        npArtist = W.text(c, "", 15, Theme.DIM);
        npArtist.setMaxLines(1);
        npArtist.setPadding(0, Theme.dp(6), 0, 0);
        npText.addView(npTitle);
        npText.addView(npArtist);
        np.addView(npText, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        npPlay = small(c, Icons.PLAY, new Runnable() { public void run() { MediaHub.get(host).playPause(); } });
        np.addView(npPlay);
        np.addView(small(c, Icons.NEXT, new Runnable() { public void run() { MediaHub.get(host).next(); } }));
        np.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { host.go(MainActivity.MEDIA); } });
        strip.addView(np, new LinearLayout.LayoutParams(0, W.WRAP, 1f));

        readouts.clear();
        if (!simple) {
            for (String id : readoutIds) {
                LinearLayout r = W.vbox(c);
                r.setGravity(Gravity.END);
                r.setPadding(Theme.dp(26), 0, 0, 0);
                TextView val = W.text(c, "--", 34, Theme.TEXT, Theme.light());
                TextView lab = W.text(c, Theme.label(Metric.byId(id).label), 13, Theme.DIM, Theme.medium());
                if (Theme.upper) lab.setLetterSpacing(0.1f);
                r.addView(val);
                r.addView(lab);
                readouts.add(val);
                r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { host.go(MainActivity.PERFORMANCE); } });
                strip.addView(r);
            }
        }
        root.addView(strip, W.lp(W.MATCH, W.WRAP));

        View rule = new View(c);
        if (Theme.style == Theme.GLI) {
            rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] { Theme.accent, Theme.alpha(Theme.accent, 0.35f), 0x00000000 }));
            root.addView(rule, W.margins(W.lp(W.MATCH, Theme.dp(3)), 0, 18, 0, 18));
        } else {
            rule.setBackgroundColor(simple ? 0x00000000 : Theme.LINE);
            root.addView(rule, W.margins(W.lp(W.MATCH, Theme.dp(1)), 0, 18, 0, 18));
        }

        List<App> list = apps();
        int cols = 5;
        for (int row = 0; row < 2; row++) {
            LinearLayout r = W.hbox(c);
            for (int k = 0; k < cols; k++) {
                int idx = row * cols + k;
                LinearLayout.LayoutParams lp = W.weight(1);
                lp.setMargins(k == 0 ? 0 : Theme.dp(7), 0, k == cols - 1 ? 0 : Theme.dp(7), 0);
                if (idx < list.size()) {
                    final App a = list.get(idx);
                    r.addView(new TileView(c, a.icon, a.label, a.go), lp);
                } else {
                    r.addView(new View(c), lp);
                }
            }
            LinearLayout.LayoutParams rl = W.vweight(1);
            rl.bottomMargin = row == 0 ? Theme.dp(14) : 0;
            root.addView(r, rl);
        }
        return root;
    }

    private ImageView small(Context c, int kind, final Runnable r) {
        ImageView iv = W.icon(c, kind, Theme.TEXT, 26);
        iv.setPadding(Theme.dp(13), Theme.dp(13), Theme.dp(13), Theme.dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(52));
        lp.leftMargin = Theme.dp(8);
        iv.setLayoutParams(lp);
        iv.setBackground(Theme.round(Theme.PANEL2, 26));
        iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return iv;
    }

    // ---------- Code ----------

    private View buildCode() {
        Context c = host;
        LinearLayout root = W.hbox(c);
        root.setGravity(Gravity.TOP);
        root.setPadding(Theme.dp(20), Theme.dp(16), Theme.dp(20), Theme.dp(16));

        LinearLayout left = pane(c);
        appsHeader = mono(c, "", 15, Theme.accent);
        left.addView(appsHeader);
        left.addView(mono(c, "$ ls /apps", 15, Theme.DIM));
        List<App> list = apps();
        for (int i = 0; i < list.size(); i++) {
            final App a = list.get(i);
            final TextView row = mono(c, String.format(Locale.US, "  %02d  %-12s ./%s", i + 1, a.label.toLowerCase(Locale.US),
                    a.label.toLowerCase(Locale.US).replace(' ', '_')), 20, Theme.TEXT);
            row.setPadding(Theme.dp(4), Theme.dp(9), Theme.dp(4), Theme.dp(9));
            row.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) row.setBackgroundColor(Theme.alpha(Theme.accent, 0.18f));
                    if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) row.setBackgroundColor(0);
                    return false;
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { EventLog.add("exec: ./" + a.label.toLowerCase(Locale.US)); a.go.run(); }
            });
            left.addView(row, W.lp(W.MATCH, W.WRAP));
        }
        left.addView(W.gap(c, 10));
        status = mono(c, "", 14, Theme.DIM);
        left.addView(status);
        npTitle = mono(c, "", 14, Theme.DIM);
        npTitle.setMaxLines(2);
        left.addView(npTitle);
        root.addView(left, W.margins(W.weight(0.95f), 0, 0, 10, 0));

        LinearLayout right = pane(c);
        clock = mono(c, "", 15, Theme.accent);
        right.addView(clock);
        right.addView(mono(c, "$ telemetry --live --hz 7", 15, Theme.DIM));
        telemetry = mono(c, "", 17, Theme.TEXT);
        telemetry.setLineSpacing(0, 1.15f);
        telemetry.setPadding(0, Theme.dp(6), 0, Theme.dp(10));
        right.addView(telemetry);
        spark = new SparkView(c, 180, -10f, 22f, "boost psi");
        spark.setBackground(Theme.outline(Theme.alpha(Theme.BG, 0.6f), Theme.LINE, 2));
        right.addView(spark, W.lp(W.MATCH, Theme.dp(110)));
        right.addView(mono(c, "$ tail -f /var/log/drehzahl", 15, Theme.DIM), W.margins(W.lp(W.MATCH, W.WRAP), 0, 10, 0, 4));
        logView = mono(c, "", 14, Theme.DIM);
        logView.setLineSpacing(0, 1.15f);
        right.addView(logView, W.vweight(1));
        root.addView(right, W.margins(W.weight(1.25f), 10, 0, 0, 0));
        return root;
    }

    private LinearLayout pane(Context c) {
        LinearLayout l = W.vbox(c);
        l.setBackground(Theme.outline(Theme.alpha(Theme.PANEL, 0.88f), Theme.LINE, 2));
        l.setPadding(Theme.dp(16), Theme.dp(12), Theme.dp(16), Theme.dp(12));
        return l;
    }

    private static TextView mono(Context c, String s, float sp, int color) {
        TextView t = W.text(c, s, sp, color, Theme.mono());
        t.setIncludeFontPadding(true);
        return t;
    }

    private static String bar(float frac, int width) {
        int n = Math.round(Math.max(0f, Math.min(1f, frac)) * width);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < width; i++) sb.append(i < n ? '#' : '.');
        return sb.append(']').toString();
    }

    // ---------- live updates ----------

    private void tickClock() {
        Date now = new Date();
        boolean h24 = Prefs.b(Prefs.CLOCK24, false);
        String batt = batteryText();
        if (Theme.style == Theme.CODE) {
            long up = (SystemClock.elapsedRealtime() - started) / 1000;
            clock.setText(String.format(Locale.US, "drehzahl@%s  %s", Prefs.s(Prefs.INTRO_TEXT, "GLI").toLowerCase(Locale.US).replace(' ', '-'),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)));
            appsHeader.setText("// " + Theme.STYLE_NAMES[Theme.style].toLowerCase(Locale.US) + " theme   up " + String.format(Locale.US, "%02d:%02d:%02d", up / 3600, (up / 60) % 60, up % 60));
            status.setText("bat " + batt + "   src " + VehicleHub.SOURCES[VehicleHub.get(host).source()].toLowerCase(Locale.US)
                    + "\ncarplay " + (CarLink.installed(host) ? "ready (com.carlink.app)" : "not installed"));
        } else {
            clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.US).format(now));
            date.setText(Theme.label(new SimpleDateFormat(MainActivity.dateFormat(), Locale.US).format(now)));
            status.setText("Tablet " + batt + (Theme.style == Theme.SIMPLE ? "" : "   " + VehicleHub.SOURCES[VehicleHub.get(host).source()]));
        }
    }

    private String batteryText() {
        Intent b = host.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "--";
        int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        return Math.round(lvl * 100f / sc) + "%" + (ch ? " charging" : "");
    }

    private void refreshMedia() {
        MediaHub m = MediaHub.get(host);
        if (Theme.style == Theme.CODE) {
            npTitle.setText(m.hasSession() ? "media " + (m.playing() ? "playing" : "paused") + ": " + m.title() + " / " + m.artist()
                    : (m.permitted ? "media idle" : "media: no access (run toolkit install)"));
            return;
        }
        npTitle.setText(m.permitted ? m.title() : "Media info is off");
        npArtist.setText(m.permitted ? (m.hasSession() ? m.artist() + "  ·  " + m.appName() : "Tap to pick a source") : "Run Install in the PC toolkit");
        Bitmap b = m.art();
        if (b != null) { npArt.setImageBitmap(b); npArt.setPadding(0, 0, 0, 0); }
        else { npArt.setImageDrawable(new Icons(Icons.MEDIA, Theme.FAINT)); int p = Theme.dp(24); npArt.setPadding(p, p, p, p); }
        npPlay.setImageDrawable(new Icons(m.playing() ? Icons.PAUSE : Icons.PLAY, Theme.TEXT));
    }

    private void onData(VehicleData d) {
        if (Theme.style == Theme.CODE) {
            spark.push(Units.pressure(d.boostKpa) * (Prefs.i(Prefs.U_PRESS, 0) == 0 ? 1f : 1f));
            if (d.gear != lastGear && lastGear != 0 && !d.live) EventLog.add("trans: shift " + lastGear + " > " + d.gear + " @ " + Math.round(d.rpm) + " rpm");
            lastGear = d.gear;
            if (telemetry.getTag() != null && (Integer) telemetry.getTag() > 0) { telemetry.setTag((Integer) telemetry.getTag() - 1); return; }
            telemetry.setTag(2);
            telemetry.setText(String.format(Locale.US,
                    "rpm       %5.0f        %s\n"
                  + "boost     %5.1f %-4s   %s\n"
                  + "throttle  %5.0f %%      %s\n"
                  + "oil       %5.0f %s\n"
                  + "coolant   %5.0f %s\n"
                  + "iat       %5.0f %s\n"
                  + "power     %5.0f %-4s   torque %4.0f %s\n"
                  + "speed     %5.0f %-4s   gear   %d\n"
                  + "g_lat     %+5.2f       g_lon  %+5.2f",
                    d.rpm, bar(d.rpm / 7000f, 16),
                    Units.pressure(d.boostKpa), Units.pressureUnit(), bar((d.boostKpa + 80f) / 260f, 16),
                    d.throttle * 100f, bar(d.throttle, 16),
                    Units.temp(d.oilC), Units.tempUnit(),
                    Units.temp(d.coolantC), Units.tempUnit(),
                    Units.temp(d.iatC), Units.tempUnit(),
                    Units.power(d.powerKw), Units.powerUnit(), Units.torque(d.torqueNm), Units.torqueUnit(),
                    Units.speed(d.speedKmh), Units.speedUnit(), d.gear,
                    d.latG, d.lonG));
            return;
        }
        for (int i = 0; i < readouts.size(); i++) {
            Metric m = Metric.byId(readoutIds[i]);
            readouts.get(i).setText(Units.fmt(m.value(d), m.decimals()) + " " + m.unit());
        }
    }

    private void refreshLog() {
        if (logView == null) return;
        StringBuilder sb = new StringBuilder();
        for (String s : EventLog.last(9)) sb.append(s).append('\n');
        logView.setText(sb.toString());
    }

    @Override public void onShow() {
        tickClock();
        refreshMedia();
        refreshLog();
        final Runnable tick = new Runnable() { public void run() { tickClock(); h.postDelayed(this, 1000); } };
        h.postDelayed(tick, 1000);
        final MediaHub.Listener ml = new MediaHub.Listener() { public void onMediaChanged() { refreshMedia(); } };
        MediaHub.get(host).addListener(ml);
        final VehicleHub.Listener vl = new VehicleHub.Listener() { public void onData(VehicleData d) { HomeGridScreen.this.onData(d); } };
        VehicleHub.get(host).addListener(vl);
        final EventLog.Listener el = new EventLog.Listener() { public void onEvent() { refreshLog(); } };
        EventLog.addListener(el);
        onLeave(new Runnable() {
            public void run() {
                h.removeCallbacks(tick);
                MediaHub.get(host).removeListener(ml);
                VehicleHub.get(host).removeListener(vl);
                EventLog.removeListener(el);
            }
        });
    }
}
