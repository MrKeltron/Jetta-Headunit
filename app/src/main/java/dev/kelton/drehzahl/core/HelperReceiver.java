package dev.kelton.drehzahl.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.kelton.drehzahl.Prefs;

/** sys.ShellHelper announces its port and token here. The manifest only lets senders holding DUMP (the shell) in. */
public final class HelperReceiver extends BroadcastReceiver {
    public static final String SELF_START = "dev.kelton.drehzahl.SELF_START";

    @Override public void onReceive(final Context c, Intent i) {
        if (SELF_START.equals(i.getAction())) {
            final String via = i.getStringExtra("via");
            final boolean ask = i.getBooleanExtra("ask", false);
            new Thread(new Runnable() {
                public void run() {
                    EventLog.add("Wi-Fi guard: start helper on the tablet (" + (via == null ? "auto" : via) + "): " + HelperStarter.start(c, via, ask));
                }
            }).start();
            return;
        }
        int port = i.getIntExtra("port", 0);
        String token = i.getStringExtra("token");
        if (port <= 0 || token == null || token.length() < 16) return;
        Prefs.init(c);
        ShellLink.set(port, token);
        EventLog.add("Wi-Fi helper started on the tablet (port " + port + ")");
        WifiGuard.get(c).helperArrived();
    }
}
