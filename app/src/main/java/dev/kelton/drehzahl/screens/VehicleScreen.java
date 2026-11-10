package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.core.CoreService;
import dev.kelton.drehzahl.ui.Controls;
import dev.kelton.drehzahl.ui.GMeterView;
import dev.kelton.drehzahl.ui.GaugeView;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.SplitScreen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;
import dev.kelton.drehzahl.vehicle.LapTimer;
import dev.kelton.drehzahl.vehicle.Metric;
import dev.kelton.drehzahl.vehicle.TripComputer;
import dev.kelton.drehzahl.vehicle.Units;
import dev.kelton.drehzahl.vehicle.VehicleData;
import dev.kelton.drehzahl.vehicle.VehicleHub;

/** Car: performance gauges, g-meter, lap timer, trip data, service, driving profile, data source. */
public class VehicleScreen extends SplitScreen {
    private static final String[] ITEMS = { "Performance", "G-meter", "Lap timer", "Driving data", "Service", "Driving profile", "Data source", "Trouble codes", "Pull recorder" };
    private static final int[] ICONS = { Icons.GAUGE, Icons.GMETER, Icons.TIMER, Icons.TRIP, Icons.SERVICE, Icons.PROFILE, Icons.LINK, Icons.DOC, Icons.SPARK };
    private static final String DEFAULT_GAUGES = "boost,oil,coolant,power,latg,iat";

    public static final int[] ALL_PAGES = { 0, 8, 1, 2, 3, 7, 4, 5, 6 };
    public static final int[] PERF_PAGES = { 0, 8, 1, 2 };
    public static final int[] CAR_PAGES = { 3, 7, 4, 5, 6 };
    private final int[] pages;
    private final String title;
    private final int section;

    public VehicleScreen() { this(ALL_PAGES, "Car", MainActivity.CAR); }

    /** Shows only some pages, so the head unit layout can split Performance and Car into two apps. */
    public VehicleScreen(int[] pages, String title, int section) {
        this.pages = pages;
        this.title = title;
        this.section = section;
    }

    @Override public String title() { return title; }
    @Override public int section() { return section; }
    @Override protected String[] items() {
        String[] s = new String[pages.length];
        for (int i = 0; i < pages.length; i++) s[i] = ITEMS[pages[i]];
        return s;
    }
    @Override protected int icon(int i) { return ICONS[pages[i]]; }

    private VehicleHub hub() { return VehicleHub.get(host); }

    private void listen(final VehicleHub.Listener l) {
        hub().addListener(l);
        onPageLeave(new Runnable() { public void run() { hub().removeListener(l); } });
    }

    @Override protected void page(int i, FrameLayout hostView) {
        Context c = host;
        switch (pages[i]) {
            case 0: performance(c, hostView); break;
            case 1: gmeter(c, hostView); break;
            case 2: lapTimer(c, hostView); break;
            case 3: trips(c, W.page(c, hostView)); break;
            case 4: service(c, W.page(c, hostView)); break;
            case 5: profile(c, W.page(c, hostView)); break;
            case 7: codes(c, W.page(c, hostView)); break;
            case 8: pulls(c, W.page(c, hostView)); break;
            default: source(c, W.page(c, hostView));
        }
    }

    private TextView sourceBanner(Context c) {
        String s = hub().source() == 0 ? "DEMO DRIVE: simulated values. Pick a live source in Data source."
                : "LIVE: " + VehicleHub.SOURCES[hub().source()].toUpperCase(Locale.US);
        TextView t = W.text(c, s, 13, hub().source() == 0 ? Theme.WARN : Theme.GOOD, Theme.medium());
        t.setLetterSpacing(0.06f);
        return t;
    }

