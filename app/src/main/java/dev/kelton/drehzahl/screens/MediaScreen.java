package dev.kelton.drehzahl.screens;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRouter2;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.audio.Presets;
import dev.kelton.drehzahl.audio.SoundEngine;
import dev.kelton.drehzahl.media.BtReceiver;
import dev.kelton.drehzahl.media.MediaHub;
import dev.kelton.drehzahl.sys.Root;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.ui.Controls;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.SplitScreen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Media app: now playing with seek and skips, the app's queue, sources, and where the sound goes. */
public class MediaScreen extends SplitScreen {
    private static final String[] ITEMS = { "Now playing", "Up next", "Sources", "Output", "Bluetooth" };
    private static final int[] ICONS = { Icons.PLAY, Icons.QUEUE, Icons.GRID, Icons.OUTPUT, Icons.BLUETOOTH };
    public static final int PAGE_BLUETOOTH = 4;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override public String title() { return "Media"; }
    @Override public int section() { return MainActivity.MEDIA; }
    @Override protected String[] items() { return ITEMS; }
    @Override protected int icon(int i) { return ICONS[i]; }

    private MediaHub hub() { return MediaHub.get(host); }

    @Override protected void page(int i, FrameLayout hostView) {
        Context c = host;
        switch (i) {
            case 0: nowPlaying(c, hostView); break;
            case 1: queue(c, W.page(c, hostView)); break;
            case 2: sources(c, W.page(c, hostView)); break;
            case 3: output(c, W.page(c, hostView)); break;
            default: bluetooth(c, W.page(c, hostView));
        }
    }

    private void listenMedia(final Runnable r) {
        final MediaHub.Listener l = new MediaHub.Listener() { public void onMediaChanged() { r.run(); } };
        hub().addListener(l);
        onPageLeave(new Runnable() { public void run() { hub().removeListener(l); } });
    }

    private void open(Intent i) {
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { host.startActivity(i); } catch (RuntimeException e) { W.toast(host, "Could not open that"); }
    }

    // ---------- Now playing ----------

