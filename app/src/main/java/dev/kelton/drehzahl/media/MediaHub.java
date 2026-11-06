package dev.kelton.drehzahl.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/** Follows whichever app is playing (Spotify, CarLink, YouTube Music...) and exposes title, art and controls. */
public final class MediaHub implements MediaSessionManager.OnActiveSessionsChangedListener {
    public interface Listener { void onMediaChanged(); }

    private static MediaHub inst;
    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private MediaSessionManager msm;
    private MediaController active;
    private boolean started;
    public boolean permitted;

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { notifyChanged(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { notifyChanged(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };

    public static MediaHub get(Context c) {
        if (inst == null) inst = new MediaHub(c.getApplicationContext());
        return inst;
    }

    private MediaHub(Context c) { ctx = c; }

    public void start() {
        msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) return;
        try {
            if (!started) msm.addOnActiveSessionsChangedListener(this, component(), h);
            started = true;
            permitted = true;
            refresh();
        } catch (SecurityException e) {
            permitted = false;
            notifyChanged();
        }
    }

    private ComponentName component() { return new ComponentName(ctx, MediaListener.class); }

    private void refresh() {
        try { onActiveSessionsChanged(msm.getActiveSessions(component())); }
        catch (RuntimeException e) { permitted = false; notifyChanged(); }
    }

    @Override public void onActiveSessionsChanged(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
            }
            if (pick == null && !list.isEmpty()) pick = list.get(0);
        }
        BtReceiver.get(ctx).sessions(list);
        if (active != null) active.unregisterCallback(cb);
        active = pick;
        if (active != null) active.registerCallback(cb, h);
        notifyChanged();
    }

    private String lastLogged = "";

    private void notifyChanged() {
        String now = hasSession() ? appName() + " > " + title() : "";
        if (!now.equals(lastLogged)) {
            lastLogged = now;
            if (!now.isEmpty()) dev.kelton.drehzahl.core.EventLog.add("media: " + now);
        }
        for (Listener l : new ArrayList<>(listeners)) l.onMediaChanged();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public boolean hasSession() { return active != null; }

    private MediaMetadata meta() { return active == null ? null : active.getMetadata(); }

    public String title() {
        MediaMetadata m = meta();
        String t = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        return t == null ? "Nothing playing" : t;
    }

    public String artist() {
        MediaMetadata m = meta();
        if (m == null) return "";
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        return a == null ? "" : a;
    }

    public String album() {
        MediaMetadata m = meta();
        String a = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        return a == null ? "" : a;
    }

    public Bitmap art() {
        MediaMetadata m = meta();
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    public String appName() {
        if (active == null) return "";
        PackageManager pm = ctx.getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(active.getPackageName(), 0)).toString(); }
        catch (Exception e) { return active.getPackageName(); }
    }

    public String packageName() { return active == null ? null : active.getPackageName(); }

    public boolean playing() {
        PlaybackState s = active == null ? null : active.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    public long duration() {
        MediaMetadata m = meta();
        return m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    public long position() {
        PlaybackState s = active == null ? null : active.getPlaybackState();
        if (s == null) return 0;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return Math.max(0, pos);
    }

    public void playPause() {
        if (active == null) return;
        if (playing()) active.getTransportControls().pause(); else active.getTransportControls().play();
    }

    public void next() { if (active != null) active.getTransportControls().skipToNext(); }

    public void seekTo(long ms) { if (active != null) active.getTransportControls().seekTo(Math.max(0, ms)); }

    /** Jump forward (positive) or back (negative) by this many milliseconds. */
    public void jump(long ms) {
        if (active == null) return;
        long d = duration();
        long to = position() + ms;
        if (d > 0) to = Math.min(to, d - 1000);
        seekTo(to);
    }

    /** The playing app's queue, or an empty list when the app does not share it. */
    public List<android.media.session.MediaSession.QueueItem> queue() {
        List<android.media.session.MediaSession.QueueItem> q = active == null ? null : active.getQueue();
        return q == null ? new ArrayList<android.media.session.MediaSession.QueueItem>() : q;
    }

    public long activeQueueId() {
        PlaybackState s = active == null ? null : active.getPlaybackState();
        return s == null ? -1 : s.getActiveQueueItemId();
    }

    public void skipToQueueItem(long id) { if (active != null) active.getTransportControls().skipToQueueItem(id); }

    public boolean isBluetooth() { return BtReceiver.isBluetoothSession(active); }

    public void openApp(Context c) {
        String p = packageName();
        if (p == null) return;
        android.content.Intent i = c.getPackageManager().getLaunchIntentForPackage(p);
        if (i == null) return;
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (RuntimeException ignored) { }
    }
    public void prev() { if (active != null) active.getTransportControls().skipToPrevious(); }
}
