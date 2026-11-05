package dev.kelton.drehzahl.audio;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.kelton.drehzahl.Prefs;

/**
 * Applies the Sound settings to every audio session it is told about.
 * Main engine: Android DynamicsProcessing (10-band EQ + per-channel gain + compressor + limiter).
 * Extras when the tablet supports them: BassBoost, Virtualizer, LoudnessEnhancer, PresetReverb.
 */
public final class SoundEngine {
    public interface Listener { void onSessionsChanged(); }

    public static final int[] FREQS = { 31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000 };
    private static final float[] CUTOFF = { 44, 88, 177, 354, 707, 1414, 2828, 5657, 11314, 20000 };
    private static final float[] W_BASS = { 1f, 1f, 0.8f, 0.4f, 0.1f, 0, 0, 0, 0, 0 };
    private static final float[] W_MID = { 0, 0, 0, 0.2f, 0.7f, 1f, 0.7f, 0.2f, 0, 0 };
    private static final float[] W_TREBLE = { 0, 0, 0, 0, 0, 0, 0.1f, 0.5f, 1f, 1f };
    private static final int PRIORITY = 1000;

    public static final String DP = "Dynamics processing (EQ, balance, compressor, limiter)";
    public static final String EQ = "Basic equalizer";
    public static final String BASS = "Bass boost";
    public static final String VIRT = "Surround (virtualizer)";
    public static final String LOUD = "Loudness enhancer";
    public static final String REVERB = "Reverb (sound stage)";

    private static SoundEngine inst;
    private final Context app;
    public final Set<String> supported = new HashSet<>();
    private final Map<Integer, Chain> chains = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastSpeedOffset;

    private static final class Chain {
        final int session;
        final String owner;
        final boolean fromDump;
        int missed;
        DynamicsProcessing dp;
        Equalizer eq;
        BassBoost bass;
        Virtualizer virt;
        LoudnessEnhancer loud;
        PresetReverb reverb;

        Chain(int session, String owner, boolean fromDump) { this.session = session; this.owner = owner; this.fromDump = fromDump; }

        void release() {
            AudioEffect[] all = { dp, eq, bass, virt, loud, reverb };
            for (AudioEffect e : all) {
                if (e == null) continue;
                try { e.setEnabled(false); e.release(); } catch (RuntimeException ignored) { }
            }
        }
    }

    public static synchronized SoundEngine get(Context c) {
        if (inst == null) inst = new SoundEngine(c.getApplicationContext());
        return inst;
    }

