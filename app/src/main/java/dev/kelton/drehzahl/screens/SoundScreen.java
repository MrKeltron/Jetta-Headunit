package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Locale;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.audio.Presets;
import dev.kelton.drehzahl.audio.SoundEngine;
import dev.kelton.drehzahl.core.CoreService;
import dev.kelton.drehzahl.ui.BalancePad;
import dev.kelton.drehzahl.ui.Controls;
import dev.kelton.drehzahl.ui.EqView;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.SplitScreen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Sound: equalizer, tone, balance/fader, enhancements, volume, and what the audio engine is doing. */
public class SoundScreen extends SplitScreen {
    private static final String[] ITEMS = { "Equalizer", "Treble, mid, bass", "Balance and fader", "Enhancements", "Volume", "Audio engine" };
    private static final int[] ICONS = { Icons.EQ, Icons.TONE, Icons.BALANCE, Icons.SPARK, Icons.VOLUME, Icons.CHIP };
    private static final String[] STAGES = { "Off", "Small room", "Medium room", "Large room", "Medium hall", "Large hall", "Plate" };

    @Override public String title() { return "Sound"; }
    @Override public int section() { return MainActivity.SOUND; }
    @Override protected String[] items() { return ITEMS; }
    @Override protected int icon(int i) { return ICONS[i]; }

    private SoundEngine eng() { return SoundEngine.get(host); }

    private void apply() { eng().applyAll(); }

    private static Controls.Fmt signed() {
        return new Controls.Fmt() { public String f(int v) { return v > 0 ? "+" + v : String.valueOf(v); } };
    }

    @Override protected void page(int i, FrameLayout hostView) {
        Context c = host;
        LinearLayout p = W.page(c, hostView);
        switch (i) {
            case 0: equalizer(c, p); break;
            case 1: tone(c, p); break;
            case 2: balance(c, p); break;
            case 3: enhancements(c, p); break;
            case 4: volume(c, p); break;
            default: engine(c, p);
        }
    }

    private void equalizer(Context c, LinearLayout p) {
        p.addView(W.toggleRow(c, "Equalizer", "Applies to every app Drehzahl is processing (see Audio engine)",
                Prefs.b(Prefs.EQ_ON, true), new Controls.BoolCb() {
                    public void on(boolean v) { Prefs.put(Prefs.EQ_ON, v); apply(); }
                }));
        final EqView[] eqRef = new EqView[1];
        final LinearLayout presets = W.choiceRow(c, "Preset", null, Presets.NAMES, Prefs.i(Prefs.EQ_PRESET, 0), new Controls.IntCb() {
            public void on(int v) {
                Presets.load(v);
                if (eqRef[0] != null) eqRef[0].setGains(Presets.current());
                apply();
            }
        });
        p.addView(presets);
        LinearLayout card = W.card(c);
        EqView eq = new EqView(c, Presets.current(), new EqView.BandCb() {
            public void on(int band, float db) {
                Presets.setBand(band, db);
                ((Controls.Segment) presets.getTag()).set(Presets.NAMES.length - 1);
                apply();
            }
        });
        eqRef[0] = eq;
        card.addView(eq, W.lp(W.MATCH, Theme.dp(360)));
        p.addView(card);
        if (!eng().supported.contains(SoundEngine.DP) && !eng().supported.contains(SoundEngine.EQ)) {
            p.addView(W.note(c, "This tablet reports no equalizer effect, so these settings are saved but cannot be applied."));
        } else if (!eng().supported.contains(SoundEngine.DP)) {
            p.addView(W.note(c, "This tablet only has the basic equalizer, so the 10 bands are blended onto its own bands."));
        }
    }

