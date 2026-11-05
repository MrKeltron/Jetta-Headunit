package dev.kelton.drehzahl.audio;

import dev.kelton.drehzahl.Prefs;

/** EQ presets. Each row is 10 bands in dB: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz. */
public final class Presets {
    public static final String[] NAMES = { "Flat", "Bass", "Warm", "Vocal", "Bright", "Night", "Custom" };
    private static final float[][] GAINS = {
        { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
        { 6, 5, 4, 1.5f, 0, 0, 0, 0, 0.5f, 1 },
        { 3, 3, 2, 1, 0, -0.5f, -1, -1, -1.5f, -2 },
        { -2, -1.5f, -1, 0, 1.5f, 3, 3.5f, 2, 0.5f, 0 },
        { -1, -1, 0, 0, 0, 0.5f, 1.5f, 3, 4, 4.5f },
        { 4, 3, 1.5f, 0, 0, 1, 1.5f, 1, 0, -1 },
    };

    private Presets() { }

    public static float[] current() {
        float[] g = new float[10];
        for (int i = 0; i < 10; i++) g[i] = Prefs.f("eq_b" + i, 0f);
        return g;
    }

    public static void load(int preset) {
        Prefs.put(Prefs.EQ_PRESET, preset);
        if (preset >= GAINS.length) return;
        for (int i = 0; i < 10; i++) Prefs.put("eq_b" + i, GAINS[preset][i]);
    }

    public static void setBand(int band, float db) {
        Prefs.put("eq_b" + band, db);
        Prefs.put(Prefs.EQ_PRESET, NAMES.length - 1);
    }
}
