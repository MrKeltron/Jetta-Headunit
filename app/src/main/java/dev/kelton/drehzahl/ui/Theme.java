package dev.kelton.drehzahl.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dev.kelton.drehzahl.Prefs;

/**
 * Themes: colors, fonts, corner shape and text style. Every screen reads from here,
 * so a new theme is one more case in init() plus a backdrop and tile style if wanted.
 */
public final class Theme {
    public static final int GLI = 0, CARBON = 1, SIMPLE = 2, CODE = 3, CLASSIC = 4;
    public static final String[] STYLE_NAMES = { "GLI", "Carbon", "Simple", "Code", "Classic" };
    public static final String[] STYLE_INFO = {
        "Red on black, hex pattern, square buttons, Saira font. Startup: tach sweep and red stripe.",
        "Carbon weave, round metal buttons, amber accents, Rajdhani font.",
        "Black and white, big text, fewer details. Inter font.",
        "Terminal look with live telemetry, event log and a boost graph. JetBrains Mono font.",
        "The first Drehzahl layout with the side rail and gauges on Home.",
    };
    private static final int[] DEFAULT_ACCENT = { 0, 4, 1, 5, 0 };

    public static final int[] ACCENTS = { 0xFFE3192B, 0xFFE8ECF1, 0xFFA9B4C2, 0xFF2F7BFF, 0xFFFF9F1A, 0xFF2BD67B };
    public static final String[] ACCENT_NAMES = { "Red", "White", "Silver", "Blue", "Amber", "Green" };

    public static int BG, PANEL, PANEL2, LINE, TEXT, DIM, FAINT;
    public static final int WARN = 0xFFFFB020, BAD = 0xFFFF3B3B, GOOD = 0xFF35D07F;
    public static int accent = ACCENTS[0];
    public static int style = GLI;
    public static boolean night = true;
    public static boolean upper;
    public static float textScale = 1f;
    private static float radiusScale = 1f;
    private static float density = 1f;
    private static Typeface fLight, fRegular, fMedium, fBold, fMono, fMonoBold;
    private static final Map<String, Typeface> cache = new HashMap<>();

    private Theme() { }

