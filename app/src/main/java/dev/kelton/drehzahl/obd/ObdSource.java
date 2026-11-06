package dev.kelton.drehzahl.obd;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.kelton.drehzahl.Prefs;
import dev.kelton.drehzahl.core.EventLog;
import dev.kelton.drehzahl.vehicle.VehicleData;

/**
 * Runs the OBD2 adapter on its own thread: connect (USB or Bluetooth), set up the ELM327,
 * find out which PIDs the car answers, then poll them as fast as the adapter allows.
 * Reconnects by itself after errors. Screens read the latest values with copyTo().
 */
public final class ObdSource implements Runnable {
    public interface Listener { void onObdStatus(); }
    public interface Callback { void done(String result); }

    public static final int OFF = 0, CONNECTING = 1, WAITING = 2, SETUP = 3, NO_CAR = 4, LIVE = 5, ERROR = 6;
    public static final String[] STATE_NAMES = { "Off", "Connecting", "Waiting", "Setting up", "Car not answering", "Live", "Error" };
    public static final String ACTION_USB_PERMISSION = "dev.kelton.drehzahl.USB_PERMISSION";

    private static final int[] FAST = { 0x0C, 0x0D, 0x0B, 0x11, 0x10, 0x04 };
    private static final int[] SLOW = { 0x05, 0x0F, 0x33, 0x42, 0x5C, 0x46, 0x5E, 0x62, 0x63 };
    private static final int[] BAUDS = { 115200, 38400, 9600, 500000 };

    private static ObdSource inst;
    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final ArrayDeque<Object[]> jobs = new ArrayDeque<>();
    private final Object wake = new Object();
    private final VehicleData snap = new VehicleData();
    private final boolean[] has = new boolean[256];
    private final PidScheduler sched = new PidScheduler();
    private String engineTx, engineRx;
    private final ArrayDeque<String> logLines = new ArrayDeque<>();
    private final SimpleDateFormat stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public volatile int state = OFF;
    public volatile String detail = "", adapter = "", linkName = "", protocol = "", vin = "", lastError = "";
    public volatile float voltage = Float.NaN, rate;
    public volatile int pidCount;

    private volatile boolean running;
    private Thread thread;
    private Link link;
    private float baro = 101f;
    private float torqueRef = Float.NaN, torquePct = Float.NaN, fuelRatePid = Float.NaN;
    private boolean receiverOn;

    public static synchronized ObdSource get(Context c) {
        if (inst == null) inst = new ObdSource(c.getApplicationContext());
        return inst;
    }

    private ObdSource(Context c) {
        ctx = c;
        snap.oilC = Float.NaN;
    }

    // ---------- control ----------

    public synchronized void start() {
        if (running) return;
        running = true;
        registerPermissionReceiver();
        thread = new Thread(this, "drehzahl-obd");
        thread.start();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        poke();
        closeLink();
        setState(OFF, "");
    }

    public void restart() {
        stop();
        Thread t = thread;
        if (t != null) {
            try { t.join(1500); } catch (InterruptedException ignored) { }
        }
        start();
    }

    public boolean running() { return running; }

