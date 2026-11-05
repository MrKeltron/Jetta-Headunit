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
    private static final String[] ITEMS = { "Performance", "G-meter", "Lap timer", "Driving data", "Service", "Driving profile", "Data source" };
    private static final int[] ICONS = { Icons.GAUGE, Icons.GMETER, Icons.TIMER, Icons.TRIP, Icons.SERVICE, Icons.PROFILE, Icons.LINK };
    private static final String DEFAULT_GAUGES = "boost,oil,coolant,power,latg,iat";

    @Override public String title() { return "Car"; }
    @Override public int section() { return MainActivity.CAR; }
    @Override protected String[] items() { return ITEMS; }
    @Override protected int icon(int i) { return ICONS[i]; }

    private VehicleHub hub() { return VehicleHub.get(host); }

    private void listen(final VehicleHub.Listener l) {
        hub().addListener(l);
        onPageLeave(new Runnable() { public void run() { hub().removeListener(l); } });
    }

    @Override protected void page(int i, FrameLayout hostView) {
        Context c = host;
        switch (i) {
            case 0: performance(c, hostView); break;
            case 1: gmeter(c, hostView); break;
            case 2: lapTimer(c, hostView); break;
            case 3: trips(c, W.page(c, hostView)); break;
            case 4: service(c, W.page(c, hostView)); break;
            case 5: profile(c, W.page(c, hostView)); break;
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
                "ELM327 style adapter in the OBD2 port. Not built yet.",
                "Live data from a flash tuner. Not built yet."
        };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            View check = i == cur ? W.icon(c, Icons.STAR, Theme.accent, 26) : null;
            LinearLayout row = W.row(c, VehicleHub.SOURCES[i], info[i], check, new View.OnClickListener() {
                public void onClick(View v) { Prefs.put(Prefs.SOURCE, idx); CoreService.refresh(host); refreshPage(); }
            });
            p.addView(row);
            if (i >= 2) W.setEnabledDeep(row, false);
        }
        p.addView(W.toggleRow(c, "Real g-forces during demo", "Mix the tablet's accelerometer into the demo drive",
                Prefs.b("demo_real_g", true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put("demo_real_g", v); } }));
        p.addView(W.chevronRow(c, "Units", "mph or km/h, °F or °C, psi or bar", new View.OnClickListener() {
            public void onClick(View v) { host.go(MainActivity.SETTINGS, SettingsScreen.UNITS); }
        }));
        p.addView(W.valueRow(c, "Tablet accelerometer", hub().motion.hasAccel() ? "Yes" : "No"));
        p.addView(W.valueRow(c, "Tablet GPS", hub().motion.hasGps() ? "Yes" : "No"));
    }
}
