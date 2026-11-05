package dev.kelton.drehzahl.screens;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.icons.IconPack;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** App drawer. Uses the icon pack picked in Settings > Home and apps. Long press = app info. */
public class AppsScreen extends Screen {
    private static final class App { String label; ComponentName cn; Drawable icon; }
    private final List<App> apps = new ArrayList<>();
    private BaseAdapter adapter;

    @Override public String title() { return "Apps"; }
    @Override public int section() { return MainActivity.APPS; }

    @Override protected View build() {
        final Context c = host;
        GridView grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Theme.dp(150));
        grid.setVerticalSpacing(Theme.dp(10));
        grid.setHorizontalSpacing(Theme.dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(Theme.dp(24), Theme.dp(12), Theme.dp(24), Theme.dp(24));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        adapter = new BaseAdapter() {
            public int getCount() { return apps.size(); }
            public Object getItem(int i) { return apps.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                LinearLayout cell = (LinearLayout) v;
                if (cell == null) {
                    cell = W.vbox(c);
                    cell.setGravity(Gravity.CENTER);
                    cell.setPadding(Theme.dp(8), Theme.dp(18), Theme.dp(8), Theme.dp(14));
                    cell.setBackground(Theme.round(Theme.PANEL, 16));
                    ImageView iv = new ImageView(c);
                    cell.addView(iv, new LinearLayout.LayoutParams(Theme.dp(72), Theme.dp(72)));
                    TextView t = W.text(c, "", 15, Theme.TEXT);
                    t.setGravity(Gravity.CENTER);
                    t.setMaxLines(2);
                    t.setPadding(0, Theme.dp(10), 0, 0);
                    cell.addView(t, W.lp(W.MATCH, W.WRAP));
                }
                App a = apps.get(i);
                ((ImageView) cell.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) cell.getChildAt(1)).setText(a.label);
                return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(apps.get(i).cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try { host.startActivity(li); } catch (RuntimeException e) { W.toast(host, "Could not open " + apps.get(i).label); }
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int i, long id) {
                Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + apps.get(i).cn.getPackageName()));
                info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.startActivity(info);
                return true;
            }
        });
        return grid;
    }

    private void load() {
        PackageManager pm = host.getPackageManager();
        IconPack pack = IconPack.current(host);
        List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        apps.clear();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo.packageName.equals(host.getPackageName())) continue;
            App a = new App();
            a.label = ri.loadLabel(pm).toString();
            a.cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            Drawable themed = pack == null ? null : pack.icon(a.cn);
            a.icon = themed != null ? themed : ri.loadIcon(pm);
            apps.add(a);
        }
        Collections.sort(apps, new Comparator<App>() {
            public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
        adapter.notifyDataSetChanged();
    }

    @Override public void onShow() { load(); }
}