    private void nowPlaying(Context c, FrameLayout hostView) {
        LinearLayout root = W.hbox(c);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(Theme.dp(36), Theme.dp(16), Theme.dp(36), Theme.dp(20));
        final ImageView art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(Theme.round(Theme.PANEL, 20));
        art.setClipToOutline(true);
        root.addView(art, new LinearLayout.LayoutParams(Theme.dp(330), Theme.dp(330)));

        LinearLayout info = W.vbox(c);
        info.setPadding(Theme.dp(40), 0, 0, 0);
        final TextView app = W.text(c, "", 15, Theme.accent, Theme.medium());
        if (Theme.upper) app.setLetterSpacing(0.1f);
        final TextView title = W.text(c, "", 34, Theme.TEXT, Theme.medium());
        title.setMaxLines(2);
        title.setPadding(0, Theme.dp(8), 0, 0);
        final TextView artist = W.text(c, "", 22, Theme.DIM);
        artist.setMaxLines(1);
        artist.setPadding(0, Theme.dp(8), 0, 0);
        final TextView album = W.text(c, "", 16, Theme.FAINT);
        album.setMaxLines(1);
        album.setPadding(0, Theme.dp(4), 0, Theme.dp(18));
        info.addView(app); info.addView(title); info.addView(artist); info.addView(album);

        final SeekBar seek = new SeekBar(c);
        seek.setMax(1000);
        seek.setProgressTintList(ColorStateList.valueOf(Theme.accent));
        seek.setThumbTintList(ColorStateList.valueOf(Theme.accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.LINE));
        final boolean[] dragging = { false };
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { }
            public void onStartTrackingTouch(SeekBar s) { dragging[0] = true; }
            public void onStopTrackingTouch(SeekBar s) {
                dragging[0] = false;
                long d = hub().duration();
                if (d > 0) hub().seekTo(d * s.getProgress() / 1000);
            }
        });
        info.addView(seek, W.lp(W.MATCH, Theme.dp(40)));
        LinearLayout times = W.hbox(c);
        final TextView pos = W.text(c, "", 15, Theme.DIM), dur = W.text(c, "", 15, Theme.DIM);
        times.addView(pos, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        times.addView(dur);
        times.setPadding(Theme.dp(14), 0, Theme.dp(14), Theme.dp(18));
        info.addView(times);

        LinearLayout ctl = W.hbox(c);
        ctl.addView(round(c, Icons.REWIND, 70, false, new Runnable() { public void run() { hub().jump(-10000); } }));
        ctl.addView(round(c, Icons.PREV, 78, false, new Runnable() { public void run() { hub().prev(); } }));
        final ImageView play = round(c, Icons.PLAY, 96, true, new Runnable() { public void run() { hub().playPause(); } });
        ctl.addView(play);
        ctl.addView(round(c, Icons.NEXT, 78, false, new Runnable() { public void run() { hub().next(); } }));
        ctl.addView(round(c, Icons.FORWARD, 70, false, new Runnable() { public void run() { hub().jump(30000); } }));
        info.addView(ctl);

        LinearLayout bottom = W.hbox(c);
        bottom.setPadding(0, Theme.dp(20), 0, 0);
        bottom.addView(W.button(c, "Open app", false, new View.OnClickListener() {
            public void onClick(View v) { if (hub().isBluetooth()) select(PAGE_BLUETOOTH); else hub().openApp(host); }
        }));
        bottom.addView(W.gap(c, 12));
        bottom.addView(W.button(c, "Output", false, new View.OnClickListener() { public void onClick(View v) { select(3); } }));
        info.addView(bottom);
        root.addView(info, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        hostView.addView(root);

        final Runnable refresh = new Runnable() {
            public void run() {
                MediaHub m = hub();
                app.setText(Theme.label(m.hasSession() ? m.appName() : (m.permitted ? "Nothing playing" : "Media info is off")));
                title.setText(m.permitted ? m.title() : "Run Install in the PC toolkit");
                artist.setText(m.artist());
                album.setText(m.album());
                Bitmap b = m.art();
                if (b != null) { art.setImageBitmap(b); art.setPadding(0, 0, 0, 0); }
                else { art.setImageDrawable(new Icons(Icons.MEDIA, Theme.FAINT)); int p = Theme.dp(100); art.setPadding(p, p, p, p); }
                play.setImageDrawable(new Icons(m.playing() ? Icons.PAUSE : Icons.PLAY, Theme.onAccent()));
            }
        };
        final Runnable progress = new Runnable() {
            public void run() {
                long d = hub().duration(), p = hub().position();
                if (!dragging[0]) seek.setProgress(d > 0 ? (int) (1000 * Math.min(p, d) / d) : 0);
                pos.setText(d > 0 ? time(p) : "");
                dur.setText(d > 0 ? "-" + time(Math.max(0, d - p)) : "");
                h.postDelayed(this, 500);
            }
        };
        refresh.run();
        h.post(progress);
        listenMedia(refresh);
        onPageLeave(new Runnable() { public void run() { h.removeCallbacks(progress); } });
    }

    private ImageView round(Context c, int kind, int sizeDp, boolean primary, final Runnable r) {
        ImageView iv = W.icon(c, kind, primary ? Theme.onAccent() : Theme.TEXT, 30);
        int pad = Theme.dp(sizeDp * 0.29f);
        iv.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(sizeDp), Theme.dp(sizeDp));
        lp.rightMargin = Theme.dp(16);
        iv.setLayoutParams(lp);
        iv.setBackground(Theme.round(primary ? Theme.accent : Theme.PANEL2, sizeDp / 2f));
        iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return iv;
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ---------- Up next ----------

    private void queue(final Context c, final LinearLayout p) {
        final Runnable fill = new Runnable() {
            public void run() {
                p.removeAllViews();
                List<MediaSession.QueueItem> q = hub().queue();
                if (q.isEmpty()) {
                    p.addView(W.note(c, hub().hasSession()
                            ? hub().appName() + " does not share its queue with other apps. Spotify and most podcast apps do; some music apps keep it private."
                            : "Start something playing and its queue shows up here."));
                    return;
                }
                long cur = hub().activeQueueId();
                p.addView(W.header(c, q.size() + " in " + hub().appName()));
                for (final MediaSession.QueueItem it : q) {
                    CharSequence t = it.getDescription().getTitle(), s = it.getDescription().getSubtitle();
                    View mark = it.getQueueId() == cur ? W.icon(c, Icons.PLAY, Theme.accent, 22) : null;
                    p.addView(W.row(c, t == null ? "Untitled" : t.toString(), s == null ? null : s.toString(), mark,
                            new View.OnClickListener() { public void onClick(View v) { hub().skipToQueueItem(it.getQueueId()); } }));
                }
            }
        };
        fill.run();
        listenMedia(fill);
    }

    // ---------- Sources ----------

    private void sources(Context c, LinearLayout p) {
        p.addView(W.chevronRow(c, "CarPlay", CarLink.installed(c) ? "Music from your iPhone through CarLink" : "CarLink is not installed yet",
                new View.OnClickListener() { public void onClick(View v) { if (!CarLink.launch(host)) host.go(MainActivity.CARPLAY); } }));
        p.addView(W.chevronRow(c, "Phone over Bluetooth", "Any phone plays its music to the tablet, like a car stereo", new View.OnClickListener() {
            public void onClick(View v) { select(PAGE_BLUETOOTH); }
        }));
        p.addView(W.header(c, "Music apps on this tablet"));
        PackageManager pm = c.getPackageManager();
        Set<String> seen = new HashSet<>();
        addApps(c, p, pm, pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0), seen);
        String[] known = { "com.spotify.music", "com.google.android.apps.youtube.music", "com.pandora.android", "com.amazon.mp3",
                "com.apple.android.music", "deezer.android.app", "com.soundcloud.android", "au.com.shiftyjelly.pocketcasts",
                "com.audible.application", "com.plexapp.android", "tunein.player", "com.sirius" };
        for (String k : known) {
            Intent li = pm.getLaunchIntentForPackage(k);
            if (li != null && !seen.contains(k)) addApps(c, p, pm, pm.queryIntentActivities(li, 0), seen);
        }
        if (seen.isEmpty()) p.addView(W.note(c, "No music apps found. Install one from the Play Store, or use CarPlay."));
    }

    private void addApps(Context c, LinearLayout p, PackageManager pm, List<ResolveInfo> list, Set<String> seen) {
        for (ResolveInfo ri : list) {
            final String pkg = ri.activityInfo.packageName;
            if (seen.contains(pkg)) continue;
            seen.add(pkg);
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(ri.loadIcon(pm));
            iv.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
            LinearLayout row = W.row(c, ri.loadLabel(pm).toString(), "Open", iv, new View.OnClickListener() {
                public void onClick(View v) { open(host.getPackageManager().getLaunchIntentForPackage(pkg)); }
            });
            p.addView(row);
        }
    }

    // ---------- Output ----------

    private void output(Context c, LinearLayout p) {
        final AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        String now = "Tablet speaker";
        for (AudioDeviceInfo d : outs) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) { now = "Bluetooth: " + d.getProductName(); break; }
            if (t == AudioDeviceInfo.TYPE_USB_DEVICE || t == AudioDeviceInfo.TYPE_USB_HEADSET) now = "USB audio: " + d.getProductName();
            if ((t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET) && now.startsWith("Tablet")) now = "Headphone jack";
        }
        LinearLayout card = W.card(c);
        card.addView(W.text(c, Theme.label("Playing through"), 14, Theme.DIM, Theme.medium()));
        TextView nowT = W.text(c, now, 30, Theme.TEXT, Theme.light());
        nowT.setPadding(0, Theme.dp(6), 0, Theme.dp(14));
        card.addView(nowT);
        LinearLayout btns = W.hbox(c);
        btns.addView(W.button(c, "Switch output", true, new View.OnClickListener() { public void onClick(View v) { switchOutput(); } }));
        btns.addView(W.gap(c, 12));
        btns.addView(W.button(c, "Bluetooth", false, new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        }));
        card.addView(btns);
        p.addView(card, W.margins(W.lp(W.MATCH, W.WRAP), 0, 0, 0, 10));

        final int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        p.addView(W.sliderRow(c, "Volume", 0, max, am.getStreamVolume(AudioManager.STREAM_MUSIC), false, new Controls.Fmt() {
            public String f(int v) { return v + " / " + max; }
        }, new Controls.IntCb() { public void on(int v) { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } }));
        p.addView(W.choiceRow(c, "Equalizer preset", null, Presets.NAMES, Prefs.i(Prefs.EQ_PRESET, 0), new Controls.IntCb() {
            public void on(int v) { Presets.load(v); SoundEngine.get(host).applyAll(); }
        }));
        p.addView(W.sliderRow(c, "Balance", -9, 9, Prefs.i(Prefs.BALANCE, 0), true, new Controls.Fmt() {
            public String f(int v) { return v == 0 ? "Center" : (v < 0 ? "L " + (-v) : "R " + v); }
        }, new Controls.IntCb() { public void on(int v) { Prefs.put(Prefs.BALANCE, v); SoundEngine.get(host).applyAll(); } }));
        p.addView(W.chevronRow(c, "All sound settings", "Equalizer, tone, fader, enhancements, volume", new View.OnClickListener() {
            public void onClick(View v) { host.go(MainActivity.SOUND); }
        }));
        p.addView(W.header(c, "Connected outputs"));
        for (AudioDeviceInfo d : outs) {
            int best = 2;
            for (int k : d.getChannelCounts()) best = Math.max(best, k);
            p.addView(W.valueRow(c, outputName(d), best + " ch"));
        }
    }

    // ---------- Phone over Bluetooth ----------

    private void bluetooth(final Context c, final LinearLayout p) {
        final BtReceiver bt = BtReceiver.get(c);
        final String[] shown = { null };
        final Runnable fill = new Runnable() {
            public void run() {
                String state = btState(bt);
                if (state.equals(shown[0])) return;
                shown[0] = state;
                p.removeAllViews();
                btPage(c, p, bt);
            }
        };
        final Runnable poll = new Runnable() {
            public void run() { fill.run(); h.postDelayed(this, 2500); }
        };
        final BtReceiver.Listener l = new BtReceiver.Listener() { public void onReceiverChanged() { fill.run(); } };
        bt.addListener(l);
        if (!bt.checked()) bt.probe();
        h.post(poll);
        listenMedia(fill);
        onPageLeave(new Runnable() { public void run() { bt.removeListener(l); h.removeCallbacks(poll); } });
    }

    private String btState(BtReceiver bt) {
        MediaHub m = hub();
        return bt.rebootPending() + "|" + bt.bluetoothOn() + "|" + bt.checked() + "|" + bt.available() + "|" + bt.busy() + "|" + bt.connected()
                + "|" + bt.tabletName() + "|" + (m.isBluetooth() ? m.title() + m.playing() : "");
    }

    private void btPage(final Context c, LinearLayout p, final BtReceiver bt) {
        LinearLayout card = W.card(c);
        card.addView(W.text(c, Theme.label("Phone over Bluetooth"), 14, Theme.DIM, Theme.medium()));
        TextView big = W.text(c, "", 30, Theme.TEXT, Theme.light());
        big.setPadding(0, Theme.dp(6), 0, Theme.dp(4));
        TextView sub = W.text(c, "", 16, Theme.DIM);
        sub.setPadding(0, 0, 0, Theme.dp(14));
        card.addView(big);
        card.addView(sub);
        LinearLayout btns = W.hbox(c);
        card.addView(btns);
        p.addView(card, W.margins(W.lp(W.MATCH, W.WRAP), 0, 0, 0, 10));

        if (bt.rebootPending()) {
            big.setText("Restart to finish");
            sub.setText(bt.moduleInstalled()
                    ? "The Bluetooth receiver switches on when the tablet starts up again."
                    : "The Bluetooth receiver is removed when the tablet starts up again.");
            btns.addView(W.button(c, "Restart now", true, new View.OnClickListener() { public void onClick(View v) { bt.reboot(); } }));
            return;
        }
        if (bt.busy()) {
            big.setText("Working on it");
            sub.setText(BtReceiver.usesOverlay() ? "Installing the receiver module. If a Grant popup shows, tap Grant."
                    : "Bluetooth restarts with the new setting. This takes about 10 seconds. If a Grant popup shows, tap Grant.");
            return;
        }
        if (!bt.bluetoothOn()) {
            big.setText("Bluetooth is off");
            sub.setText("Turn it on to let a phone connect.");
            btns.addView(W.button(c, "Turn on Bluetooth", true, new View.OnClickListener() {
                public void onClick(View v) { startPlain(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); }
            }));
            return;
        }
        if (!bt.checked()) {
            big.setText("Checking this tablet");
            sub.setText("Asking Android whether the Bluetooth receiver is switched on.");
            return;
        }
        if (!bt.available()) {
            big.setText("Switched off in Android");
            if (Root.present()) {
                sub.setText(BtReceiver.usesOverlay()
                        ? "This tablet is rooted, so Drehzahl can switch the receiver on. It adds a small root module, and after one restart phones can play music here."
                        : "This tablet is rooted, so Drehzahl can switch the receiver on. It adds a small root module that turns it on at every start, then restarts Bluetooth.");
                btns.addView(W.button(c, "Switch on", true, new View.OnClickListener() {
                    public void onClick(View v) {
                        W.confirm(c, "Switch on the Bluetooth receiver?",
                                BtReceiver.usesOverlay()
                                        ? "Drehzahl asks for root and adds the module \"Drehzahl Bluetooth receiver\". After a restart, phones can play music here. "
                                          + "On Android " + android.os.Build.VERSION.RELEASE + " a tablet can either receive or send Bluetooth music, so while this is on "
                                          + "the tablet cannot play to Bluetooth headphones or speakers. You can switch it off again on this page."
                                        : "Drehzahl asks for root, adds the module \"Drehzahl Bluetooth receiver\" and restarts Bluetooth. "
                                          + "Paired devices stay paired. You can switch it off again on this page.",
                                new Runnable() { public void run() { bt.enable(btDone(c, true)); } });
                    }
                }));
            } else {
                sub.setText("Android ships phones and tablets with the receiver part of Bluetooth turned off, and only a rooted tablet can turn it on. "
                        + "Your iPhone's music still plays here through CarPlay.");
            }
            btns.addView(W.gap(c, 12));
            btns.addView(W.button(c, "Check again", false, new View.OnClickListener() { public void onClick(View v) { bt.reprobe(); } }));
            return;
        }

        List<String> phones = bt.connected();
        if (!phones.isEmpty()) {
            big.setText(phones.get(0));
            MediaHub m = hub();
            if (m.isBluetooth() && m.title().length() > 0) sub.setText((m.playing() ? "Playing: " : "Paused: ") + m.title() + (m.artist().isEmpty() ? "" : " by " + m.artist()));
            else sub.setText("Connected. Start music on the phone.");
            btns.addView(W.button(c, "Now playing", true, new View.OnClickListener() { public void onClick(View v) { select(0); } }));
            btns.addView(W.gap(c, 12));
            btns.addView(W.button(c, "Pause / play", false, new View.OnClickListener() { public void onClick(View v) { hub().playPause(); } }));
        } else {
            big.setText("Ready for a phone");
            sub.setText("Shows up on phones as \"" + bt.tabletName() + "\".");
            btns.addView(W.button(c, "Make visible for 2 minutes", true, new View.OnClickListener() {
                public void onClick(View v) {
                    startPlain(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120));
                }
            }));
        }
        btns.addView(W.gap(c, 12));
        btns.addView(W.button(c, "Bluetooth settings", false, new View.OnClickListener() {
            public void onClick(View v) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        }));

        if (phones.isEmpty()) {
            p.addView(W.header(c, "Pair an iPhone"));
            p.addView(W.note(c, "1. Tap Make visible for 2 minutes on this page.\n"
                    + "2. On the iPhone open Settings > Bluetooth and tap \"" + bt.tabletName() + "\" under Other Devices.\n"
                    + "3. Check that both screens show the same number, then tap Pair on both.\n"
                    + "4. Play music on the iPhone. Next time it connects on its own when Bluetooth is on.\n"
                    + "If the iPhone does not list the tablet, pair from this side: Bluetooth settings > Pair new device, "
                    + "with the iPhone's Bluetooth settings open so it stays visible."));
        }
        p.addView(W.header(c, "Options"));
        p.addView(W.chevronRow(c, "Tablet's Bluetooth name", bt.tabletName(), new View.OnClickListener() {
            public void onClick(View v) {
                W.askText(c, "Bluetooth name", bt.tabletName(), false, new W.TextCb() {
                    public void on(String v) {
                        String n = v.trim();
                        if (n.isEmpty()) return;
                        W.toast(c, bt.rename(n) ? "Renamed. Phones see the new name after they search again." : "Android did not accept that name");
                    }
                });
            }
        }));
        p.addView(W.toggleRow(c, "Start the sound when the phone plays", "If the phone plays and the tablet stays silent, Drehzahl presses play once so Android hands the speaker over",
                Prefs.b(Prefs.BT_NUDGE, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.BT_NUDGE, v); } }));
        if (bt.moduleInstalled()) {
            p.addView(W.chevronRow(c, "Switch the receiver off", "Removes the Drehzahl Bluetooth receiver module and restarts Bluetooth", new View.OnClickListener() {
                public void onClick(View v) {
                    W.confirm(c, "Switch the receiver off?", "Phones can no longer play music to this tablet over Bluetooth."
                                    + (BtReceiver.usesOverlay() ? " This takes effect after a restart." : ""),
                            new Runnable() { public void run() { bt.disable(btDone(c, false)); } });
                }
            }));
        }
    }

    private BtReceiver.Done btDone(final Context c, final boolean on) {
        return new BtReceiver.Done() {
            public void on(boolean ok, String output) {
                if (!ok) W.toast(c, output.contains("denied") || output.contains("not allowed") ? "Root was not granted" : "That did not work. A restart of the tablet may finish it.");
                else W.toast(c, on ? "Bluetooth receiver is on" : "Bluetooth receiver is off");
            }
        };
    }

    private void startPlain(Intent i) {
        try { host.startActivity(i); } catch (RuntimeException e) { W.toast(host, "Android blocked that request"); }
    }

    /** Android 14+ system output picker, else the volume panel, else Bluetooth settings. */
    private void switchOutput() {
        if (Build.VERSION.SDK_INT >= 34) {
            try { if (MediaRouter2.getInstance(host).showSystemOutputSwitcher()) return; } catch (RuntimeException ignored) { }
        }
        try { open(new Intent(Settings.Panel.ACTION_VOLUME)); }
        catch (RuntimeException e) { open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    }

    private static String outputName(AudioDeviceInfo d) {
        String n = d.getProductName() == null ? "" : d.getProductName().toString();
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Tablet speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "Earpiece";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Headphone jack";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth: " + n;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth calls: " + n;
            case AudioDeviceInfo.TYPE_USB_DEVICE: case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB audio: " + n;
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_TELEPHONY: return "Telephony";
            default: return n.isEmpty() ? "Output type " + d.getType() : n;
        }
    }
}
