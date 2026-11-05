package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.graphics.Bitmap;
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
import dev.kelton.drehzahl.ui.GaugeView;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;
import dev.kelton.drehzahl.vehicle.Metric;
import dev.kelton.drehzahl.vehicle.VehicleData;
import dev.kelton.drehzahl.vehicle.VehicleHub;

/** Home: time, now playing, CarPlay shortcut and three live gauges. */
public class HomeScreen extends Screen {
    private TextView bigClock, bigDate, title, artist, appName, cpStatus, sourceTag;
    private ImageView art, playBtn;
    private final GaugeView[] gauges = new GaugeView[3];

    @Override public String title() { return "Home"; }
    @Override public int section() { return MainActivity.HOME; }

    static String[] homeGauges() {
        int profile = Prefs.i(Prefs.PROFILE, 1);
        if (profile == 0) return new String[] { "speed", "throttle", "coolant" };
        if (profile == 2) return new String[] { "boost", "oil", "power" };
        String saved = Prefs.s(Prefs.GAUGES, "boost,oil,coolant,power,latg,iat");
        String[] all = saved.split(",");
        return new String[] { all[0], all.length > 1 ? all[1] : "oil", all.length > 2 ? all[2] : "coolant" };
    }

    @Override protected View build() {
        Context c = host;
        LinearLayout root = W.hbox(c);
        root.setGravity(Gravity.TOP);
        root.setPadding(Theme.dp(24), Theme.dp(8), Theme.dp(24), Theme.dp(24));

        LinearLayout left = W.vbox(c);
        LinearLayout clockCard = W.card(c);
        bigClock = W.text(c, "", 88, Theme.TEXT, Theme.thin());
        bigDate = W.text(c, "", 22, Theme.DIM);
        bigDate.setPadding(Theme.dp(4), Theme.dp(6), 0, 0);
        clockCard.addView(bigClock);
        clockCard.addView(bigDate);
        left.addView(clockCard, W.margins(W.lp(W.MATCH, W.WRAP), 0, 0, 0, 16));

        LinearLayout media = W.card(c);
        media.setOrientation(LinearLayout.HORIZONTAL);
        media.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(Theme.round(Theme.PANEL2, 14));
        art.setClipToOutline(true);
        media.addView(art, new LinearLayout.LayoutParams(Theme.dp(150), Theme.dp(150)));
        LinearLayout info = W.vbox(c);
        info.setPadding(Theme.dp(22), 0, 0, 0);
        appName = W.text(c, "", 14, Theme.accent, Theme.medium());
        title = W.text(c, "", 26, Theme.TEXT, Theme.medium());
        title.setMaxLines(2);
        artist = W.text(c, "", 18, Theme.DIM);
        artist.setPadding(0, Theme.dp(6), 0, Theme.dp(14));
        artist.setMaxLines(1);
        info.addView(appName);
        info.addView(title);
        info.addView(artist);
        LinearLayout controls = W.hbox(c);
        controls.addView(control(c, Icons.PREV, new Runnable() { public void run() { MediaHub.get(host).prev(); } }));
        playBtn = control(c, Icons.PLAY, new Runnable() { public void run() { MediaHub.get(host).playPause(); } });
        controls.addView(playBtn);
        controls.addView(control(c, Icons.NEXT, new Runnable() { public void run() { MediaHub.get(host).next(); } }));
        info.addView(controls);
        media.addView(info, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        media.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.go(MainActivity.MEDIA); }
        });
        left.addView(media, W.vweight(1));
        root.addView(left, W.margins(W.weight(1.25f), 0, 0, 16, 0));

        LinearLayout right = W.vbox(c);
        LinearLayout cp = W.card(c);
        LinearLayout cpTop = W.hbox(c);
        cpTop.addView(W.icon(c, Icons.CARPLAY, Theme.accent, 40));
        LinearLayout cpText = W.vbox(c);
        cpText.setPadding(Theme.dp(16), 0, 0, 0);
        cpText.addView(W.text(c, "CarPlay", 24, Theme.TEXT, Theme.medium()));
        cpStatus = W.text(c, "", 15, Theme.DIM);
        cpStatus.setPadding(0, Theme.dp(4), 0, 0);
        cpText.addView(cpStatus);
        cpTop.addView(cpText, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        cp.addView(cpTop);
        cp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!CarLink.launch(host)) host.go(MainActivity.CARPLAY);
            }
        });
        right.addView(cp, W.margins(W.lp(W.MATCH, W.WRAP), 0, 0, 0, 16));

        LinearLayout car = W.card(c);
        LinearLayout carTop = W.hbox(c);
        carTop.addView(W.text(c, "Vehicle", 20, Theme.TEXT, Theme.medium()), new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        sourceTag = W.text(c, "", 13, Theme.DIM, Theme.medium());
        carTop.addView(sourceTag);
        car.addView(carTop);
        LinearLayout row = W.hbox(c);
        String[] ids = homeGauges();
        for (int i = 0; i < 3; i++) {
            gauges[i] = new GaugeView(c, Metric.byId(ids[i]), true);
            row.addView(gauges[i], W.weight(1));
        }
        car.addView(row, W.vweight(1));
        car.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { host.go(MainActivity.CAR, 0); }
        });
        right.addView(car, W.vweight(1));
        root.addView(right, W.weight(1));
        return root;
    }

    private ImageView control(Context c, int kind, final Runnable r) {
        ImageView iv = W.icon(c, kind, Theme.TEXT, 30);
        iv.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(64), Theme.dp(64)));
        iv.setBackground(Theme.round(Theme.PANEL2, 32));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
        lp.rightMargin = Theme.dp(12);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return iv;
    }

    private void refreshMedia() {
        MediaHub m = MediaHub.get(host);
        if (!m.permitted) {
            appName.setText("MEDIA");
            title.setText("Media info is off");
            artist.setText("Run Install in the PC toolkit to allow it");
        } else {
            appName.setText(m.hasSession() ? m.appName().toUpperCase(Locale.US) : "MEDIA");
            title.setText(m.title());
            artist.setText(m.artist());
        }
        Bitmap b = m.art();
        if (b != null) { art.setImageBitmap(b); art.setPadding(0, 0, 0, 0); }
        else {
            art.setImageDrawable(new Icons(Icons.MEDIA, Theme.FAINT));
            int p = Theme.dp(42);
            art.setPadding(p, p, p, p);
        }
        playBtn.setImageDrawable(new Icons(m.playing() ? Icons.PAUSE : Icons.PLAY, Theme.TEXT));
    }

    private void refreshStatic() {
        Date now = new Date();
        boolean h24 = Prefs.b(Prefs.CLOCK24, false);
        bigClock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.US).format(now));
        bigDate.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now));
        if (!CarLink.installed(host)) cpStatus.setText("CarLink not installed yet. Use Install in the PC toolkit.");
        else cpStatus.setText("Tap to open. Plug in your iPhone or connect wirelessly.");
        sourceTag.setText(VehicleHub.SOURCES[VehicleHub.get(host).source()].toUpperCase(Locale.US));
    }

    @Override public void onShow() {
        refreshStatic();
        refreshMedia();
        final MediaHub.Listener ml = new MediaHub.Listener() { public void onMediaChanged() { refreshMedia(); } };
        MediaHub.get(host).addListener(ml);
        final VehicleHub.Listener vl = new VehicleHub.Listener() {
            int n;
            public void onData(VehicleData d) {
                for (GaugeView g : gauges) g.update(d);
                if (++n % 20 == 0) refreshStatic();
            }
        };
        VehicleHub.get(host).addListener(vl);
        onLeave(new Runnable() {
            public void run() {
                MediaHub.get(host).removeListener(ml);
                VehicleHub.get(host).removeListener(vl);
            }
        });
    }
}
