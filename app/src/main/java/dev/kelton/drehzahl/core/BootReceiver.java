package dev.kelton.drehzahl.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.projection.CarLink;

/** Starts the background engine when the tablet boots, and CarPlay too if that option is on. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        Prefs.init(c);
        CoreService.start(c);
        if (Prefs.b(Prefs.BOOT_CP, false)) CarLink.launch(c);
    }
}
