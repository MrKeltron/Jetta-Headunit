package dev.kelton.drehzahl.vehicle;

import java.util.Locale;

import dev.kelton.drehzahl.Prefs;

/** Converts metric values into whatever units are picked in Settings > Units. */
public final class Units {
    private Units() { }

    public static boolean mph() { return Prefs.i(Prefs.U_SPEED, 0) == 0; }
    public static boolean fahrenheit() { return Prefs.i(Prefs.U_TEMP, 0) == 0; }

    public static float speed(float kmh) { return mph() ? kmh * 0.621371f : kmh; }
    public static String speedUnit() { return mph() ? "mph" : "km/h"; }

    public static float dist(float km) { return mph() ? km * 0.621371f : km; }
    public static float distToKm(float v) { return mph() ? v / 0.621371f : v; }
    public static String distUnit() { return mph() ? "mi" : "km"; }

    public static float temp(float c) { return fahrenheit() ? c * 9f / 5f + 32f : c; }
    public static String tempUnit() { return fahrenheit() ? "°F" : "°C"; }

    public static float pressure(float kpa) {
        int u = Prefs.i(Prefs.U_PRESS, 0);
        if (u == 0) return kpa * 0.145038f;
        if (u == 1) return kpa / 100f;
        return kpa;
    }
    public static String pressureUnit() { return new String[] { "psi", "bar", "kPa" }[Prefs.i(Prefs.U_PRESS, 0)]; }
    public static int pressureDecimals() { return Prefs.i(Prefs.U_PRESS, 0) == 1 ? 2 : (Prefs.i(Prefs.U_PRESS, 0) == 0 ? 1 : 0); }

    public static float power(float kw) { return mph() ? kw * 1.34102f : kw; }
    public static String powerUnit() { return mph() ? "hp" : "kW"; }

    public static float torque(float nm) { return mph() ? nm * 0.737562f : nm; }
    public static String torqueUnit() { return mph() ? "lb-ft" : "Nm"; }

    /** Average consumption from litres used over a distance. Returns NaN when not enough data. */
    public static float consumption(float litres, float km) {
        if (km < 0.1f || litres <= 0.0001f) return Float.NaN;
        float l100 = litres / km * 100f;
        return Prefs.i(Prefs.U_CONS, 0) == 0 ? 235.215f / l100 : l100;
    }
    public static String consUnit() { return Prefs.i(Prefs.U_CONS, 0) == 0 ? "mpg" : "L/100km"; }

    public static String fmt(float v, int decimals) {
        if (Float.isNaN(v)) return "--";
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    public static String duration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60;
        return h > 0 ? String.format(Locale.US, "%d h %02d min", h, m) : String.format(Locale.US, "%d min", m);
    }
}
