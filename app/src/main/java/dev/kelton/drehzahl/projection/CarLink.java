package dev.kelton.drehzahl.projection;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

/**
 * CarPlay runs inside CarLink (github.com/F-e-n-y-x/CarLink, AGPL-3.0), a separate app built on DiPlay.
 * Drehzahl only starts it and comes back when you press Home. Change PKG to use a different engine app.
 */
public final class CarLink {
    public static final String PKG = "com.carlink.app";
    public static final int APPLE_VENDOR_ID = 0x05AC;

    private CarLink() { }

    public static boolean installed(Context c) {
        try { c.getPackageManager().getPackageInfo(PKG, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    public static String version(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(PKG, 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) { return null; }
    }

    public static boolean launch(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(PKG);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { c.startActivity(i); return true; } catch (RuntimeException e) { return false; }
    }

    public static void openAppInfo(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + PKG));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (RuntimeException ignored) { }
    }
}
