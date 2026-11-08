package dev.kelton.drehzahl.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.IBinder;

import dev.kelton.drehzahl.MainActivity;
import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.R;
import dev.kelton.drehzahl.audio.SessionWatcher;
import dev.kelton.drehzahl.audio.SoundEngine;
import dev.kelton.drehzahl.projection.CarLink;
import dev.kelton.drehzahl.vehicle.VehicleData;
import dev.kelton.drehzahl.vehicle.VehicleHub;

/**
 * Always-on background part of Drehzahl. Keeps audio effects alive while other apps play,
 * opens CarPlay when an iPhone is plugged in, and runs speed-dependent volume.
 */
public class CoreService extends Service {
    private static final String CHANNEL = "engine";
    private SessionWatcher watcher;
    private boolean holding;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(a)) {
                UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (d != null && d.getVendorId() == CarLink.APPLE_VENDOR_ID && Prefs.b(Prefs.AUTO_CP, true)) CarLink.launch(c);
                if (d != null && dev.kelton.drehzahl.obd.UsbSerial.supported(d)) dev.kelton.drehzahl.obd.ObdSource.get(c).poke();
            } else {
                new SessionReceiver().onReceive(c, i);
            }
        }
    };

    private final VehicleHub.Listener speedVolume = new VehicleHub.Listener() {
        @Override public void onData(VehicleData d) {
            if (d.live && d.hasSpeed) SoundEngine.get(CoreService.this).speedTick(d.speedKmh);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, CoreService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (RuntimeException ignored) { }
    }

    /** Re-read settings that change what the service does (called from the Sound and Car screens). */
    public static void refresh(Context c) { start(c); }

    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        goForeground();
        IntentFilter f = new IntentFilter();
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, f);
        SoundEngine eng = SoundEngine.get(this);
        eng.applyStartupVolume();
        watcher = new SessionWatcher(this);
        watcher.start();
        PowerFollow.get(this).start();
        WifiGuard.get(this).start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SoundEngine.get(this).applyMode();
        SoundEngine.get(this).applyAll();
        int src = Prefs.i(Prefs.SOURCE, 0);
        boolean wantSpeed = (Prefs.i(Prefs.SPEED_VOL, 0) > 0 && src != 0) || src == 2;
        if (src == 2) dev.kelton.drehzahl.obd.ObdSource.get(this).start();
        else dev.kelton.drehzahl.obd.ObdSource.get(this).stop();
        VehicleHub hub = VehicleHub.get(this);
        if (wantSpeed && !holding) { hub.addListener(speedVolume); hub.hold(true); holding = true; }
        if (!wantSpeed && holding) { hub.removeListener(speedVolume); hub.hold(false); holding = false; }
        return START_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Sound engine", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Drehzahl")
                .setContentText("Sound settings active")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
        if (watcher != null) watcher.stop();
        PowerFollow.get(this).stop();
        WifiGuard.get(this).stop();
        if (holding) { VehicleHub.get(this).removeListener(speedVolume); VehicleHub.get(this).hold(false); }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
