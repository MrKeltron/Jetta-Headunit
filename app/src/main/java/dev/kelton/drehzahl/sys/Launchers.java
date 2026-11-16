package dev.kelton.drehzahl.sys;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.core.EventLog;
import dev.kelton.drehzahl.core.LauncherPick;

/** Finds the tablet's other home apps and opens one (the "Stock launcher" button on Home). */
public final class Launchers {
    private Launchers() { }

    /** Every enabled home app on the tablet, Drehzahl included. */
    public static List<LauncherPick.Home> all(Context c) {
        List<LauncherPick.Home> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> found;
        try { found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0); }
        catch (RuntimeException e) { return out; }
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            boolean system = ai != null && (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            CharSequence label = ri.loadLabel(pm);
            out.add(new LauncherPick.Home(ri.activityInfo.packageName, ri.activityInfo.name,
                    label == null ? ri.activityInfo.packageName : label.toString(), system, ri.priority));
        }
        return out;
    }

    /** The home apps the button can open. */
    public static List<LauncherPick.Home> choices(Context c) { return LauncherPick.usable(all(c), c.getPackageName()); }

    /** The one the button opens right now, or null. */
    public static LauncherPick.Home current(Context c) {
        return LauncherPick.choose(all(c), c.getPackageName(), Prefs.s(Prefs.STOCK_LAUNCHER, ""));
    }

    /** Opens it. False when the tablet has no other launcher or it would not start. */
    public static boolean open(Context c) {
        LauncherPick.Home h = current(c);
        if (h == null) return false;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(h.pkg, h.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            EventLog.add("home: opened " + h.label + " (" + h.pkg + ")");
            return true;
        } catch (RuntimeException e) {
            EventLog.add("home: could not open " + h.pkg + ", " + e.getMessage());
            return false;
        }
    }
}
