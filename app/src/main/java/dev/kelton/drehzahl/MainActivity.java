package dev.kelton.drehzahl;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import dev.kelton.drehzahl.core.CoreService;
import dev.kelton.drehzahl.media.MediaHub;
import dev.kelton.drehzahl.screens.AppsScreen;
import dev.kelton.drehzahl.screens.CarPlayScreen;
import dev.kelton.drehzahl.screens.HomeScreen;
import dev.kelton.drehzahl.screens.MediaScreen;
import dev.kelton.drehzahl.screens.SettingsScreen;
import dev.kelton.drehzahl.screens.SoundScreen;
import dev.kelton.drehzahl.screens.VehicleScreen;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** The whole app is this one activity: side rail, top bar, and a stack of screens in the middle. */
public class MainActivity extends Activity implements SensorEventListener {
    public static final int HOME = 0, MEDIA = 1, CARPLAY = 2, CAR = 3, SOUND = 4, APPS = 5, SETTINGS = 6, PERFORMANCE = 7, WIDGETS = 8;
    private static final String[] RAIL_LABELS = { "Home", "Media", "CarPlay", "Car", "Sound", "Apps", "Settings" };
    private static final int[] RAIL_ICONS = { Icons.HOME, Icons.MEDIA, Icons.CARPLAY, Icons.CAR, Icons.SOUND, Icons.APPS, Icons.SETTINGS };

    private final Screen[] roots = new Screen[9];
    private final ArrayList<Screen> stack = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayList<LinearLayout> railItems = new ArrayList<>();
    private Screen active;

