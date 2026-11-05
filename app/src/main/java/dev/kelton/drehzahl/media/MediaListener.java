package dev.kelton.drehzahl.media;

import android.service.notification.NotificationListenerService;

/** Exists only so Android lets Drehzahl read what is playing. It ignores notifications themselves. */
public class MediaListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        MediaHub.get(this).start();
    }
}