    /** Retry right away (a USB adapter was plugged in, or permission was granted). */
    public void poke() {
        synchronized (wake) { wake.notifyAll(); }
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    /** Run raw commands between polls (console, trouble codes). headers = turn ATH1 on for the duration. */
    public void submit(String[] cmds, boolean headers, Callback cb) {
        if (state != LIVE && state != NO_CAR) { cb.done("Not connected to an adapter yet (" + STATE_NAMES[state] + ")."); return; }
        synchronized (jobs) { jobs.add(new Object[] { cmds, headers, cb }); }
    }

    public List<String> log() { synchronized (logLines) { return new ArrayList<>(logLines); } }

    public void copyTo(VehicleData d) {
        synchronized (snap) {
            d.copyFrom(snap);
        }
        if (state != LIVE) { d.hasEngine = false; d.rpm = 0; d.speedKmh = 0; d.hasSpeed = false; }
    }

    public boolean supports(int pid) { return has[pid & 0xFF]; }

    // ---------- worker ----------

    @Override public void run() {
        int backoff = 2000;
        log("OBD2 started");
        while (running) {
            try {
                connectOnce();
                backoff = 2000;
            } catch (WaitException e) {
                setState(WAITING, e.getMessage());
                backoff = 2500;
            } catch (Exception e) {
                if (!running) break;
                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
                log("error: " + lastError);
                setState(ERROR, lastError);
            } finally {
                closeLink();
            }
            if (!running) break;
            synchronized (wake) { try { wake.wait(backoff); } catch (InterruptedException ignored) { } }
            backoff = Math.min(backoff * 2, 10000);
        }
        log("OBD2 stopped");
    }

    private void connectOnce() throws Exception {
        setState(CONNECTING, "");
        link = openLink();
        linkName = link.describe();
        log("link: " + linkName);
        Elm327 elm = new Elm327(link, new Elm327.Log() { public void line(String s) { log(s); } });
        elm.traffic = Prefs.b(Prefs.OBD_TRAFFIC, false);

        setState(SETUP, "Talking to the adapter");
        boolean found = false;
        for (int b : (link instanceof BtLink ? new int[] { 0 } : BAUDS)) {
            if (b > 0) link.setBaud(b);
            if (elm.probe()) { found = true; if (b > 0) log("adapter answers at " + b + " baud"); break; }
        }
        if (!found) throw new IOException("The adapter did not answer. Is it an ELM327 type adapter?");
        elm.init();
        adapter = elm.version;
        log("adapter: " + adapter);
        voltage = ObdParse.volts(safe(elm, "ATRV", 1000));
        notifyListeners();

        setState(SETUP, "Searching for the car's protocol (can take 10 seconds)");
        String r = elm.cmd("0100", 15000);
        while (running && ObdParse.modeResponse(r, 1, 0) == null) {
            setState(NO_CAR, "Turn the ignition on. Adapter sees " + (Float.isNaN(voltage) ? "--" : String.format(Locale.US, "%.1f V", voltage)) + ".");
            runJobs(elm);
            synchronized (wake) { wake.wait(3000); }
            voltage = ObdParse.volts(safe(elm, "ATRV", 1000));
            r = elm.cmd("0100", 15000);
        }
        if (!running) return;

        java.util.Arrays.fill(has, false);
        engineTx = null;
        engineRx = null;
        boolean more = ObdParse.supported(r, 0x00, has);
        for (int base = 0x20; more && base <= 0xA0; base += 0x20) more = ObdParse.supported(safe(elm, String.format(Locale.US, "01%02X", base), 2000), base, has);
        int n = 0;
        for (boolean b : has) if (b) n++;
        pidCount = n;
        protocol = ObdParse.protocolName(safe(elm, "ATDPN", 1000));
        String v = ObdParse.vin(safe(elm, "0902", 4000));
        vin = v == null ? "" : v;
        log("protocol " + protocol + ", " + n + " PIDs, VIN " + (vin.isEmpty() ? "not reported" : vin));
        String t = safe(elm, "010D1", 1000);
        elm.countSuffix = ObdParse.modeResponse(t, 1, 0x0D) != null;
        sched.reset(has, 0x0C, 0x0D);
        // The adapter returns as soon as the one expected reply arrives, so a patient timeout costs nothing
        // and stops "NO DATA" when the engine computer is slow to answer (it is busiest under boost).
        if (elm.countSuffix) { safe(elm, "ATAT0", 1000); safe(elm, "ATST32", 1000); }
        else safe(elm, "ATAT1", 1000);
        if (protocol.contains("CAN 11")) directToEngine(elm);
        EventLog.add("obd: live, " + protocol.toLowerCase(Locale.US) + ", " + n + " pids");
        setState(LIVE, "");
        poll(elm);
    }

    /** Sends readings to the engine computer only (for example 7E0) so the gearbox and other modules stay quiet. */
    private void directToEngine(Elm327 elm) {
        safe(elm, "ATH1", 1000);
        String rx = ObdParse.engineHeader(safe(elm, "010C", 2000));
        safe(elm, "ATH0", 1000);
        if (rx == null) { log("could not tell which module is the engine, asking all modules"); return; }
        String tx = ObdParse.requestHeader(rx);
        safe(elm, "ATSH" + tx, 1000);
        safe(elm, "ATCRA" + rx, 1000);
        String test = safe(elm, "010C" + (elm.countSuffix ? "1" : ""), 1500);
        if (ObdParse.modeResponse(test, 1, 0x0C) != null) {
            engineTx = tx;
            engineRx = rx;
            log("asking the engine directly (" + tx + ", answers from " + rx + ")");
        } else {
            toAllModules(elm);
            log("direct engine requests got no answer, asking all modules");
        }
    }

    private void toAllModules(Elm327 elm) {
        safe(elm, "ATSH7DF", 1000);
        safe(elm, "ATAR", 1000);
    }

    private void backToEngine(Elm327 elm) {
        if (engineTx == null) return;
        safe(elm, "ATSH" + engineTx, 1000);
        safe(elm, "ATCRA" + engineRx, 1000);
    }

    private void poll(Elm327 elm) throws Exception {
        int slowIdx = 0, frames = 0, errors = 0;
        long rateStart = SystemClock.elapsedRealtime(), lastVolt = 0;
        while (running) {
            runJobs(elm);
            long now = SystemClock.elapsedRealtime();
            for (int pid : FAST) {
                if (!sched.due(pid, now)) continue;
                if (!read(elm, pid)) errors++; else { errors = 0; frames++; }
            }
            now = SystemClock.elapsedRealtime();
            for (int k = 0; k < SLOW.length; k++) {
                int pid = SLOW[(slowIdx + k) % SLOW.length];
                if (sched.due(pid, now)) { slowIdx = (slowIdx + k + 1) % SLOW.length; if (read(elm, pid)) frames++; break; }
            }
            now = SystemClock.elapsedRealtime();
            if (!has[0x42] && now - lastVolt > 5000) {
                lastVolt = now;
                float vv = ObdParse.volts(safe(elm, "ATRV", 800));
                if (!Float.isNaN(vv)) { voltage = vv; synchronized (snap) { snap.batteryV = vv; } }
            }
            derive();
            if (now - rateStart >= 2000) {
                rate = frames * 1000f / (now - rateStart);
                frames = 0;
                rateStart = now;
                notifyListeners();
            }
            if (errors >= 12) throw new IOException("The car stopped answering (ignition off?)");
        }
    }

    private boolean read(Elm327 elm, int pid) throws IOException {
        String r = elm.cmd(String.format(Locale.US, "01%02X%s", pid, elm.countSuffix ? "1" : ""), 1500);
        int[] d = ObdParse.modeResponse(r, 1, pid);
        if (d == null) {
            long rest = sched.miss(pid, SystemClock.elapsedRealtime());
            if (rest > 0) {
                markStale(pid);
                log(String.format(Locale.US, "PID %02X quiet, last reply \"%s\", asking again in %d s", pid, r.replace('\n', ' ').trim(), rest / 1000));
            }
            return !ObdParse.noCar(r);
        }
        if (sched.hit(pid)) log(String.format(Locale.US, "PID %02X answering again", pid));
        decode(pid, d);
        return true;
    }

    /** Shows "--" for a reading the car stopped answering, instead of freezing on its last value. */
    private void markStale(int pid) {
        synchronized (snap) {
            switch (pid) {
                case 0x0B: snap.boostKpa = Float.NaN; break;
                case 0x11: snap.throttle = Float.NaN; break;
                case 0x10: snap.mafGs = Float.NaN; break;
                case 0x04: snap.load = Float.NaN; break;
                case 0x05: snap.coolantC = Float.NaN; break;
                case 0x0F: snap.iatC = Float.NaN; break;
                case 0x5C: snap.oilC = Float.NaN; break;
                case 0x46: snap.ambientC = Float.NaN; break;
                case 0x5E: fuelRatePid = Float.NaN; break;
                case 0x62: torquePct = Float.NaN; break;
                case 0x63: torqueRef = Float.NaN; break;
                default: break;
            }
        }
    }

    private void decode(int pid, int[] d) {
        int a = d.length > 0 ? d[0] : 0, b = d.length > 1 ? d[1] : 0;
        synchronized (snap) {
            switch (pid) {
                case 0x0C: snap.rpm = ((a << 8) | b) / 4f; break;
                case 0x0D: snap.speedKmh = a; snap.hasSpeed = true; break;
                case 0x0B: snap.boostKpa = a - baro; break;
                case 0x11: snap.throttle = a / 255f; break;
                case 0x10: snap.mafGs = ((a << 8) | b) / 100f; break;
                case 0x04: snap.load = a / 255f; break;
                case 0x05: snap.coolantC = a - 40; break;
                case 0x0F: snap.iatC = a - 40; break;
                case 0x33: baro = a > 50 ? a : 101f; break;
                case 0x42: snap.batteryV = ((a << 8) | b) / 1000f; voltage = snap.batteryV; break;
                case 0x5C: snap.oilC = a - 40; break;
                case 0x46: snap.ambientC = a - 40; break;
                case 0x5E: fuelRatePid = ((a << 8) | b) / 20f; break;
                case 0x62: torquePct = a - 125; break;
                case 0x63: torqueRef = (a << 8) | b; break;
                default: break;
            }
        }
    }

    /** Values the car does not report directly: fuel flow, power and torque (estimated from airflow when needed). */
    private void derive() {
        synchronized (snap) {
            snap.hasEngine = true;
            snap.fuelLph = !Float.isNaN(fuelRatePid) ? fuelRatePid : snap.mafGs * 3600f / 14.7f / 745f;
            if (!Float.isNaN(torquePct) && !Float.isNaN(torqueRef) && snap.rpm > 300) {
                snap.torqueNm = Math.max(0f, torqueRef * torquePct / 100f);
                snap.powerKw = snap.torqueNm * snap.rpm / 9549f;
                snap.powerEstimated = false;
            } else {
                float k = snap.load > 0.7f ? 1.07f : 0.82f;
                snap.powerKw = snap.throttle < 0.03f ? 0f : Math.max(0f, snap.mafGs * k - 4f);
                snap.torqueNm = snap.rpm > 500 ? snap.powerKw * 9549f / snap.rpm : 0f;
                snap.powerEstimated = true;
            }
            snap.gear = 0;
        }
    }

    private void runJobs(Elm327 elm) {
        while (true) {
            Object[] job;
            synchronized (jobs) { job = jobs.poll(); }
            if (job == null) return;
            String[] cmds = (String[]) job[0];
            boolean headers = (Boolean) job[1];
            final Callback cb = (Callback) job[2];
            final StringBuilder out = new StringBuilder();
            toAllModules(elm);
            try {
                if (headers) elm.cmd("ATH1", 1000);
                for (String c : cmds) {
                    String r = elm.cmd(c, 6000);
                    out.append("> ").append(c).append('\n').append(r).append('\n');
                    log("console > " + c + "  " + r.replace('\n', ' '));
                }
            } catch (IOException e) {
                out.append("error: ").append(e.getMessage()).append('\n');
            } finally {
                if (headers) safe(elm, "ATH0", 1000);
                backToEngine(elm);
            }
            main.post(new Runnable() { public void run() { cb.done(out.toString()); } });
        }
    }

    private static String safe(Elm327 elm, String c, int t) {
        try { return elm.cmd(c, t); } catch (IOException e) { return ""; }
    }

    // ---------- links ----------

    static final class WaitException extends IOException {
        WaitException(String m) { super(m); }
    }

    private Link openLink() throws IOException {
        if (Prefs.i(Prefs.OBD_LINK, 0) == 0) {
            UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
            if (um == null) throw new WaitException("This tablet has no USB host support");
            UsbDevice found = null;
            int best = 0;
            for (UsbDevice d : um.getDeviceList().values()) {
                int r = UsbSerial.rank(d);
                if (r > best) { best = r; found = d; }
            }
            if (found == null) throw new WaitException("Plug the USB adapter into the tablet (USB-C OTG adapter) and into the car");
            if (!um.hasPermission(found)) {
                Intent i = new Intent(ACTION_USB_PERMISSION).setPackage(ctx.getPackageName());
                int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
                um.requestPermission(found, PendingIntent.getBroadcast(ctx, 7, i, flags));
                throw new WaitException("Tap OK on the popup to let Drehzahl use the USB adapter");
            }
            return UsbSerial.open(um, found, BAUDS[0]);
        }
        String addr = Prefs.s(Prefs.OBD_BT_ADDR, "");
        if (addr.isEmpty()) throw new WaitException("Pick your Bluetooth adapter in Car > Data source");
        if (Build.VERSION.SDK_INT >= 31 && ctx.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
            throw new WaitException("Bluetooth permission missing. Run Install in the PC toolkit.");
        }
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ba = bm == null ? null : bm.getAdapter();
        if (ba == null || !ba.isEnabled()) throw new WaitException("Turn Bluetooth on");
        return new BtLink(ba, addr);
    }

    private void registerPermissionReceiver() {
        if (receiverOn) return;
        receiverOn = true;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean ok = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                log("USB permission " + (ok ? "granted" : "denied"));
                if (ok) poke();
            }
        };
        IntentFilter f = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else ctx.registerReceiver(r, f);
    }

    private void closeLink() {
        Link l = link;
        link = null;
        if (l != null) l.close();
    }

    // ---------- status and log ----------

    private void setState(int s, String d) {
        boolean changed = s != state || !d.equals(detail);
        state = s;
        detail = d;
        if (changed) {
            log("state: " + STATE_NAMES[s] + (d.isEmpty() ? "" : " (" + d + ")"));
            notifyListeners();
        }
    }

    private void notifyListeners() {
        main.post(new Runnable() { public void run() { for (Listener l : new ArrayList<>(listeners)) l.onObdStatus(); } });
    }

    private void log(String s) {
        String line = stamp.format(new Date()) + "  " + s;
        synchronized (logLines) {
            logLines.add(line);
            while (logLines.size() > 300) logLines.poll();
        }
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        File f = new File(dir, "obd-log.txt");
        try {
            if (f.length() > 2000000) f.delete();
            FileWriter w = new FileWriter(f, true);
            w.write(line + "\n");
            w.close();
        } catch (IOException ignored) { }
    }
}