    private FrameLayout content, outer;
    private View topBar;
    private TextView titleView, clockView, dateView, batteryView;
    private ImageView backBtn;
    private View blackout;
    private SensorManager sensors;
    private Sensor light;
    private boolean pendingNight;
    private long pendingSince;
    private int batteryPct = -1;
    private boolean charging;

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            checkClockNight();
            h.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            batteryPct = lvl < 0 ? -1 : Math.round(lvl * 100f / sc);
            int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            updateClock();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        light = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        Theme.init(this, startNight());
        buildUi();
        go(HOME);
        CoreService.start(this);
        MediaHub.get(this).start();
        dev.kelton.drehzahl.core.EventLog.add("ui: started, theme " + Theme.STYLE_NAMES[Theme.style].toLowerCase(Locale.US));
        boolean handled = handleIntent(getIntent());
        if (b == null && !handled && Prefs.b(Prefs.INTRO, true)) playIntro();
    }

    // ---------- layout ----------

    private void buildUi() {
        outer = new FrameLayout(this);
        outer.setBackground(new dev.kelton.drehzahl.ui.Backdrop(Theme.style));
        LinearLayout main = W.vbox(this);
        topBar = buildTopBar();
        main.addView(topBar, new LinearLayout.LayoutParams(W.MATCH, Theme.dp(Theme.grid() ? 66 : 72)));
        content = new FrameLayout(this);
        main.addView(content, W.vweight(1));
        if (Theme.grid()) {
            railItems.clear();
            outer.addView(main, new FrameLayout.LayoutParams(W.MATCH, W.MATCH));
        } else {
            LinearLayout row = W.hbox(this);
            row.setGravity(Gravity.TOP);
            row.addView(buildRail(), new LinearLayout.LayoutParams(Theme.dp(116), W.MATCH));
            row.addView(main, W.weight(1));
            outer.addView(row, new FrameLayout.LayoutParams(W.MATCH, W.MATCH));
        }

        blackout = new View(this);
        blackout.setBackgroundColor(0xFF000000);
        blackout.setVisibility(View.GONE);
        blackout.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { blackout.setVisibility(View.GONE); }
        });
        outer.addView(blackout, new FrameLayout.LayoutParams(W.MATCH, W.MATCH));

        outer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(s.left, s.top, s.right, s.bottom);
                } else {
                    v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                }
                return in;
            }
        });
        setContentView(outer);
    }

    private View buildRail() {
        LinearLayout rail = W.vbox(this);
        rail.setBackgroundColor(Theme.PANEL);
        rail.setPadding(0, Theme.dp(10), 0, Theme.dp(10));
        railItems.clear();
        for (int i = 0; i < RAIL_LABELS.length; i++) {
            final int idx = i;
            LinearLayout item = W.vbox(this);
            item.setGravity(Gravity.CENTER);
            ImageView ic = W.icon(this, RAIL_ICONS[i], Theme.DIM, 34);
            item.addView(ic);
            TextView t = W.text(this, RAIL_LABELS[i], 13, Theme.DIM, Theme.medium());
            t.setPadding(0, Theme.dp(6), 0, 0);
            t.setGravity(Gravity.CENTER);
            item.addView(t, W.lp(W.MATCH, W.WRAP));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (idx == CARPLAY && stack.size() > 0 && stack.get(0) == roots[CARPLAY]) {
                        ((CarPlayScreen) roots[CARPLAY]).startCarPlay();
                        return;
                    }
                    go(idx);
                }
            });
            LinearLayout.LayoutParams lp = W.vweight(1);
            lp.setMargins(Theme.dp(10), Theme.dp(3), Theme.dp(10), Theme.dp(3));
            rail.addView(item, lp);
            railItems.add(item);
        }
        return rail;
    }

    private View buildTopBar() {
        LinearLayout bar = W.hbox(this);
        bar.setPadding(Theme.dp(12), 0, Theme.dp(28), 0);
        backBtn = W.icon(this, Icons.BACK, Theme.TEXT, 30);
        backBtn.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        backBtn.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(56), Theme.dp(56)));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Theme.grid() && stack.size() == 2) go(HOME); else back();
            }
        });
        bar.addView(backBtn);
        titleView = W.text(this, "", 24, Theme.TEXT, Theme.medium());
        if (Theme.upper) titleView.setLetterSpacing(0.08f);
        titleView.setPadding(Theme.dp(8), 0, 0, 0);
        bar.addView(titleView, new LinearLayout.LayoutParams(0, W.WRAP, 1f));
        batteryView = W.text(this, "", 16, Theme.DIM);
        bar.addView(batteryView);
        dateView = W.text(this, "", 18, Theme.DIM);
        dateView.setPadding(Theme.dp(28), 0, Theme.dp(20), 0);
        bar.addView(dateView);
        clockView = W.text(this, "", 28, Theme.TEXT, Theme.light());
        clockView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { screenOff(); return true; }
        });
        bar.addView(clockView);
        return bar;
    }

    private void highlightRail(int section) {
        for (int i = 0; i < railItems.size(); i++) {
            LinearLayout item = railItems.get(i);
            boolean on = i == section;
            item.setBackground(on ? Theme.round(Theme.PANEL2, 16) : null);
            ((Icons) ((ImageView) item.getChildAt(0)).getDrawable()).setColor(on ? Theme.accent : Theme.DIM);
            ((TextView) item.getChildAt(1)).setTextColor(on ? Theme.TEXT : Theme.DIM);
        }
    }

    // ---------- navigation ----------

    private Screen root(int section) {
        if (roots[section] == null) {
            switch (section) {
                case MEDIA: roots[section] = new MediaScreen(); break;
                case CARPLAY: roots[section] = new CarPlayScreen(); break;
                case CAR: roots[section] = Theme.grid()
                        ? new VehicleScreen(VehicleScreen.CAR_PAGES, "Car", CAR) : new VehicleScreen(); break;
                case PERFORMANCE: roots[section] = new VehicleScreen(VehicleScreen.PERF_PAGES, "Performance", PERFORMANCE); break;
                case WIDGETS: roots[section] = new dev.kelton.drehzahl.screens.WidgetsScreen(); break;
                case SOUND: roots[section] = new SoundScreen(); break;
                case APPS: roots[section] = new AppsScreen(); break;
                case SETTINGS: roots[section] = new SettingsScreen(); break;
                default: roots[section] = Theme.grid() ? new dev.kelton.drehzahl.screens.HomeGridScreen() : new HomeScreen();
            }
        }
        return roots[section];
    }

    public Screen rootScreen(int section) { return root(section); }

    public void go(int section) {
        if (!Theme.grid() && section > SETTINGS) section = section == PERFORMANCE ? CAR : HOME;
        deactivate();
        stack.clear();
        if (Theme.grid()) {
            stack.add(root(HOME));
            if (section != HOME) stack.add(root(section));
        } else {
            stack.add(root(section));
        }
        show();
    }

    /** Open a section and pick a page inside it (for example Settings > Units). */
    public void go(int section, int page) {
        go(section);
        Screen s = stack.get(stack.size() - 1);
        if (s instanceof dev.kelton.drehzahl.ui.SplitScreen) ((dev.kelton.drehzahl.ui.SplitScreen) s).select(page);
    }

    public void push(Screen s) {
        deactivate();
        stack.add(s);
        show();
    }

    public void back() {
        if (stack.isEmpty()) return;
        Screen top = stack.get(stack.size() - 1);
        if (top.onBack()) return;
        if (stack.size() > 1) {
            deactivate();
            stack.remove(stack.size() - 1);
            show();
        } else if (!Theme.grid() && top != roots[HOME]) {
            go(HOME);
        }
    }

    private void show() {
        Screen top = stack.get(stack.size() - 1);
        View v = top.view(this);
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(W.MATCH, W.MATCH));
        if (Prefs.b(Prefs.ANIM, true)) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(160).start();
        }
        titleView.setText(Theme.grid() ? Theme.label(top.title()) : top.title());
        if (Theme.grid()) {
            topBar.setVisibility(stack.size() == 1 ? View.GONE : View.VISIBLE);
            backBtn.setImageDrawable(new Icons(stack.size() == 2 ? Icons.HOME : Icons.BACK, Theme.TEXT));
            backBtn.setVisibility(View.VISIBLE);
        } else {
            backBtn.setVisibility(stack.size() > 1 || top != roots[HOME] ? View.VISIBLE : View.INVISIBLE);
            highlightRail(top.section());
        }
        activate(top);
    }

    private void activate(Screen s) {
        if (active == s) return;
        deactivate();
        active = s;
        s.onShow();
    }

    private void deactivate() {
        if (active == null) return;
        Screen s = active;
        active = null;
        s.onHide();
    }

    /** Rebuild every view with the current theme (after day/night or accent changes). */
    public void rebuild() {
        Theme.init(this, Theme.night);
        rebuildViews();
    }

    private void rebuildViews() {
        deactivate();
        for (Screen s : roots) if (s != null) s.invalidateView();
        for (Screen s : stack) s.invalidateView();
        buildUi();
        applyWindow();
        show();
        updateClock();
    }

    /** A new theme was picked: rebuild everything, land on Settings > Display and play the theme's intro. */
    public void styleChanged() {
        deactivate();
        for (Screen s : roots) if (s != null) s.invalidateView();
        java.util.Arrays.fill(roots, null);
        stack.clear();
        Theme.init(this, startNight());
        buildUi();
        applyWindow();
        go(SETTINGS, dev.kelton.drehzahl.screens.SettingsScreen.DISPLAY);
        updateClock();
        dev.kelton.drehzahl.core.EventLog.add("ui: theme " + Theme.STYLE_NAMES[Theme.style].toLowerCase(Locale.US));
        if (Prefs.b(Prefs.INTRO, true)) playIntro();
    }

    public void playIntro() {
        final dev.kelton.drehzahl.ui.IntroView[] ref = new dev.kelton.drehzahl.ui.IntroView[1];
        ref[0] = new dev.kelton.drehzahl.ui.IntroView(this, Theme.style, Prefs.s(Prefs.INTRO_TEXT, "GLI"), bootLines(), new Runnable() {
            @Override public void run() { if (ref[0].getParent() != null) ((ViewGroup) ref[0].getParent()).removeView(ref[0]); }
        });
        outer.addView(ref[0], new FrameLayout.LayoutParams(W.MATCH, W.MATCH));
    }

    private String[] bootLines() {
        dev.kelton.drehzahl.audio.SoundEngine eng = dev.kelton.drehzahl.audio.SoundEngine.get(this);
        dev.kelton.drehzahl.vehicle.VehicleHub hub = dev.kelton.drehzahl.vehicle.VehicleHub.get(this);
        String cl = dev.kelton.drehzahl.projection.CarLink.version(this);
        return new String[] {
            "drehzahl " + versionName() + " on " + Build.MANUFACTURER + " " + Build.MODEL + " (android " + Build.VERSION.RELEASE + ")",
            "[ OK ] theme code, font jetbrains mono",
            "[ OK ] audio engine: " + eng.supported.size() + " effect types",
            "[ OK ] vehicle source: " + dev.kelton.drehzahl.vehicle.VehicleHub.SOURCES[hub.source()].toLowerCase(Locale.US),
            "[ OK ] sensors: accel " + (hub.motion.hasAccel() ? "yes" : "no") + ", gps " + (hub.motion.hasGps() ? "yes" : "no")
                    + ", light " + (light != null ? "yes" : "no"),
            (cl == null ? "[WARN] carplay bridge: carlink missing" : "[ OK ] carplay bridge: carlink " + cl),
            "starting ui..."
        };
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /**
     * Extras for scripts and adb: style (theme number), open (section) with page, intro (true to play it).
     * Example: adb shell am start -n dev.kelton.drehzahl/.MainActivity --ei style 0 --ei open 1 --ei page 0
     */
    private boolean handleIntent(Intent i) {
        if (i == null || i.getExtras() == null) return false;
        boolean any = false;
        if (i.hasExtra("style")) {
            Prefs.put(Prefs.STYLE, i.getIntExtra("style", 0));
            deactivate();
            for (Screen s : roots) if (s != null) s.invalidateView();
            java.util.Arrays.fill(roots, null);
            stack.clear();
            Theme.init(this, startNight());
            buildUi();
            applyWindow();
            go(HOME);
            any = true;
        }
        if (i.hasExtra("open")) { go(i.getIntExtra("open", 0), i.getIntExtra("page", 0)); any = true; }
        if (i.getBooleanExtra("intro", false)) { playIntro(); any = true; }
        i.removeExtra("style"); i.removeExtra("open"); i.removeExtra("page"); i.removeExtra("intro");
        return any;
    }

    @Override public void onBackPressed() { back(); }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (handleIntent(i)) return;
        if (i.hasCategory(Intent.CATEGORY_HOME)) {
            blackout.setVisibility(View.GONE);
            go(HOME);
        }
    }

    // ---------- lifecycle ----------

    @Override protected void onResume() {
        super.onResume();
        applyWindow();
        Intent sticky = registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) battery.onReceive(this, sticky);
        h.post(clockTick);
        if (light != null && Prefs.i(Prefs.THEME, 0) == 2) sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        if (!stack.isEmpty()) activate(stack.get(stack.size() - 1));
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(battery); } catch (RuntimeException ignored) { }
        h.removeCallbacks(clockTick);
        if (sensors != null) sensors.unregisterListener(this);
        deactivate();
    }

    public void applyWindow() {
        if (Prefs.b(Prefs.KEEP_ON, true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean hide = Prefs.b(Prefs.HIDE_BARS, true);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (hide) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            int flags = hide ? (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE) : 0;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        getWindow().setStatusBarColor(Theme.BG);
        getWindow().setNavigationBarColor(Theme.BG);
    }

    public void screenOff() { blackout.setVisibility(View.VISIBLE); }

    // ---------- clock and status ----------

    private void updateClock() {
        if (clockView == null) return;
        Date now = new Date();
        boolean h24 = Prefs.b(Prefs.CLOCK24, false), sec = Prefs.b(Prefs.SECONDS, false);
        String tf = h24 ? (sec ? "HH:mm:ss" : "HH:mm") : (sec ? "h:mm:ss a" : "h:mm a");
        clockView.setText(new SimpleDateFormat(tf, Locale.US).format(now));
        dateView.setText(new SimpleDateFormat(dateFormat(), Locale.US).format(now));
        batteryView.setText(batteryPct < 0 ? "" : "Tablet " + batteryPct + "%" + (charging ? " charging" : ""));
    }

    public static String dateFormat() {
        switch (Prefs.i(Prefs.DATE_FMT, 0)) {
            case 1: return "MM/dd/yyyy";
            case 2: return "yyyy-MM-dd";
            default: return "EEE, MMM d";
        }
    }

    // ---------- day / night ----------

    private boolean startNight() {
        int mode = Prefs.i(Prefs.THEME, 0);
        if (mode == 0) return true;
        if (mode == 1) return false;
        return clockSaysNight();
    }

    private static boolean clockSaysNight() {
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hr >= 19 || hr < 7;
    }

    private void checkClockNight() {
        if (Prefs.i(Prefs.THEME, 0) != 2 || light != null) return;
        boolean want = clockSaysNight();
        if (want != Theme.night) { Theme.init(this, want); rebuildViews(); }
    }

    /** Called when Settings > Display > Theme changes. */
    public void themeModeChanged() {
        if (sensors != null) sensors.unregisterListener(this);
        if (light != null && Prefs.i(Prefs.THEME, 0) == 2) sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        Theme.init(this, startNight());
        rebuildViews();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float lux = e.values[0];
        boolean want = Theme.night;
        if (lux < 12f) want = true;
        else if (lux > 45f) want = false;
        if (want == Theme.night) { pendingSince = 0; return; }
        if (pendingSince == 0 || want != pendingNight) { pendingNight = want; pendingSince = SystemClock.elapsedRealtime(); return; }
        if (SystemClock.elapsedRealtime() - pendingSince > 8000) {
            pendingSince = 0;
            Theme.init(this, want);
            rebuildViews();
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    public boolean hasLightSensor() { return light != null; }
}
