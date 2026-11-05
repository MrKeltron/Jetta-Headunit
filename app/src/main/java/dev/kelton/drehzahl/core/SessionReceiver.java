package dev.kelton.drehzahl.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.audio.SoundEngine;

/** Music apps that support system equalizers announce their audio session here when they start and stop. */
public class SessionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Prefs.init(c);
        int session = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
        if (session <= 0) return;
        String pkg = i.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        String owner = pkg;
        try { owner = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception ignored) { }
        SoundEngine eng = SoundEngine.get(c);
        if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) {
            if (Prefs.i(Prefs.FX_MODE, 1) != 2) eng.open(session, owner, false);
        } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) {
            eng.close(session);
        }
    }
}
