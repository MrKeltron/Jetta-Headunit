package dev.kelton.drehzahl.sys;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Turns Wi-Fi auto-join, and with it Android's periodic network search, off or on.
 * Apps cannot do this, so WifiGuard starts this class as its own process through root:
 *   CLASSPATH=<Drehzahl apk> app_process /system/bin dev.kelton.drehzahl.sys.WifiAutojoin off|on|state
 * It prints one line starting with MARK and exits. The setting is not saved; Android turns
 * auto-join back on when it restarts.
 */
public final class WifiAutojoin {
    public static final String MARK = "DREHZAHL_AUTOJOIN";
    private static final String LISTENER = "android.net.wifi.IBooleanListener";

    private WifiAutojoin() { }

    public static void main(String[] args) {
        Thread limit = new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(10000); } catch (InterruptedException ignored) { }
                System.out.println(MARK + " timeout");
                System.exit(2);
            }
        });
        limit.setDaemon(true);
        limit.start();
        String line;
        try {
            String what = args.length > 0 ? args[0] : "state";
            Object wifi = service();
            if (wifi == null) line = "unsupported no-wifi-service";
            else if (what.equals("state")) line = "ok state=" + word(query(wifi));
            else line = set(wifi, what.equals("on"));
        } catch (SecurityException e) {
            line = "denied " + e.getMessage();
        } catch (Throwable t) {
            line = "error " + t;
        }
        System.out.println(MARK + " " + line);
        System.exit(0);
    }

    private static Object service() throws Exception {
        IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "wifi");
        if (b == null) return null;
        return Class.forName("android.net.wifi.IWifiManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, b);
    }

    private static String set(Object wifi, boolean on) throws Throwable {
        Method setter = null;
        for (Method m : wifi.getClass().getMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (m.getName().equals("allowAutojoinGlobal") && t.length >= 1 && t[0] == boolean.class) { setter = m; break; }
        }
        if (setter == null) {
            for (Method m : wifi.getClass().getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (m.getName().equals("enableWifiConnectivityManager") && t.length == 1 && t[0] == boolean.class) { setter = m; break; }
            }
        }
        if (setter == null) return "unsupported no-method";
        Class<?>[] t = setter.getParameterTypes();
        Object[] a = new Object[t.length];
        a[0] = on;
        for (int i = 1; i < t.length; i++) {
            if (t[i] == String.class) a[i] = android.os.Process.myUid() == 0 ? "android" : "com.android.shell";
            else if (t[i] == Bundle.class) a[i] = new Bundle();
            else a[i] = null;
        }
        try {
            setter.invoke(wifi, a);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
        Boolean now = query(wifi);
        if (now != null && now != on) return "unknown state=" + word(now) + " via=" + setter.getName();
        return "ok state=" + (now == null ? (on ? "on" : "off") + "?" : word(now)) + " via=" + setter.getName() + "/" + t.length;
    }

    /** Android 13+ reports the current setting through a one-way callback. Null when it cannot. */
    private static Boolean query(Object wifi) {
        try {
            final Class<?> type = Class.forName(LISTENER);
            Method q = wifi.getClass().getMethod("queryAutojoinGlobal", type);
            final Boolean[] answer = { null };
            final CountDownLatch done = new CountDownLatch(1);
            final Binder callback = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
                    if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
                    data.enforceInterface(LISTENER);
                    answer[0] = data.readInt() != 0;
                    done.countDown();
                    return true;
                }
            };
            callback.attachInterface(null, LISTENER);
            Object listener = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] args) {
                    if (m.getName().equals("asBinder")) return callback;
                    if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (m.getName().equals("equals")) return args != null && proxy == args[0];
                    if (m.getName().equals("toString")) return "DrehzahlAutojoinListener";
                    return null;
                }
            });
            q.invoke(wifi, listener);
            return done.await(3, TimeUnit.SECONDS) ? answer[0] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String word(Boolean b) { return b == null ? "unknown" : b ? "on" : "off"; }
}
