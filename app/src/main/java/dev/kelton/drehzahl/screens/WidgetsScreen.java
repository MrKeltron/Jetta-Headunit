package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.media.MediaHub;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.ui.GMeterView;
import dev.kelton.drehzahl.ui.GaugeView;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;
import dev.kelton.drehzahl.vehicle.Metric;
import dev.kelton.drehzahl.vehicle.TripComputer;
import dev.kelton.drehzahl.vehicle.Units;
import dev.kelton.drehzahl.vehicle.VehicleData;
import dev.kelton.drehzahl.vehicle.VehicleHub;

/** Six live cards on one screen: clock, now playing, gauges, g-meter, trip, tablet and CarPlay status. Tap a card to open its app. */
public class WidgetsScreen extends Screen {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView clock, date, npTitle, npArtist, trip, tablet, gNow;
    private ImageView npArt, npPlay;
    private final GaugeView[] gauges = new GaugeView[3];
    private GMeterView gm;

    @Override public String title() { return "Widgets"; }
    @Override public int section() { return MainActivity.WIDGETS; }

    @Override protected View build() {
        Context c = host;
        LinearLayout root = W.vbox(c);
        root.setPadding(Theme.dp(20), Theme.dp(12), Theme.dp(20), Theme.dp(20));
        LinearLayout r1 = W.hbox(c), r2 = W.hbox(c);
        r1.setGravity(Gravity.TOP);
        r2.setGravity(Gravity.TOP);

        LinearLayout ck = card(c, "Clock", MainActivity.SETTINGS);
        clock = W.text(c, "", 64, Theme.TEXT, Theme.light());
        date = W.text(c, "", 18, Theme.DIM);
        ck.addView(clock);
        ck.addView(date);
        r1.addView(ck, lp(0));

        LinearLayout np = card(c, "Now playing", MainActivity.MEDIA);
        LinearLayout npRow = W.hbox(c);
        npArt = new ImageView(c);
        npArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        npArt.setBackground(Theme.round(Theme.PANEL2, 10));
        npArt.setClipToOutline(true);
        npRow.addView(npArt, new LinearLayout.LayoutParams(Theme.dp(96), Theme.dp(96)));
        LinearLayout t = W.vbox(c);
        t.setPadding(Theme.dp(14), 0, 0, 0);
        npTitle = W.text(c, "", 19, Theme.TEXT, Theme.medium());
        npTitle.setMaxLines(2);
        npArtist = W.text(c, "", 15, Theme.DIM);
        npArtist.setMaxLines(1);
        npArtist.setPadding(0, Theme.dp(4), 0, Theme.dp(8));
        t.addView(npTitle);
        t.addView(npArtist);
        LinearLayout ctl = W.hbox(c);
        ctl.addView(btn(c, Icons.PREV, new Runnable() { public void run() { MediaHub.get(host).prev(); } }));
        npPlay = btn(c, Icons.PLAY, new Runnable() { public void run() { MediaHub.get(host).playPause(); } });
        ctl.addView(npPlay);
        ctl.addView(btn(c, Icons.NEXT, new Runnable() { public void run() { MediaHub.get(host).next(); } }));
        t.addView(ctl);
        npRow.addView(t, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        np.addView(npRow);
        r1.addView(np, lp(1));

        LinearLayout gc = card(c, "Engine", MainActivity.PERFORMANCE);
        LinearLayout gr = W.hbox(c);
        String[] ids = { "boost", "oil", "coolant" };
        for (int i = 0; i < 3; i++) {
            gauges[i] = new GaugeView(c, Metric.byId(ids[i]), true);
            gr.addView(gauges[i], W.weight(1));
        }
        gc.addView(gr, W.vweight(1));
        r1.addView(gc, lp(2));

        LinearLayout gmc = card(c, "G-meter", MainActivity.PERFORMANCE);
        LinearLayout gmr = W.hbox(c);
        gm = new GMeterView(c);
        gmr.addView(gm, W.weight(1));
        gNow = W.text(c, "", 30, Theme.TEXT, Theme.light());
        gmr.addView(gNow);
        gmc.addView(gmr, W.vweight(1));
        r2.addView(gmc, lp(0));

        LinearLayout tc = card(c, "Since start", MainActivity.CAR);
        trip = W.text(c, "", 20, Theme.TEXT);
        trip.setLineSpacing(0, 1.35f);
        tc.addView(trip);
        r2.addView(tc, lp(1));

        LinearLayout tb = card(c, "Tablet and CarPlay", MainActivity.CARPLAY);
        tablet = W.text(c, "", 18, Theme.TEXT);
        tablet.setLineSpacing(0, 1.35f);
        tb.addView(tablet);
        r2.addView(tb, lp(2));

        root.addView(r1, W.vweight(1));
        root.addView(W.gap(c, 14));
        root.addView(r2, W.vweight(1));
        return root;
    }

    private LinearLayout.LayoutParams lp(int col) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, W.MATCH, 1f);
        p.leftMargin = col == 0 ? 0 : Theme.dp(7);
        p.rightMargin = col == 2 ? 0 : Theme.dp(7);
        return p;
    }

    private LinearLayout card(Context c, String title, final int section) {
        LinearLayout l = W.card(c);
        TextView t = W.text(c, Theme.label(title), 14, Theme.accent, Theme.medium());
        if (Theme.upper) t.setLetterSpacing(0.1f);
        t.setPadding(0, 0, 0, Theme.dp(12));
        l.addView(t);
        l.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { host.go(section); } });
        return l;
    }

    private ImageView btn(Context c, int kind, final Runnable r) {
        ImageView iv = W.icon(c, kind, Theme.TEXT, 22);
        iv.setPadding(Theme.dp(12), Theme.dp(12), Theme.dp(12), Theme.dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46));
        lp.rightMargin = Theme.dp(8);
        iv.setLayoutParams(lp);
        iv.setBackground(Theme.round(Theme.PANEL2, 23));
        iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return iv;
    }

    private void slow() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat(Prefs.b(Prefs.CLOCK24, false) ? "HH:mm" : "h:mm a", Locale.US).format(now));
        date.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now));
        TripComputer.Trip tr = VehicleHub.get(host).trips.trips[0];
        trip.setText(Units.fmt(Units.dist(tr.km), 1) + " " + Units.distUnit() + "\n"
                + Units.fmt(Units.speed(tr.avgSpeedKmh()), 0) + " " + Units.speedUnit() + " average\n"
                + Units.fmt(Units.consumption(tr.litres, tr.km), 1) + " " + Units.consUnit() + "\n"
                + Units.duration((long) tr.seconds) + " driving");
        Intent b = host.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        String batt = "--";
        if (b != null) {
            int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            float temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            batt = Math.round(lvl * 100f / sc) + "%, " + Units.fmt(Units.temp(temp), 0) + Units.tempUnit();
        }
        String cp = CarLink.version(host);
        tablet.setText("Battery " + batt + "\nData: " + VehicleHub.SOURCES[VehicleHub.get(host).source()]
                + "\nCarPlay: " + (cp == null ? "not installed" : "CarLink " + cp));
    }

    private void media() {
        MediaHub m = MediaHub.get(host);
        npTitle.setText(m.permitted ? m.title() : "Media info is off");
        npArtist.setText(m.hasSession() ? m.artist() : "");
        Bitmap b = m.art();
        if (b != null) { npArt.setImageBitmap(b); npArt.setPadding(0, 0, 0, 0); }
        else { npArt.setImageDrawable(new Icons(Icons.MEDIA, Theme.FAINT)); int p = Theme.dp(26); npArt.setPadding(p, p, p, p); }
        npPlay.setImageDrawable(new Icons(m.playing() ? Icons.PAUSE : Icons.PLAY, Theme.TEXT));
    }

    @Override public void onShow() {
        slow();
        media();
        final Runnable tick = new Runnable() { public void run() { slow(); h.postDelayed(this, 1000); } };
        h.postDelayed(tick, 1000);
        final MediaHub.Listener ml = new MediaHub.Listener() { public void onMediaChanged() { media(); } };
        MediaHub.get(host).addListener(ml);
        final VehicleHub.Listener vl = new VehicleHub.Listener() {
            public void onData(VehicleData d) {
                for (GaugeView g : gauges) g.update(d);
                gm.update(d.latG, d.lonG);
                gNow.setText(String.format(Locale.US, "%.2f g", (float) Math.hypot(d.latG, d.lonG)));
            }
        };
        VehicleHub.get(host).addListener(vl);
        onLeave(new Runnable() {
            public void run() {
                h.removeCallbacks(tick);
                MediaHub.get(host).removeListener(ml);
                VehicleHub.get(host).removeListener(vl);
            }
        });
    }
}