    // ---- Performance ----
    private void performance(Context c, FrameLayout hostView) {
        LinearLayout root = W.vbox(c);
        root.setPadding(Theme.dp(20), Theme.dp(12), Theme.dp(20), Theme.dp(16));
        LinearLayout top = W.hbox(c);
        top.addView(sourceBanner(c), new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        final String[] ids = Prefs.s(Prefs.GAUGES, DEFAULT_GAUGES).split(",");
        final GaugeView[] g = new GaugeView[6];
        top.addView(W.button(c, "Reset max", false, new View.OnClickListener() {
            public void onClick(View v) { for (GaugeView x : g) x.resetPeak(); }
        }));
        root.addView(top);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = W.hbox(c);
            for (int k = 0; k < 3; k++) {
                final int idx = r * 3 + k;
                g[idx] = new GaugeView(c, Metric.byId(idx < ids.length ? ids[idx] : "boost"), false);
                g[idx].setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { chooseGauge(idx, g); }
                });
                row.addView(g[idx], W.weight(1));
            }
            root.addView(row, W.vweight(1));
        }
        TextView hint = W.text(c, "Tap a gauge to change what it shows", 14, Theme.FAINT);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, W.lp(W.MATCH, W.WRAP));
        hostView.addView(root);
        listen(new VehicleHub.Listener() {
            public void onData(VehicleData d) { for (GaugeView x : g) x.update(d); }
        });
    }

    private void chooseGauge(final int slot, final GaugeView[] g) {
        W.pick(host, "Gauge " + (slot + 1), Metric.labels(), new Controls.IntCb() {
            public void on(int v) {
                g[slot].setMetric(Metric.ALL[v]);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < g.length; i++) { if (i > 0) sb.append(','); sb.append(g[i].metric().id); }
                Prefs.put(Prefs.GAUGES, sb.toString());
            }
        });
    }

    // ---- G-meter ----
    private void gmeter(Context c, FrameLayout hostView) {
        LinearLayout root = W.hbox(c);
        root.setPadding(Theme.dp(20), Theme.dp(12), Theme.dp(20), Theme.dp(16));
        final GMeterView gm = new GMeterView(c);
        root.addView(gm, W.weight(1.3f));
        LinearLayout side = W.vbox(c);
        side.setPadding(Theme.dp(20), 0, 0, 0);
        side.addView(sourceBanner(c));
        final TextView now = W.text(c, "", 40, Theme.TEXT, Theme.light());
        now.setPadding(0, Theme.dp(16), 0, Theme.dp(16));
        side.addView(now);
        final TextView peaks = W.text(c, "", 18, Theme.DIM);
        peaks.setLineSpacing(0, 1.4f);
        side.addView(peaks);
        side.addView(W.gap(c, 20));
        side.addView(W.button(c, "Reset peaks", false, new View.OnClickListener() {
            public void onClick(View v) { gm.resetPeaks(); }
        }), W.lp(W.MATCH, W.WRAP));
        side.addView(W.gap(c, 10));
        TextView cal = W.button(c, "Calibrate mount", true, new View.OnClickListener() {
            public void onClick(View v) {
                W.confirm(host, "Calibrate mount", "Park on level ground with the tablet in its mount, then tap Yes.", new Runnable() {
                    public void run() { hub().motion.calibrate(); gm.resetPeaks(); W.toast(host, "Calibrated"); }
                });
            }
        });
        side.addView(cal, W.lp(W.MATCH, W.WRAP));
        side.addView(W.note(c, hub().motion.hasAccel()
                ? "Uses the tablet's own accelerometer, so this works without any car connection."
                : "This tablet has no accelerometer. Values come from the selected data source."));
        root.addView(side, W.weight(1));
        hostView.addView(root);
        listen(new VehicleHub.Listener() {
            public void onData(VehicleData d) {
                gm.update(d.latG, d.lonG);
                now.setText(String.format(Locale.US, "%.2f g", (float) Math.hypot(d.latG, d.lonG)));
                peaks.setText(String.format(Locale.US, "Braking   %.2f g\nAccel     %.2f g\nLeft turn   %.2f g\nRight turn  %.2f g",
                        gm.peakBrake, gm.peakAcc, gm.peakTurnLeft, gm.peakTurnRight));
            }
        });
    }

    // ---- Lap timer ----
    private void lapTimer(Context c, FrameLayout hostView) {
        final LapTimer t = hub().laps;
        LinearLayout root = W.hbox(c);
        root.setPadding(Theme.dp(28), Theme.dp(20), Theme.dp(28), Theme.dp(20));
        root.setGravity(Gravity.TOP);
        LinearLayout left = W.vbox(c);
        final TextView total = W.text(c, "", 96, Theme.TEXT, Theme.thin());
        final TextView lap = W.text(c, "", 28, Theme.DIM);
        final TextView best = W.text(c, "", 22, Theme.accent, Theme.medium());
        left.addView(W.header(c, "Total"));
        left.addView(total);
        left.addView(W.header(c, "Current lap"));
        left.addView(lap);
        best.setPadding(0, Theme.dp(14), 0, Theme.dp(24));
        left.addView(best);
        LinearLayout btns = W.hbox(c);
        final TextView start = W.button(c, "", true, null);
        start.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { t.startStop(); } });
        btns.addView(start, W.margins(W.lp(Theme.dp(180), W.WRAP), 0, 0, 12, 0));
        btns.addView(W.button(c, "Lap", false, new View.OnClickListener() { public void onClick(View v) { t.lap(); } }),
                W.margins(W.lp(Theme.dp(140), W.WRAP), 0, 0, 12, 0));
        btns.addView(W.button(c, "Reset", false, new View.OnClickListener() { public void onClick(View v) { t.reset(); } }),
                W.lp(Theme.dp(140), W.WRAP));
        left.addView(btns);
        root.addView(left, W.weight(1.3f));
        final LinearLayout list = W.vbox(c);
        list.setPadding(Theme.dp(20), 0, 0, 0);
        root.addView(list, W.weight(1));
        hostView.addView(root);

        final Handler h = new Handler(Looper.getMainLooper());
        final int[] lastCount = { -1 };
        final Runnable tick = new Runnable() {
            public void run() {
                total.setText(LapTimer.fmt(t.total()));
                lap.setText(LapTimer.fmt(t.currentLap()));
                long b = t.best();
                best.setText(b == 0 ? "No laps yet" : "Best lap  " + LapTimer.fmt(b));
                start.setText(t.running() ? "Stop" : (t.total() > 0 ? "Resume" : "Start"));
                if (lastCount[0] != t.laps.size()) {
                    lastCount[0] = t.laps.size();
                    list.removeAllViews();
                    list.addView(W.header(list.getContext(), "Laps"));
                    int n = t.laps.size();
                    for (int i = 0; i < Math.min(n, 8); i++) {
                        long ms = t.laps.get(i);
                        String delta = b == 0 || ms == b ? "best" : String.format(Locale.US, "+%.2f s", (ms - b) / 1000f);
                        list.addView(W.valueRow(list.getContext(), "Lap " + (n - i) + "    " + LapTimer.fmt(ms), delta));
                    }
                }
                h.postDelayed(this, 47);
            }
        };
        h.post(tick);
        onPageLeave(new Runnable() { public void run() { h.removeCallbacks(tick); } });
    }

    // ---- Driving data ----
    private void trips(Context c, LinearLayout p) {
        p.addView(sourceBanner(c));
        LinearLayout row = W.hbox(c);
        row.setGravity(Gravity.TOP);
        final TripComputer tc = hub().trips;
        final TextView[][] cells = new TextView[3][4];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout card = W.card(c);
            card.addView(W.text(c, TripComputer.NAMES[i], 20, Theme.TEXT, Theme.medium()));
            String[] labels = { "Distance", "Average speed", "Consumption", "Driving time" };
            for (int k = 0; k < 4; k++) {
                card.addView(W.header(c, labels[k]));
                cells[i][k] = W.text(c, "", 28, Theme.TEXT, Theme.light());
                card.addView(cells[i][k]);
            }
            if (i > 0) {
                card.addView(W.gap(c, 16));
                card.addView(W.button(c, "Reset", false, new View.OnClickListener() {
                    public void onClick(View v) {
                        W.confirm(host, "Reset " + TripComputer.NAMES[idx], "Clear distance, time and consumption?", new Runnable() {
                            public void run() { tc.trips[idx].reset(); }
                        });
                    }
                }));
            }
            row.addView(card, W.margins(W.weight(1), 0, 8, i < 2 ? 12 : 0, 0));
        }
        p.addView(row, W.lp(W.MATCH, W.WRAP));
        listen(new VehicleHub.Listener() {
            int n;
            public void onData(VehicleData d) {
                if (n++ % 10 != 0) return;
                for (int i = 0; i < 3; i++) {
                    TripComputer.Trip t = tc.trips[i];
                    cells[i][0].setText(Units.fmt(Units.dist(t.km), 1) + " " + Units.distUnit());
                    cells[i][1].setText(Units.fmt(Units.speed(t.avgSpeedKmh()), 0) + " " + Units.speedUnit());
                    cells[i][2].setText(Units.fmt(Units.consumption(t.litres, t.km), 1) + " " + Units.consUnit());
                    cells[i][3].setText(Units.duration((long) t.seconds));
                }
            }
        });
    }

    // ---- Service ----
    private void service(Context c, LinearLayout p) {
        float odo = Prefs.f(Prefs.ODO_KM, 0f);
        float oilAt = Prefs.f(Prefs.OIL_KM, 0f);
        float intKm = Prefs.f(Prefs.OIL_INT_KM, 8047f);
        int intDays = Prefs.i(Prefs.OIL_INT_DAYS, 365);
        long today = System.currentTimeMillis() / 86400000L;
        long oilDay = Prefs.l(Prefs.OIL_DAY, 0);
        LinearLayout card = W.card(c);
        card.addView(W.header(c, "Oil change"));
        if (oilDay == 0) {
            card.addView(W.text(c, "Not set up yet", 30, Theme.TEXT, Theme.light()));
        } else {
            float leftKm = oilAt + intKm - odo;
            long leftDays = oilDay + intDays - today;
            int color = (leftKm < 0 || leftDays < 0) ? Theme.BAD : (leftKm < 800 || leftDays < 21) ? Theme.WARN : Theme.TEXT;
            String txt = leftKm < 0 || leftDays < 0 ? "Overdue" : "Due in " + Units.fmt(Units.dist(leftKm), 0) + " " + Units.distUnit() + " or " + leftDays + " days";
            card.addView(W.text(c, txt, 30, color, Theme.light()));
        }
        p.addView(card, W.margins(W.lp(W.MATCH, W.WRAP), 0, 0, 0, 12));
        p.addView(W.chevronRow(c, "Odometer", Units.fmt(Units.dist(odo), 0) + " " + Units.distUnit() + ". Tap to set it from the cluster.", new View.OnClickListener() {
            public void onClick(View v) {
                W.askNumber(host, "Odometer", Units.distUnit(), Math.round(Units.dist(Prefs.f(Prefs.ODO_KM, 0f))), new W.FloatCb() {
                    public void on(float v) { Prefs.put(Prefs.ODO_KM, Units.distToKm(v)); refreshPage(); }
                });
            }
        }));
        p.addView(W.chevronRow(c, "Oil change done today", "Starts a new interval from the current odometer", new View.OnClickListener() {
            public void onClick(View v) {
                W.confirm(host, "Oil change done", "Record an oil change today at " + Units.fmt(Units.dist(Prefs.f(Prefs.ODO_KM, 0f)), 0) + " " + Units.distUnit() + "?", new Runnable() {
                    public void run() {
                        Prefs.put(Prefs.OIL_KM, Prefs.f(Prefs.ODO_KM, 0f));
                        Prefs.put(Prefs.OIL_DAY, System.currentTimeMillis() / 86400000L);
                        refreshPage();
                    }
                });
            }
        }));
        p.addView(W.chevronRow(c, "Oil interval distance", Units.fmt(Units.dist(intKm), 0) + " " + Units.distUnit(), new View.OnClickListener() {
            public void onClick(View v) {
                W.askNumber(host, "Oil interval", Units.distUnit(), Math.round(Units.dist(Prefs.f(Prefs.OIL_INT_KM, 8047f))), new W.FloatCb() {
                    public void on(float v) { Prefs.put(Prefs.OIL_INT_KM, Units.distToKm(v)); refreshPage(); }
                });
            }
        }));
        p.addView(W.chevronRow(c, "Oil interval time", intDays + " days", new View.OnClickListener() {
            public void onClick(View v) {
                W.askNumber(host, "Oil interval", "days", Prefs.i(Prefs.OIL_INT_DAYS, 365), new W.FloatCb() {
                    public void on(float v) { Prefs.put(Prefs.OIL_INT_DAYS, Math.max(1, Math.round(v))); refreshPage(); }
                });
            }
        }));
        p.addView(W.note(c, "The odometer only counts up by itself when a live speed source is selected. "
                + "Check the dipstick between changes; oil use on turbo engines is normal up to a point."));
    }

    // ---- Driving profile ----
    private void profile(Context c, LinearLayout p) {
        final String[] names = { "Eco", "Normal", "Sport", "Individual" };
        final String[] info = {
                "Green accent. Home shows speed, throttle and coolant.",
                "Your accent color. Home shows your first three Performance gauges.",
                "Red accent. Home shows boost, oil temperature and power.",
                "Same as Normal. Reserved for tuner maps once a tuner link exists."
        };
        int cur = Prefs.i(Prefs.PROFILE, 1);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            View check = i == cur ? W.icon(c, Icons.STAR, Theme.accent, 26) : null;
            p.addView(W.row(c, names[i], info[i], check, new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.put(Prefs.PROFILE, idx);
                    host.rebuild();
                }
            }));
        }
        p.addView(W.note(c, "Profiles change this display only. Changing how the car drives needs the tuner link, which is not built yet."));
    }

    // ---- Data source ----
    private void source(Context c, LinearLayout p) {
        int cur = hub().source();
        String[] info = {
                "Simulated drive with warm-up, gear changes and boost. Good for testing screens at home.",
                "Accelerometer for g-forces, GPS for speed if this tablet has GPS. No engine data.",
                "ELM327 adapter in the OBD2 port, by USB cable, classic Bluetooth or Bluetooth LE. Engine data, trouble codes.",
                "Live data from a flash tuner. Not built yet."
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            View check = i == cur ? W.icon(c, Icons.STAR, Theme.accent, 26) : null;
            LinearLayout row = W.row(c, VehicleHub.SOURCES[i], info[i], check, new View.OnClickListener() {
                public void onClick(View v) { Prefs.put(Prefs.SOURCE, idx); CoreService.refresh(host); refreshPage(); }
            });
            p.addView(row);
            if (i >= 3) W.setEnabledDeep(row, false);
        }
        if (cur == 2) obdSection(c, p);
        p.addView(W.toggleRow(c, "Real g-forces during demo", "Mix the tablet's accelerometer into the demo drive",
                Prefs.b("demo_real_g", true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put("demo_real_g", v); } }));
        p.addView(W.chevronRow(c, "Units", "mph or km/h, °F or °C, psi or bar", new View.OnClickListener() {
            public void onClick(View v) { host.go(MainActivity.SETTINGS, SettingsScreen.UNITS); }
        }));
        p.addView(W.valueRow(c, "Tablet accelerometer", hub().motion.hasAccel() ? "Yes" : "No"));
        p.addView(W.valueRow(c, "Tablet GPS", hub().motion.hasGps() ? "Yes" : "No"));
    }

    // ---- OBD2 adapter (shown under Data source when OBD2 is picked) ----
    private void obdSection(final Context c, LinearLayout p) {
        final dev.kelton.drehzahl.obd.ObdSource obd = dev.kelton.drehzahl.obd.ObdSource.get(host);
        p.addView(W.header(c, "OBD2 adapter"));
        p.addView(W.choiceRow(c, "Connection", null, new String[] { "USB cable", "Bluetooth" }, Prefs.i(Prefs.OBD_LINK, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.OBD_LINK, v); obd.restart(); refreshPage(); }
        }));
        if (Prefs.i(Prefs.OBD_LINK, 0) == 1) {
            String name = Prefs.s(Prefs.OBD_BT_NAME, "");
            p.addView(W.chevronRow(c, "Bluetooth adapter", name.isEmpty() ? "Pick your adapter: paired ones, plus Bluetooth LE ones nearby (no pairing needed)" : name,
                    new View.OnClickListener() { public void onClick(View v) { pickBluetooth(); } }));
        }
        LinearLayout card = W.card(c);
        final TextView st = W.text(c, "", 30, Theme.TEXT, Theme.light());
        final TextView det = W.text(c, "", 15, Theme.DIM);
        det.setLineSpacing(0, 1.35f);
        det.setPadding(0, Theme.dp(8), 0, Theme.dp(14));
        card.addView(st);
        card.addView(det);
        LinearLayout btns = W.hbox(c);
        btns.addView(W.button(c, "Reconnect", true, new View.OnClickListener() { public void onClick(View v) { obd.restart(); } }));
        btns.addView(W.gap(c, 12));
        btns.addView(W.button(c, "Adapter console", false, new View.OnClickListener() { public void onClick(View v) { host.push(new ObdConsoleScreen()); } }));
        card.addView(btns);
        p.addView(card, W.margins(W.lp(W.MATCH, W.WRAP), 0, 0, 0, 8));
        final Runnable upd = new Runnable() {
            public void run() {
                int s = obd.state;
                st.setText(dev.kelton.drehzahl.obd.ObdSource.STATE_NAMES[s]);
                st.setTextColor(s == dev.kelton.drehzahl.obd.ObdSource.LIVE ? Theme.GOOD : s == dev.kelton.drehzahl.obd.ObdSource.ERROR ? Theme.BAD : Theme.TEXT);
                StringBuilder sb = new StringBuilder();
                if (!obd.detail.isEmpty()) sb.append(obd.detail).append('\n');
                if (!obd.adapter.isEmpty()) sb.append("Adapter: ").append(obd.adapter).append('\n');
                if (!obd.linkName.isEmpty()) sb.append("Link: ").append(obd.linkName).append('\n');
                if (!Float.isNaN(obd.voltage)) sb.append(String.format(Locale.US, "Battery: %.1f V%n", obd.voltage));
                if (s == dev.kelton.drehzahl.obd.ObdSource.LIVE) {
                    sb.append("Protocol: ").append(obd.protocol).append('\n');
                    sb.append("VIN: ").append(obd.vin.isEmpty() ? "not reported" : obd.vin).append('\n');
                    sb.append(String.format(Locale.US, "Readings the car answers: %d, updating %.0f per second%n", obd.pidCount, obd.rate));
                    if (!obd.supports(0x5C)) sb.append("Oil temp: this car does not report it over generic OBD2\n");
                    if (!(obd.supports(0x62) && obd.supports(0x63))) sb.append("Power and torque: estimated from airflow\n");
                }
                if (s == dev.kelton.drehzahl.obd.ObdSource.ERROR && !obd.lastError.isEmpty()) sb.append("Last error: ").append(obd.lastError).append('\n');
                det.setText(sb.toString().trim());
            }
        };
        upd.run();
        final dev.kelton.drehzahl.obd.ObdSource.Listener l = new dev.kelton.drehzahl.obd.ObdSource.Listener() { public void onObdStatus() { upd.run(); } };
        obd.addListener(l);
        onPageLeave(new Runnable() { public void run() { obd.removeListener(l); } });
        p.addView(W.toggleRow(c, "Log all adapter traffic", "Writes every command and reply to obd-log.txt. Slows polling a little.",
                Prefs.b(Prefs.OBD_TRAFFIC, false), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.OBD_TRAFFIC, v); obd.restart(); } }));
        p.addView(W.note(c, "USB: plug the adapter into the car, then into the tablet with a USB-C OTG adapter. Android asks once whether "
                + "Drehzahl may use it; tick Always open. Turn the ignition on so the car answers.\n"
                + "Bluetooth: needs a classic Bluetooth (SPP) adapter paired in Android settings first. BLE-only adapters are not supported yet."));
    }

    private void pickBluetooth() {
        BtAdapterPicker.show(host, new BtAdapterPicker.Picked() {
            public void on(String address, String label, boolean le) {
                Prefs.put(Prefs.OBD_BT_ADDR, address);
                Prefs.put(Prefs.OBD_BT_NAME, label);
                Prefs.put(Prefs.OBD_BT_LE, le);
                dev.kelton.drehzahl.obd.ObdSource.get(host).restart();
                refreshPage();
            }
        });
    }


    // ---- Pull recorder ----
    private void pulls(final Context c, LinearLayout p) {
        p.addView(W.note(c, "Records every full-throttle pull from the OBD2 adapter: boost, rpm, pedal, throttle, timing, fuel rail pressure "
                + "and air-fuel ratio, from 5 s before the pedal goes down until you lift. While a pull runs Drehzahl asks for these "
                + "readings as fast as the car answers. Afterwards it looks for the ECU closing the throttle with your foot down (a torque "
                + "cut, felt as a buck), timing pulled for knock, fuel pressure sagging, running lean, and the DSG clutch slipping."));
        p.addView(W.toggleRow(c, "Record full-throttle pulls", "Starts at 80% pedal above 1200 rpm", Prefs.b(Prefs.PULLS, true), new Controls.BoolCb() {
            public void on(boolean x) {
                Prefs.put(Prefs.PULLS, x);
                dev.kelton.drehzahl.obd.ObdSource.get(host).pulls.setEnabled(x);
            }
        }));
        String status = dev.kelton.drehzahl.obd.ObdSource.get(host).pullStatus();
        if (!status.isEmpty()) p.addView(W.note(c, "Last pull: " + status + "."));
        if (hub().source() != 2) p.addView(W.note(c, "Data source is not the OBD2 adapter, so nothing is recorded."));
        java.io.File[] list = dev.kelton.drehzahl.obd.PullStore.list(c);
        p.addView(W.header(c, list.length == 0 ? "No pulls yet" : "Recorded pulls"));
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        for (final java.io.File f : list) {
            java.util.List<String> lines = dev.kelton.drehzahl.obd.PullStore.readable(dev.kelton.drehzahl.obd.PullStore.lines(f));
            String sub = lines.isEmpty() ? "" : lines.get(0) + (lines.size() > 1 ? "\n" + lines.get(1) : "");
            p.addView(W.row(c, df.format(dev.kelton.drehzahl.obd.PullStore.when(f)), sub, null, new View.OnClickListener() {
                public void onClick(View v) { host.push(new PullScreen(f)); }
            }));
        }
    }

    // ---- Trouble codes ----
    private void codes(final Context c, final LinearLayout p) {
        p.addView(W.note(c, "Reads stored, pending and permanent codes from every module that answers generic OBD2. "
                + "On your car that is the engine and the DSG. Needs Data source set to OBD2 adapter and the ignition on."));
        LinearLayout btns = W.hbox(c);
        final LinearLayout res = W.vbox(c);
        btns.addView(W.button(c, "Read codes", true, new View.OnClickListener() {
            public void onClick(View v) {
                res.removeAllViews();
                res.addView(W.note(c, "Reading..."));
                dev.kelton.drehzahl.obd.ObdSource.get(host).submit(new String[] { "03", "07", "0A" }, true, new dev.kelton.drehzahl.obd.ObdSource.Callback() {
                    public void done(String r) { showCodes(c, res, r); }
                });
            }
        }));
        btns.addView(W.gap(c, 12));
        btns.addView(W.button(c, "Clear codes", false, new View.OnClickListener() {
            public void onClick(View v) {
                W.confirm(host, "Clear trouble codes", "This turns off the check engine light and resets the emissions readiness monitors. "
                        + "If the fault is still there the code comes back. Clear with the engine off and the ignition on.", new Runnable() {
                    public void run() {
                        dev.kelton.drehzahl.obd.ObdSource.get(host).submit(new String[] { "04" }, false, new dev.kelton.drehzahl.obd.ObdSource.Callback() {
                            public void done(String r) {
                                res.removeAllViews();
                                res.addView(W.note(c, r.contains("44") ? "Codes cleared." : "The car did not confirm the clear:\n" + r.trim()));
                            }
                        });
                    }
                });
            }
        }));
        p.addView(btns);
        p.addView(res, W.margins(W.lp(W.MATCH, W.WRAP), 0, 14, 0, 0));
    }

    private void showCodes(Context c, LinearLayout res, String out) {
        res.removeAllViews();
        if (out.startsWith("Not connected")) { res.addView(W.note(c, out)); return; }
        String[] kinds = { "Stored", "Pending", "Permanent" };
        int[] modes = { 0x03, 0x07, 0x0A };
        String[] parts = out.split("(?m)^> ");
        int total = 0;
        for (String part : parts) {
            if (part.trim().isEmpty()) continue;
            String cmd = part.substring(0, part.indexOf('\n') < 0 ? part.length() : part.indexOf('\n')).trim();
            String body = part.indexOf('\n') < 0 ? "" : part.substring(part.indexOf('\n') + 1);
            int k = cmd.equals("03") ? 0 : cmd.equals("07") ? 1 : cmd.equals("0A") ? 2 : -1;
            if (k < 0) continue;
            for (String e : dev.kelton.drehzahl.obd.ObdParse.dtcs(body, modes[k])) {
                String[] f = e.split("\\|");
                res.addView(W.row(c, f[0], f[1] + ", " + kinds[k].toLowerCase(Locale.US), null, null));
                total++;
            }
        }
        res.addView(W.note(c, total == 0 ? "No trouble codes." : total + " code" + (total == 1 ? "" : "s")
                + ". Look them up for your engine (EA888) or DSG (DQ250), or check them with VCDS for manufacturer detail."));
    }
}