    private void tone(Context c, LinearLayout p) {
        p.addView(W.note(c, "Each step is 1 dB. These stack on top of the equalizer."));
        p.addView(W.sliderRow(c, "Treble", -9, 9, Prefs.i(Prefs.TREBLE, 0), true, signed(), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.TREBLE, v); apply(); }
        }));
        p.addView(W.sliderRow(c, "Mid", -9, 9, Prefs.i(Prefs.MID, 0), true, signed(), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.MID, v); apply(); }
        }));
        p.addView(W.sliderRow(c, "Bass", -9, 9, Prefs.i(Prefs.BASS, 0), true, signed(), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.BASS, v); apply(); }
        }));
        p.addView(W.button(c, "Reset tone", false, new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.put(Prefs.TREBLE, 0); Prefs.put(Prefs.MID, 0); Prefs.put(Prefs.BASS, 0);
                apply(); refreshPage();
            }
        }), W.lp(W.WRAP, W.WRAP));
    }

    private void balance(Context c, LinearLayout p) {
        LinearLayout row = W.hbox(c);
        row.setGravity(android.view.Gravity.TOP);
        final LinearLayout sliders = W.vbox(c);
        final LinearLayout balRow = W.sliderRow(c, "Balance (left / right)", -9, 9, Prefs.i(Prefs.BALANCE, 0), true, signed(), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.BALANCE, v); apply(); }
        });
        final LinearLayout fadRow = W.sliderRow(c, "Fader (rear / front)", -9, 9, Prefs.i(Prefs.FADER, 0), true, signed(), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.FADER, v); }
        });
        BalancePad pad = new BalancePad(c, Prefs.i(Prefs.BALANCE, 0), Prefs.i(Prefs.FADER, 0), new BalancePad.Cb() {
            public void on(int b, int f) {
                Prefs.put(Prefs.BALANCE, b); Prefs.put(Prefs.FADER, f);
                ((Controls.Slider) balRow.getTag()).set(b);
                ((Controls.Slider) fadRow.getTag()).set(f);
                apply();
            }
        });
        row.addView(pad, new LinearLayout.LayoutParams(Theme.dp(300), Theme.dp(420)));
        sliders.addView(balRow);
        sliders.addView(fadRow);
        sliders.addView(W.choiceRow(c, "Sound focus", "Driver shifts the balance 3 steps toward the driver seat",
                new String[] { "All seats", "Front", "Driver" }, Prefs.i(Prefs.FOCUS, 0), new Controls.IntCb() {
                    public void on(int v) { Prefs.put(Prefs.FOCUS, v); apply(); }
                }));
        sliders.addView(W.button(c, "Center", false, new View.OnClickListener() {
            public void onClick(View v) { Prefs.put(Prefs.BALANCE, 0); Prefs.put(Prefs.FADER, 0); Prefs.put(Prefs.FOCUS, 0); apply(); refreshPage(); }
        }), W.lp(W.WRAP, W.WRAP));
        row.addView(sliders, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        p.addView(row);
        String fader = quadOutput(c)
                ? "A 4-channel output is connected. Fader is stored and will drive it once multi-channel routing is added."
                : "The tablet has a stereo output, so balance works now. Fader and Front focus need a 4-channel USB audio "
                  + "interface feeding front and rear speakers; they are saved and will apply once one is connected.";
        p.addView(W.note(c, fader));
    }

    private void enhancements(Context c, LinearLayout p) {
        SoundEngine e = eng();
        Controls.Fmt tenths = new Controls.Fmt() { public String f(int v) { return v == 0 ? "Off" : String.valueOf(v); } };
        View bb = W.sliderRow(c, "Bass boost", 0, 10, Prefs.i(Prefs.BASS_BOOST, 0), false, tenths, new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.BASS_BOOST, v); apply(); }
        });
        p.addView(bb);
        if (!e.supported.contains(SoundEngine.BASS)) W.setEnabledDeep(bb, false);
        View sur = W.sliderRow(c, "Surround", 0, 10, Prefs.i(Prefs.SURROUND, 0), false, tenths, new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.SURROUND, v); apply(); }
        });
        p.addView(sur);
        if (!e.supported.contains(SoundEngine.VIRT)) W.setEnabledDeep(sur, false);
        View loud = W.sliderRow(c, "Loudness", 0, 10, Prefs.i(Prefs.LOUDNESS, 0), false, new Controls.Fmt() {
            public String f(int v) { return v == 0 ? "Off" : String.format(Locale.US, "+%.1f dB", v * 1.2f); }
        }, new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.LOUDNESS, v); apply(); }
        });
        p.addView(loud);
        if (!e.supported.contains(SoundEngine.LOUD)) W.setEnabledDeep(loud, false);
        View comp = W.toggleRow(c, "Dynamic range compression", "Evens out quiet and loud parts for road noise (3 band compressor)",
                Prefs.b(Prefs.COMPRESS, false), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.COMPRESS, v); apply(); } });
        p.addView(comp);
        View lim = W.toggleRow(c, "Speaker protection limiter", "Stops boosted bass and EQ from clipping above -1.5 dB",
                Prefs.b(Prefs.LIMITER, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.LIMITER, v); apply(); } });
        p.addView(lim);
        if (!e.supported.contains(SoundEngine.DP)) { W.setEnabledDeep(comp, false); W.setEnabledDeep(lim, false); }
        View stage = W.choiceRow(c, "Sound stage", "Reverb that makes the cabin sound bigger", STAGES, Prefs.i(Prefs.STAGE, 0), new Controls.IntCb() {
            public void on(int v) { Prefs.put(Prefs.STAGE, v); apply(); }
        });
        p.addView(stage);
        if (!e.supported.contains(SoundEngine.REVERB)) W.setEnabledDeep(stage, false);
        p.addView(W.note(c, "Greyed out items are not offered by this tablet's audio system."));
    }

    private void volume(Context c, LinearLayout p) {
        final AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        final int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        p.addView(W.sliderRow(c, "Media volume", 0, max, am.getStreamVolume(AudioManager.STREAM_MUSIC), false, new Controls.Fmt() {
            public String f(int v) { return v + " / " + max; }
        }, new Controls.IntCb() { public void on(int v) { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } }));
        p.addView(W.sliderRow(c, "Maximum switch-on volume", 10, 100, Prefs.i(Prefs.START_VOL, 60), false, new Controls.Fmt() {
            public String f(int v) { return v + "%"; }
        }, new Controls.IntCb() { public void on(int v) { Prefs.put(Prefs.START_VOL, v); } }));
        p.addView(W.choiceRow(c, "Speed-dependent volume", "Raises the volume as you go faster. Needs a live speed source (Car > Data source).",
                new String[] { "Off", "Low", "Medium", "High" }, Prefs.i(Prefs.SPEED_VOL, 0), new Controls.IntCb() {
                    public void on(int v) { Prefs.put(Prefs.SPEED_VOL, v); CoreService.refresh(host); }
                }));
        boolean canWrite = android.provider.Settings.System.canWrite(c);
        View click = W.toggleRow(c, "Touch tones", canWrite ? "Click sound when tapping buttons" : "Needs the Install step in the PC toolkit",
                android.provider.Settings.System.getInt(c.getContentResolver(), android.provider.Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1,
                new Controls.BoolCb() {
                    public void on(boolean v) {
                        try { android.provider.Settings.System.putInt(host.getContentResolver(), android.provider.Settings.System.SOUND_EFFECTS_ENABLED, v ? 1 : 0); }
                        catch (RuntimeException e) { W.toast(host, "Not allowed yet. Run Install in the PC toolkit."); }
                    }
                });
        p.addView(click);
        if (!canWrite) W.setEnabledDeep(click, false);
    }

    private void engine(Context c, LinearLayout p) {
        final SoundEngine e = eng();
        p.addView(W.choiceRow(c, "Which audio gets the effects", null,
                new String[] { "Apps that ask", "All players", "Whole tablet" }, Prefs.i(Prefs.FX_MODE, 1), new Controls.IntCb() {
                    public void on(int v) { Prefs.put(Prefs.FX_MODE, v); e.applyMode(); CoreService.refresh(host); refreshPage(); }
                }));
        p.addView(W.note(c, "Apps that ask: only players that announce themselves (Spotify, Pandora, many podcast apps).\n"
                + "All players: also finds apps that do not announce themselves. Needs the DUMP permission from the PC toolkit.\n"
                + "Whole tablet: the old global mode. Works on some tablets, ignored on others."));
        p.addView(W.valueRow(c, "DUMP permission", e.canDump() ? "Granted" : "Not granted"));
        p.addView(W.header(c, "Processing now"));
        List<String> s = e.describeSessions();
        if (s.isEmpty()) p.addView(W.note(c, "No audio sessions yet. Start music in any app and come back."));
        for (String x : s) p.addView(W.valueRow(c, x, "active"));
        p.addView(W.header(c, "Effects this tablet offers"));
        String[] all = { SoundEngine.DP, SoundEngine.EQ, SoundEngine.BASS, SoundEngine.VIRT, SoundEngine.LOUD, SoundEngine.REVERB };
        for (String x : all) p.addView(W.valueRow(c, x, e.supported.contains(x) ? "Yes" : "No"));
        p.addView(W.header(c, "Audio outputs"));
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int[] ch = d.getChannelCounts();
            int best = 2;
            for (int k : ch) best = Math.max(best, k);
            p.addView(W.valueRow(c, outputName(d), best + " ch"));
        }
        final SoundEngine.Listener l = new SoundEngine.Listener() { public void onSessionsChanged() { refreshPage(); } };
        e.addListener(l);
        onPageLeave(new Runnable() { public void run() { e.removeListener(l); } });
    }

    private static String outputName(AudioDeviceInfo d) {
        String n = d.getProductName() == null ? "" : d.getProductName().toString();
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Tablet speaker";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Headphone jack";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth: " + n;
            case AudioDeviceInfo.TYPE_USB_DEVICE: case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB audio: " + n;
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            default: return n.isEmpty() ? "Output type " + d.getType() : n;
        }
    }

    static boolean quadOutput(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() != AudioDeviceInfo.TYPE_USB_DEVICE && d.getType() != AudioDeviceInfo.TYPE_USB_HEADSET) continue;
            for (int k : d.getChannelCounts()) if (k >= 4) return true;
        }
        return false;
    }
}
