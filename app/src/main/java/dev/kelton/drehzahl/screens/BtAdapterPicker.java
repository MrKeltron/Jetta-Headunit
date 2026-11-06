package dev.kelton.drehzahl.screens;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.kelton.drehzahl.ui.W;

/**
 * Lists OBD2 adapters: paired classic Bluetooth devices first, then Bluetooth LE adapters found by a
 * live search (those work without pairing). Dual-mode adapters show up in both; the paired entry is faster to connect.
 */
final class BtAdapterPicker {
    interface Picked { void on(String address, String label, boolean le); }

    private static final long SCAN_MS = 15000;
    private static final Pattern LIKELY = Pattern.compile("(?i).*(obd|elm|vlink|veepeak|icar|vgate|obdlink|carista|konnwei|v-?link|scan|vlinker|obdii).*");

    private BtAdapterPicker() { }

    static void show(final Activity host, final Picked cb) {
        BluetoothManager bm = (BluetoothManager) host.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter ba = bm == null ? null : bm.getAdapter();
        if (ba == null) { W.toast(host, "This tablet has no Bluetooth"); return; }
        if (!askPermissions(host)) return;
        boolean on;
        try { on = ba.isEnabled(); } catch (SecurityException e) { on = false; }
        if (!on) { W.toast(host, "Turn Bluetooth on first"); return; }

        final List<String> labels = new ArrayList<>();
        final List<String> addrs = new ArrayList<>();
        final List<Boolean> le = new ArrayList<>();
        try {
            for (BluetoothDevice d : ba.getBondedDevices()) {
                String n = d.getName() == null ? "Unnamed" : d.getName();
                boolean leOnly = d.getType() == BluetoothDevice.DEVICE_TYPE_LE;
                labels.add((leOnly ? "Bluetooth LE: " : "Paired: ") + n + "  (" + d.getAddress() + ")");
                addrs.add(d.getAddress());
                le.add(leOnly);
            }
        } catch (SecurityException e) { W.toast(host, "Bluetooth permission missing"); return; }
        final int pairedCount = labels.size();

        final AlertDialog.Builder builder = new AlertDialog.Builder(host, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(builder.getContext(), android.R.layout.select_dialog_item, labels);
        final Handler h = new Handler(Looper.getMainLooper());
        final BluetoothLeScanner scanner = ba.getBluetoothLeScanner();
        final boolean[] scanning = { false };
        final int[] likelyEnd = { pairedCount };

        final ScanCallback scan = new ScanCallback() {
            @Override public void onScanResult(int type, final ScanResult r) {
                h.post(new Runnable() {
                    public void run() {
                        String addr = r.getDevice().getAddress();
                        if (addrs.contains(addr)) return;
                        String n = r.getScanRecord() == null ? null : r.getScanRecord().getDeviceName();
                        if (n == null) {
                            try { n = r.getDevice().getName(); } catch (SecurityException ignored) { }
                        }
                        if (n == null || n.trim().isEmpty()) return;
                        String label = "Bluetooth LE: " + n + "  (" + String.format(Locale.US, "%d dBm", r.getRssi()) + ")";
                        int at = LIKELY.matcher(n).matches() ? likelyEnd[0]++ : labels.size();
                        labels.add(at, label);
                        addrs.add(at, addr);
                        le.add(at, true);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        };

        final AlertDialog dlg = builder
                .setTitle("Bluetooth adapter (searching)")
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w < 0 || w >= addrs.size()) return;
                        cb.on(addrs.get(w), labels.get(w), le.get(w));
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        final Runnable stop = new Runnable() {
            public void run() {
                if (!scanning[0]) return;
                scanning[0] = false;
                try { if (scanner != null) scanner.stopScan(scan); } catch (RuntimeException ignored) { }
                if (dlg.isShowing()) {
                    dlg.setTitle("Bluetooth adapter");
                    if (labels.size() == pairedCount) {
                        W.toast(host, Build.VERSION.SDK_INT < 31
                                ? "No Bluetooth LE adapters found. Android " + Build.VERSION.RELEASE + " only finds them while Location is on."
                                : "No Bluetooth LE adapters found. Is the ignition on?");
                    }
                }
            }
        };
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) { h.removeCallbacks(stop); stop.run(); }
        });
        dlg.show();

        if (scanner != null) {
            try {
                scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scan);
                scanning[0] = true;
                h.postDelayed(stop, SCAN_MS);
            } catch (RuntimeException e) {
                dlg.setTitle("Bluetooth adapter");
                W.toast(host, "Bluetooth LE search is not allowed: " + e.getMessage());
            }
        }
    }

    /** Asks Android for the Bluetooth (and on older versions, location) permissions a search needs. */
    private static boolean askPermissions(Activity host) {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (host.checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) need.add("android.permission.BLUETOOTH_SCAN");
            if (host.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) need.add("android.permission.BLUETOOTH_CONNECT");
        } else if (host.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.ACCESS_FINE_LOCATION");
        }
        if (need.isEmpty()) return true;
        host.requestPermissions(need.toArray(new String[0]), 61);
        W.toast(host, "Allow the permission, then tap the adapter row again");
        return false;
    }
}
