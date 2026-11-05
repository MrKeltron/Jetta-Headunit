package dev.kelton.drehzahl.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.kelton.drehzahl.Prefs;

/**
 * Reads standard launcher icon packs (like OnePro Dark) the same way third-party launchers do:
 * the pack's appfilter.xml maps each app's component to a drawable inside the pack.
 */
public final class IconPack {
    private static final String[] PACK_ACTIONS = {
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME", "com.gau.go.launcherex.theme"
    };
    private static IconPack loaded;

    private final String pkg;
    private final Resources res;
    private final Map<String, String> map = new HashMap<>();
    private final Map<String, Drawable> cache = new HashMap<>();

    /** Installed icon packs: package name to label. */
    public static Map<String, String> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> out = new LinkedHashMap<>();
        for (String a : PACK_ACTIONS) {
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(a), PackageManager.GET_META_DATA);
            for (ResolveInfo ri : list) {
                String p = ri.activityInfo.packageName;
                if (!out.containsKey(p)) out.put(p, ri.loadLabel(pm).toString());
            }
        }
        return out;
    }

    /** Used until an icon pack is picked in Settings: OnePro (Dark) if it is installed. */
    public static final String DEFAULT_PACK = "com.eatos.onedarkpro";

    public static synchronized IconPack current(Context c) {
        if (!Prefs.has(Prefs.ICON_PACK)) {
            try { c.getPackageManager().getPackageInfo(DEFAULT_PACK, 0); Prefs.put(Prefs.ICON_PACK, DEFAULT_PACK); }
            catch (PackageManager.NameNotFoundException ignored) { }
        }
        String want = Prefs.s(Prefs.ICON_PACK, "");
        if (want.isEmpty()) { loaded = null; return null; }
        if (loaded != null && loaded.pkg.equals(want)) return loaded;
        try { loaded = new IconPack(c, want); } catch (Exception e) { loaded = null; }
        return loaded;
    }

    private IconPack(Context c, String pkg) throws Exception {
        this.pkg = pkg;
        res = c.getPackageManager().getResourcesForApplication(pkg);
        XmlPullParser xp;
        int id = res.getIdentifier("appfilter", "xml", pkg);
        if (id != 0) {
            xp = res.getXml(id);
        } else {
            InputStream in = res.getAssets().open("appfilter.xml");
            xp = XmlPullParserFactory.newInstance().newPullParser();
            xp.setInput(in, "utf-8");
        }
        int ev = xp.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && "item".equals(xp.getName())) {
                String comp = xp.getAttributeValue(null, "component");
                String draw = xp.getAttributeValue(null, "drawable");
                if (comp != null && draw != null) {
                    int a = comp.indexOf('{'), b = comp.indexOf('}');
                    if (a >= 0 && b > a) map.put(comp.substring(a + 1, b), draw);
                }
            }
            ev = xp.next();
        }
    }

    /** Icon from the pack for this app, or null if the pack has none. */
    public Drawable icon(ComponentName cn) {
        String key = cn.getPackageName() + "/" + cn.getClassName();
        if (cache.containsKey(key)) return cache.get(key);
        String name = map.get(key);
        if (name == null) name = map.get(cn.flattenToShortString());
        Drawable d = null;
        if (name != null) {
            int id = res.getIdentifier(name, "drawable", pkg);
            if (id != 0) {
                try { d = res.getDrawable(id, null); } catch (Resources.NotFoundException ignored) { }
            }
        }
        cache.put(key, d);
        return d;
    }

    public static List<String> labels(Map<String, String> packs) { return new ArrayList<>(packs.values()); }
}
