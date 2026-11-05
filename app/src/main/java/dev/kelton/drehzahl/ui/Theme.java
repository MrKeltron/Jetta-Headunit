package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import dev.kelton.drehzahl.Prefs;

/** Colors, sizes and fonts. Change the palette here and every screen follows. */
public final class Theme {
    public static final int[] ACCENTS = { 0xFFE3192B, 0xFFE8ECF1, 0xFFA9B4C2, 0xFF2F7BFF, 0xFFFF9F1A, 0xFF2BD67B };
    public static final String[] ACCENT_NAMES = { "Red", "White", "Silver", "Blue", "Amber", "Green" };

    public static int BG, PANEL, PANEL2, LINE, TEXT, DIM, FAINT;
    public static final int WARN = 0xFFFFB020, BAD = 0xFFFF3B3B, GOOD = 0xFF35D07F;
    public static int accent = ACCENTS[0];
    public static boolean night = true;
    private static float density = 1.5f;

    private Theme() { }

    public static void init(Context c, boolean isNight) {
        density = c.getResources().getDisplayMetrics().density;
        night = isNight;
        if (night) {
            BG = 0xFF090B0E; PANEL = 0xFF13161B; PANEL2 = 0xFF1B1F26; LINE = 0xFF272C35;
            TEXT = 0xFFF1F3F6; DIM = 0xFF8C96A4; FAINT = 0xFF4A525E;
        } else {
            BG = 0xFFEEF1F5; PANEL = 0xFFFFFFFF; PANEL2 = 0xFFE6EAF0; LINE = 0xFFD3D9E1;
            TEXT = 0xFF0E1217; DIM = 0xFF586270; FAINT = 0xFFA7B0BC;
        }
        int a = Prefs.i(Prefs.ACCENT, 0);
        int profile = Prefs.i(Prefs.PROFILE, 1);
        if (profile == 0) a = 5;
        if (profile == 2) a = 0;
        accent = ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, a))];
        if (!night && (accent == ACCENTS[1] || accent == ACCENTS[2])) accent = 0xFF3A4452;
    }

    public static int dp(float v) { return Math.round(v * density); }
    public static float dpf(float v) { return v * density; }

    public static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
    }

    public static Typeface regular() { return Typeface.create("sans-serif", Typeface.NORMAL); }
    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    public static Typeface light() { return Typeface.create("sans-serif-light", Typeface.NORMAL); }
    public static Typeface thin() { return Typeface.create("sans-serif-thin", Typeface.NORMAL); }
    public static Typeface condensed() { return Typeface.create("sans-serif-condensed", Typeface.BOLD); }

    public static GradientDrawable round(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dpf(radiusDp));
        return g;
    }

    public static GradientDrawable outline(int fill, int stroke, float radiusDp) {
        GradientDrawable g = round(fill, radiusDp);
        g.setStroke(dp(1), stroke);
        return g;
    }
}