    public static void init(Context c, boolean isNight) {
        density = c.getResources().getDisplayMetrics().density;
        night = isNight;
        style = Math.max(0, Math.min(STYLE_NAMES.length - 1, Prefs.i(Prefs.STYLE, GLI)));
        switch (style) {
            case GLI:
                palette(night, 0xFF08090B, 0xF2131417, 0xFF1D1F24, 0xFF2B2E35, 0xFFF3F3F5, 0xFF9298A2, 0xFF4C5059,
                        0xFFECEDEF, 0xF7FFFFFF, 0xFFE2E4E8, 0xFFCDD0D6, 0xFF111216, 0xFF5A606A, 0xFFA6ABB3);
                fonts(c, "saira_300", "saira_400", "saira_600", "saira_600");
                radiusScale = 0.4f; upper = true; textScale = 1f;
                break;
            case CARBON:
                palette(night, 0xFF0C0C0C, 0xD9161616, 0xE6262626, 0xFF353535, 0xFFEEEEEE, 0xFFA3A3A3, 0xFF555555,
                        0xFFD9D9D9, 0xE6F4F4F4, 0xFFE4E4E4, 0xFFBDBDBD, 0xFF141414, 0xFF555555, 0xFF9E9E9E);
                fonts(c, "rajdhani_400", "rajdhani_500", "rajdhani_600", "rajdhani_600");
                radiusScale = 1f; upper = true; textScale = 1.14f;
                break;
            case SIMPLE:
                palette(night, 0xFF000000, 0xFF111111, 0xFF1E1E1E, 0xFF2C2C2C, 0xFFFFFFFF, 0xFFB3B3B3, 0xFF5C5C5C,
                        0xFFFFFFFF, 0xFFF2F2F2, 0xFFE6E6E6, 0xFFD6D6D6, 0xFF000000, 0xFF4D4D4D, 0xFFA0A0A0);
                fonts(c, "inter_300", "inter_400", "inter_600", "inter_600");
                radiusScale = 1.3f; upper = false; textScale = 1.06f;
                break;
            case CODE:
                palette(night, 0xFF040807, 0xFF08110E, 0xFF0D1C17, 0xFF173A2E, 0xFFC9F5D9, 0xFF5E9C7A, 0xFF2D5242,
                        0xFFF3F1EA, 0xFFFBFAF5, 0xFFE9E6DA, 0xFFD0CCBC, 0xFF13261C, 0xFF4F6B5B, 0xFFA3AE9F);
                fonts(c, "jetbrains_mono_400", "jetbrains_mono_400", "jetbrains_mono_600", "jetbrains_mono_600");
                radiusScale = 0.12f; upper = false; textScale = 0.9f;
                break;
            default:
                palette(night, 0xFF090B0E, 0xFF13161B, 0xFF1B1F26, 0xFF272C35, 0xFFF1F3F6, 0xFF8C96A4, 0xFF4A525E,
                        0xFFEEF1F5, 0xFFFFFFFF, 0xFFE6EAF0, 0xFFD3D9E1, 0xFF0E1217, 0xFF586270, 0xFFA7B0BC);
                fLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
                fRegular = Typeface.create("sans-serif", Typeface.NORMAL);
                fMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                fBold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
                radiusScale = 1f; upper = false; textScale = 1f;
        }
        fMono = font(c, "jetbrains_mono_400");
        fMonoBold = font(c, "jetbrains_mono_600");

        int a = Prefs.i(accentKey(), DEFAULT_ACCENT[style]);
        int profile = Prefs.i(Prefs.PROFILE, 1);
        if (profile == 0) a = 5;
        if (profile == 2) a = 0;
        accent = ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, a))];
        if (!night && (accent == ACCENTS[1] || accent == ACCENTS[2])) accent = 0xFF3A4452;
    }

    private static void palette(boolean n, int bg, int p, int p2, int l, int t, int d, int f,
                                int dbg, int dp, int dp2, int dl, int dt, int dd, int df) {
        BG = n ? bg : dbg; PANEL = n ? p : dp; PANEL2 = n ? p2 : dp2; LINE = n ? l : dl;
        TEXT = n ? t : dt; DIM = n ? d : dd; FAINT = n ? f : df;
    }

    private static void fonts(Context c, String light, String regular, String medium, String bold) {
        fLight = font(c, light); fRegular = font(c, regular); fMedium = font(c, medium); fBold = font(c, bold);
    }

    private static Typeface font(Context c, String name) {
        Typeface t = cache.get(name);
        if (t == null) {
            try { t = Typeface.createFromAsset(c.getAssets(), "fonts/" + name + ".ttf"); }
            catch (RuntimeException e) { t = Typeface.DEFAULT; }
            cache.put(name, t);
        }
        return t;
    }

    public static String accentKey() { return "accent_" + style; }

    public static int accentIndex() { return Prefs.i(accentKey(), DEFAULT_ACCENT[style]); }

    /** Head unit layout (home screen of buttons) for every theme except Classic. */
    public static boolean grid() { return style != CLASSIC; }

    public static String label(String s) { return upper ? s.toUpperCase(Locale.US) : s; }

    public static int dp(float v) { return Math.round(v * density); }
    public static float dpf(float v) { return v * density; }

    public static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
    }

    public static boolean lightAccent() { return accent == ACCENTS[1] || accent == ACCENTS[2]; }

    /** Text color to use on top of an accent-colored fill. */
    public static int onAccent() { return lightAccent() ? 0xFF0E1217 : 0xFFFFFFFF; }

    public static Typeface regular() { return fRegular; }
    public static Typeface medium() { return fMedium; }
    public static Typeface light() { return fLight; }
    public static Typeface thin() { return fLight; }
    public static Typeface condensed() { return fBold; }
    public static Typeface mono() { return fMono; }
    public static Typeface monoBold() { return fMonoBold; }

    public static float radius(float dpValue) { return dpf(dpValue * radiusScale); }

    public static GradientDrawable round(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius(radiusDp));
        return g;
    }

    public static GradientDrawable outline(int fill, int stroke, float radiusDp) {
        GradientDrawable g = round(fill, radiusDp);
        g.setStroke(dp(1), stroke);
        return g;
    }
}
