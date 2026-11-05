package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.res.ColorStateList;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.media.MediaHub;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Now playing from whatever app has audio, plus buttons to jump to music sources. */
public class MediaScreen extends Screen {
    private ImageView art, play;
    private TextView title, artist, album, app, pos, dur;
    private ProgressBar bar;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable progress = new Runnable() {
        public void run() { updateProgress(); h.postDelayed(this, 500); }
    };

    @Override public String title() { return "Media"; }
    @Override public int section() { return MainActivity.MEDIA; }

    @Override protected View build() {
        Context c = host;
        LinearLayout root = W.vbox(c);
        root.setPadding(Theme.dp(32), Theme.dp(12), Theme.dp(32), Theme.dp(24));

        LinearLayout top = W.hbox(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(Theme.round(Theme.PANEL, 20));
        art.setClipToOutline(true);
        top.addView(art, new LinearLayout.LayoutParams(Theme.dp(300), Theme.dp(300)));

        LinearLayout info = W.vbox(c);
        info.setPadding(Theme.dp(40), 0, 0, 0);
        app = W.text(c, "", 16, Theme.accent, Theme.medium());
        title = W.text(c, "", 38, Theme.TEXT, Theme.medium());
        title.setMaxLines(2);
        title.setPadding(0, Theme.dp(10), 0, 0);
        artist = W.text(c, "", 24, Theme.DIM);
        artist.setPadding(0, Theme.dp(10), 0, 0);
        album = W.text(c, "", 18, Theme.FAINT);
        album.setPadding(0, Theme.dp(6), 0, Theme.dp(26));
        info.addView(app); info.addView(title); info.addView(artist); info.addView(album);

        bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(Theme.accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.LINE));
        info.addView(bar, new LinearLayout.LayoutParams(W.MATCH, Theme.dp(8)));
        LinearLayout times = W.hbox(c);
        pos = W.text(c, "", 15, Theme.DIM);
        dur = W.text(c, "", 15, Theme.DIM);
        times.addView(pos, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        times.addView(dur);
        times.setPadding(0, Theme.dp(8), 0, Theme.dp(20));
        info.addView(times);

        LinearLayout ctl = W.hbox(c);
        ctl.addView(big(c, Icons.PREV, false, new Runnable() { public void run() { MediaHub.get(host).prev(); } }));
        play = big(c, Icons.PLAY, true, new Runnable() { public void run() { MediaHub.get(host).playPause(); } });
        ctl.addView(play);
        ctl.addView(big(c, Icons.NEXT, false, new Runnable() { public void run() { MediaHub.get(host).next(); } }));
        info.addView(ctl);
        top.addView(info, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        root.addView(top, W.vweight(1));

        root.addView(W.header(c, "Sources"));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout src = W.hbox(c);
        src.addView(source(c, "CarPlay", null, new Runnable() {
            public void run() { if (!CarLink.launch(host)) host.go(MainActivity.CARPLAY); }
        }));
        src.addView(source(c, "Bluetooth", null, new Runnable() {
            public void run() { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        }));
        PackageManager pm = c.getPackageManager();
        Set<String> seen = new HashSet<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC);
        addApps(c, src, pm, pm.queryIntentActivities(q, 0), seen);
        String[] known = { "com.spotify.music", "com.google.android.apps.youtube.music", "com.pandora.android",
                "com.amazon.mp3", "com.apple.android.music", "deezer.android.app", "com.soundcloud.android",
                "au.com.shiftyjelly.pocketcasts", "com.audible.application" };
        for (String k : known) {
            Intent li = pm.getLaunchIntentForPackage(k);
            if (li != null && !seen.contains(k)) {
                List<ResolveInfo> one = pm.queryIntentActivities(li, 0);
                addApps(c, src, pm, one, seen);
            }
        }
        hs.addView(src);
        root.addView(hs, W.lp(W.MATCH, W.WRAP));
        return root;
    }

    private void addApps(Context c, LinearLayout src, PackageManager pm, List<ResolveInfo> list, Set<String> seen) {
        for (final ResolveInfo ri : list) {
            final String pkg = ri.activityInfo.packageName;
            if (seen.contains(pkg)) continue;
            seen.add(pkg);
            src.addView(source(c, ri.loadLabel(pm).toString(), ri, new Runnable() {
                public void run() { open(host.getPackageManager().getLaunchIntentForPackage(pkg)); }
            }));
        }
    }

    private void open(Intent i) {
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { host.startActivity(i); } catch (RuntimeException e) { W.toast(host, "Could not open that app"); }
    }

    private View source(Context c, String label, ResolveInfo ri, final Runnable r) {
        LinearLayout b = W.hbox(c);
        b.setBackground(Theme.round(Theme.PANEL, 14));
        b.setPadding(Theme.dp(16), Theme.dp(12), Theme.dp(22), Theme.dp(12));
        ImageView iv = new ImageView(c);
        if (ri != null) iv.setImageDrawable(ri.loadIcon(c.getPackageManager()));
        else iv.setImageDrawable(new Icons(label.equals("CarPlay") ? Icons.CARPLAY : Icons.BLUETOOTH, Theme.accent));
        b.addView(iv, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        TextView t = W.text(c, label, 18, Theme.TEXT);
        t.setPadding(Theme.dp(12), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = W.lp(W.WRAP, Theme.dp(64));
        lp.rightMargin = Theme.dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private ImageView big(Context c, int kind, boolean primary, final Runnable r) {
        ImageView iv = W.icon(c, kind, primary ? 0xFFFFFFFF : Theme.TEXT, 34);
        int pad = Theme.dp(primary ? 26 : 22);
        iv.setPadding(pad, pad, pad, pad);
        int size = Theme.dp(primary ? 96 : 80);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = Theme.dp(18);
        iv.setLayoutParams(lp);
        iv.setBackground(Theme.round(primary ? Theme.accent : Theme.PANEL2, size / 2f / (Theme.dp(1))));
        iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return iv;
    }

    private void refresh() {
        MediaHub m = MediaHub.get(host);
        app.setText(m.hasSession() ? m.appName().toUpperCase(Locale.US) : (m.permitted ? "NOTHING PLAYING" : "MEDIA INFO IS OFF"));
        title.setText(m.permitted ? m.title() : "Allow media info");
        artist.setText(m.permitted ? m.artist() : "PC toolkit > Install grants it. Or tap here.");
        album.setText(m.album());
        Bitmap b = m.art();
        if (b != null) { art.setImageBitmap(b); art.setPadding(0, 0, 0, 0); }
        else { art.setImageDrawable(new Icons(Icons.MEDIA, Theme.FAINT)); int p = Theme.dp(90); art.setPadding(p, p, p, p); }
        play.setImageDrawable(new Icons(m.playing() ? Icons.PAUSE : Icons.PLAY, 0xFFFFFFFF));
        if (!m.permitted) {
            artist.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            });
        }
        updateProgress();
    }

    private void updateProgress() {
        MediaHub m = MediaHub.get(host);
        long d = m.duration(), p = m.position();
        bar.setProgress(d > 0 ? (int) (1000 * Math.min(p, d) / d) : 0);
        pos.setText(d > 0 ? time(p) : "");
        dur.setText(d > 0 ? time(d) : "");
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    @Override public void onShow() {
        refresh();
        final MediaHub.Listener l = new MediaHub.Listener() { public void onMediaChanged() { refresh(); } };
        MediaHub.get(host).addListener(l);
        h.post(progress);
        onLeave(new Runnable() {
            public void run() { MediaHub.get(host).removeListener(l); h.removeCallbacks(progress); }
        });
    }
}
