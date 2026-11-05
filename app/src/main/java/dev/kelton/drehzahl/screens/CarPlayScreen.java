package dev.kelton.drehzahl.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.ui.Controls;
import dev.kelton.drehzahl.ui.Icons;
import dev.kelton.drehzahl.ui.Screen;
import dev.kelton.drehzahl.ui.Theme;
import dev.kelton.drehzahl.ui.W;

/** Launch pad for CarPlay. Tapping the rail button again while here opens CarPlay directly. */
public class CarPlayScreen extends Screen {
    private TextView status;

    @Override public String title() { return "CarPlay"; }
    @Override public int section() { return MainActivity.CARPLAY; }

    public void startCarPlay() {
        if (!CarLink.launch(host)) W.toast(host, "CarLink is not installed. Use Install in the PC toolkit.");
    }

    @Override protected View build() {
        Context c = host;
        FrameLayout frame = new FrameLayout(c);
        LinearLayout box = W.page(c, frame);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout hero = W.card(c);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.addView(W.icon(c, Icons.CARPLAY, Theme.accent, 96));
        TextView t = W.text(c, "CarPlay", 34, Theme.TEXT, Theme.medium());
        t.setPadding(0, Theme.dp(14), 0, Theme.dp(8));
        hero.addView(t);
        status = W.text(c, "", 17, Theme.DIM);
        status.setGravity(Gravity.CENTER);
        hero.addView(status);
        TextView go = W.button(c, "Open CarPlay", true, new View.OnClickListener() {
            public void onClick(View v) { startCarPlay(); }
        });
        LinearLayout.LayoutParams gp = W.lp(Theme.dp(320), W.WRAP);
        gp.topMargin = Theme.dp(22);
        hero.addView(go, gp);
        box.addView(hero, W.margins(W.lp(Theme.dp(640), W.WRAP), 0, 0, 0, 12));

        LinearLayout opts = W.vbox(c);
        opts.addView(W.toggleRow(c, "Open when an iPhone is plugged in", "Watches the USB port for an Apple device",
                Prefs.b(Prefs.AUTO_CP, true), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.AUTO_CP, v); } }));
        opts.addView(W.toggleRow(c, "Open when the tablet starts", "Lets wireless CarPlay reconnect as soon as the car turns on",
                Prefs.b(Prefs.BOOT_CP, false), new Controls.BoolCb() { public void on(boolean v) { Prefs.put(Prefs.BOOT_CP, v); } }));
        opts.addView(W.chevronRow(c, "CarLink app info", "Permissions, storage, force stop", new View.OnClickListener() {
            public void onClick(View v) { CarLink.openAppInfo(host); }
        }));
        opts.addView(W.note(c, "CarPlay runs in CarLink, a separate open source app (github.com/F-e-n-y-x/CarLink, AGPL-3.0) built on the DiPlay engine. "
                + "Press Home or the Drehzahl rail to come back here. Wireless CarPlay needs Wi-Fi Direct on the tablet; "
                + "check Settings > Tablet info."));
        box.addView(opts, W.lp(Theme.dp(640), W.WRAP));
        return frame;
    }

    @Override public void onShow() {
        String v = CarLink.version(host);
        status.setText(v == null ? "CarLink is not installed yet.\nOn your PC: JettaHeadUnit > START HERE > 4 Install."
                : "CarLink " + v + " installed. Plug in your iPhone or pair it once over Bluetooth for wireless.");
    }
}