    private SoundEngine(Context c) {
        app = c;
        try {
            for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
                if (AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING.equals(d.type)) supported.add(DP);
                if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) supported.add(EQ);
                if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) supported.add(BASS);
                if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) supported.add(VIRT);
                if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) supported.add(LOUD);
                if (AudioEffect.EFFECT_TYPE_PRESET_REVERB.equals(d.type)) supported.add(REVERB);
            }
        } catch (RuntimeException ignored) { }
    }

    public boolean canDump() {
        return app.checkSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED;
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        main.post(new Runnable() {
            @Override public void run() { for (Listener l : new ArrayList<>(listeners)) l.onSessionsChanged(); }
        });
    }

    /** Sessions currently being processed, as "owner (session id)". */
    public synchronized List<String> describeSessions() {
        List<String> out = new ArrayList<>();
        for (Chain c : chains.values()) out.add((c.session == 0 ? "Whole tablet" : c.owner) + "  (session " + c.session + ")");
        return out;
    }

    public synchronized void open(int session, String owner, boolean fromDump) {
        if (chains.containsKey(session)) {
            chains.get(session).missed = 0;
            return;
        }
        Chain c = new Chain(session, owner == null ? "unknown app" : owner, fromDump);
        create(c);
        chains.put(session, c);
        apply(c);
        dev.kelton.drehzahl.core.EventLog.add("audio: effects on " + c.owner + " (session " + session + ")");
        changed();
    }

    public synchronized void close(int session) {
        Chain c = chains.remove(session);
        if (c != null) { c.release(); dev.kelton.drehzahl.core.EventLog.add("audio: released session " + session); changed(); }
    }

    public synchronized void closeAll() {
        for (Chain c : chains.values()) c.release();
        chains.clear();
        changed();
    }

    /** Called by the dumpsys watcher with every playing session it found this round. */
    public synchronized void dumpRound(Map<Integer, String> seen) {
        for (Map.Entry<Integer, String> e : seen.entrySet()) open(e.getKey(), e.getValue(), true);
        List<Integer> gone = new ArrayList<>();
        for (Chain c : chains.values()) {
            if (!c.fromDump || seen.containsKey(c.session)) continue;
            if (++c.missed >= 3) gone.add(c.session);
        }
        for (Integer s : gone) close(s);
    }

    /** Mode changed in Settings: 2 = whole tablet (session 0), anything else drops session 0. */
    public synchronized void applyMode() {
        int mode = Prefs.i(Prefs.FX_MODE, 1);
        if (mode == 2) open(0, "Whole tablet", false);
        else close(0);
        if (mode == 0) {
            List<Integer> dumps = new ArrayList<>();
            for (Chain c : chains.values()) if (c.fromDump) dumps.add(c.session);
            for (Integer s : dumps) close(s);
        }
    }

    private void create(Chain c) {
        int s = c.session;
        if (supported.contains(DP)) {
            try {
                DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                        true, 10, true, 3, false, 0, true).setPreferredFrameDuration(10f).build();
                cfg.setPreEqAllChannelsTo(buildEq());
                cfg.setMbcAllChannelsTo(buildMbc());
                cfg.setLimiterAllChannelsTo(buildLimiter());
                c.dp = new DynamicsProcessing(PRIORITY, s, cfg);
            } catch (Throwable t) { c.dp = null; }
        }
        if (c.dp == null && supported.contains(EQ)) {
            try { c.eq = new Equalizer(PRIORITY, s); } catch (Throwable t) { c.eq = null; }
        }
        if (supported.contains(BASS)) { try { c.bass = new BassBoost(PRIORITY, s); } catch (Throwable t) { c.bass = null; } }
        if (supported.contains(VIRT)) { try { c.virt = new Virtualizer(PRIORITY, s); } catch (Throwable t) { c.virt = null; } }
        if (supported.contains(LOUD)) { try { c.loud = new LoudnessEnhancer(s); } catch (Throwable t) { c.loud = null; } }
        if (supported.contains(REVERB) && s != 0) { try { c.reverb = new PresetReverb(PRIORITY, s); } catch (Throwable t) { c.reverb = null; } }
    }

    /** Push the current settings to every session. Call after any Sound setting changes. */
    public synchronized void applyAll() {
        for (Chain c : chains.values()) apply(c);
    }

    private void apply(Chain c) {
        float[] g = bandGains();
        float[] lr = channelGains();
        try {
            if (c.dp != null) {
                for (int i = 0; i < 10; i++) c.dp.setPreEqBandAllChannelsTo(i, new DynamicsProcessing.EqBand(true, CUTOFF[i], g[i]));
                c.dp.setInputGainbyChannel(0, lr[0]);
                c.dp.setInputGainbyChannel(1, lr[1]);
                c.dp.setMbcAllChannelsTo(buildMbc());
                c.dp.setLimiterAllChannelsTo(buildLimiter());
                c.dp.setEnabled(true);
            }
            if (c.eq != null) {
                short bands = c.eq.getNumberOfBands();
                short[] range = c.eq.getBandLevelRange();
                for (short b = 0; b < bands; b++) {
                    float hz = c.eq.getCenterFreq(b) / 1000f;
                    int mb = Math.round(gainAt(g, hz) * 100f);
                    c.eq.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], mb)));
                }
                c.eq.setEnabled(true);
            }
            if (c.bass != null) {
                int v = Prefs.i(Prefs.BASS_BOOST, 0);
                if (c.bass.getStrengthSupported()) c.bass.setStrength((short) (v * 100));
                c.bass.setEnabled(v > 0);
            }
            if (c.virt != null) {
                int v = Prefs.i(Prefs.SURROUND, 0);
                if (c.virt.getStrengthSupported()) c.virt.setStrength((short) (v * 100));
                c.virt.setEnabled(v > 0);
            }
            if (c.loud != null) {
                int v = Prefs.i(Prefs.LOUDNESS, 0);
                c.loud.setTargetGain(v * 120);
                c.loud.setEnabled(v > 0);
            }
            if (c.reverb != null) {
                int v = Prefs.i(Prefs.STAGE, 0);
                c.reverb.setPreset((short) v);
                c.reverb.setEnabled(v > 0);
            }
        } catch (RuntimeException ignored) { }
    }

    private DynamicsProcessing.Eq buildEq() {
        float[] g = bandGains();
        DynamicsProcessing.Eq eq = new DynamicsProcessing.Eq(true, true, 10);
        for (int i = 0; i < 10; i++) eq.setBand(i, new DynamicsProcessing.EqBand(true, CUTOFF[i], g[i]));
        return eq;
    }

    private DynamicsProcessing.Mbc buildMbc() {
        boolean on = Prefs.b(Prefs.COMPRESS, false);
        DynamicsProcessing.Mbc m = new DynamicsProcessing.Mbc(true, on, 3);
        float[] cut = { 250f, 4000f, 20000f };
        for (int i = 0; i < 3; i++) {
            m.setBand(i, new DynamicsProcessing.MbcBand(on, cut[i], 10f, 150f, 3f, -26f, 6f, -90f, 1f, 0f, 5f));
        }
        return m;
    }

    private DynamicsProcessing.Limiter buildLimiter() {
        boolean on = Prefs.b(Prefs.LIMITER, true);
        return new DynamicsProcessing.Limiter(true, on, 0, 1f, 60f, 10f, -1.5f, 0f);
    }

    /** Final gain per band = 10-band EQ + bass/mid/treble spread over the bands it covers. */
    public static float[] bandGains() {
        float[] g = new float[10];
        boolean eqOn = Prefs.b(Prefs.EQ_ON, true);
        int bass = Prefs.i(Prefs.BASS, 0), mid = Prefs.i(Prefs.MID, 0), treble = Prefs.i(Prefs.TREBLE, 0);
        for (int i = 0; i < 10; i++) {
            float v = eqOn ? Prefs.f("eq_b" + i, 0f) : 0f;
            v += bass * W_BASS[i] + mid * W_MID[i] + treble * W_TREBLE[i];
            g[i] = Math.max(-18f, Math.min(18f, v));
        }
        return g;
    }

    /** Left and right gain in dB from balance and sound focus. Each step is 3 dB; step 9 is nearly silent. */
    public static float[] channelGains() {
        int bal = Prefs.i(Prefs.BALANCE, 0);
        if (Prefs.i(Prefs.FOCUS, 0) == 2) bal = Math.max(-9, bal - 3);
        float left = bal > 0 ? -stepDb(bal) : 0f;
        float right = bal < 0 ? -stepDb(-bal) : 0f;
        return new float[] { left, right };
    }

    private static float stepDb(int steps) { return steps >= 9 ? 60f : steps * 3f; }

    private static float gainAt(float[] g, float hz) {
        if (hz <= FREQS[0]) return g[0];
        for (int i = 1; i < 10; i++) {
            if (hz <= FREQS[i]) {
                double a = Math.log(hz / FREQS[i - 1]) / Math.log(FREQS[i] / (double) FREQS[i - 1]);
                return (float) (g[i - 1] + (g[i] - g[i - 1]) * a);
            }
        }
        return g[9];
    }

    // ---- Volume helpers ----

    /** Cap the music volume when the app starts (like a factory "max switch-on volume"). */
    public void applyStartupVolume() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cap = Math.round(max * Prefs.i(Prefs.START_VOL, 60) / 100f);
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
    }

    /** Speed-dependent volume: adds steps as speed rises, keeps any change you make by hand. */
    public void speedTick(float kmh) {
        int level = Prefs.i(Prefs.SPEED_VOL, 0);
        int offset = level == 0 ? 0 : Math.min(6, Math.round(kmh / (70f / level)));
        if (offset == lastSpeedOffset) return;
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int next = Math.max(0, Math.min(max, cur - lastSpeedOffset + offset));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
        lastSpeedOffset = offset;
    }
}
