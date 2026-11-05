package dev.kelton.drehzahl.obd;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.util.Locale;

/**
 * USB serial drivers written against Android's USB host API, so no driver library is needed.
 * FTDI (vLinker FS USB, most good adapters), CH340/CH341 (cheap clones), CP210x, and standard CDC-ACM.
 */
public abstract class UsbSerial implements Link {
    public static final int VID_FTDI = 0x0403, VID_CH34X = 0x1A86, VID_CP210X = 0x10C4, VID_PROLIFIC = 0x067B;
    static final int TIMEOUT = 500;

    protected final UsbDevice device;
    protected UsbDeviceConnection conn;
    protected UsbInterface dataIface;
    protected UsbEndpoint in, out;
    private final byte[] rx = new byte[512];

    UsbSerial(UsbDevice d) { device = d; }

    /** True when this looks like a serial adapter Drehzahl can drive. */
    public static boolean supported(UsbDevice d) {
        int v = d.getVendorId();
        if (v == VID_FTDI || v == VID_CH34X || v == VID_CP210X) return true;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            if (d.getInterface(i).getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) return true;
        }
        return false;
    }

    public static UsbSerial open(UsbManager um, UsbDevice d, int baud) throws IOException {
        UsbSerial s;
        switch (d.getVendorId()) {
            case VID_FTDI: s = new Ftdi(d); break;
            case VID_CH34X: s = new Ch34x(d); break;
            case VID_CP210X: s = new Cp210x(d); break;
            default: s = new CdcAcm(d);
        }
        s.conn = um.openDevice(d);
        if (s.conn == null) throw new IOException("Android did not let Drehzahl open the USB adapter");
        try {
            s.claim();
            s.init(baud);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        return s;
    }

    /** Claims the interface with two bulk endpoints. */
    protected void claim() throws IOException {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface ui = device.getInterface(i);
            UsbEndpoint bi = null, bo = null;
            for (int e = 0; e < ui.getEndpointCount(); e++) {
                UsbEndpoint ep = ui.getEndpoint(e);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) bi = ep; else bo = ep;
            }
            if (bi != null && bo != null) {
                if (!conn.claimInterface(ui, true)) throw new IOException("Could not claim the USB interface");
                dataIface = ui; in = bi; out = bo;
                return;
            }
        }
        throw new IOException("No bulk endpoints on this USB device");
    }

    protected abstract void init(int baud) throws IOException;

    protected void ctrl(int type, int req, int value, int index, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int r = conn.controlTransfer(type, req, value, index, data, len, TIMEOUT);
        if (r < 0) throw new IOException(String.format(Locale.US, "USB control 0x%02X/0x%02X failed", type, req));
    }

    @Override public void write(byte[] data) throws IOException {
        int off = 0;
        while (off < data.length) {
            int n = Math.min(data.length - off, out.getMaxPacketSize());
            byte[] chunk = new byte[n];
            System.arraycopy(data, off, chunk, 0, n);
            int r = conn.bulkTransfer(out, chunk, n, TIMEOUT);
            if (r < 0) throw new IOException("USB write failed (adapter unplugged?)");
            off += r;
        }
    }

    @Override public int read(byte[] buf, int timeoutMs) throws IOException {
        int n = conn.bulkTransfer(in, rx, Math.min(rx.length, buf.length), Math.max(1, timeoutMs));
        if (n < 0) return 0;
        return unwrap(rx, n, buf);
    }

    /** Copies payload bytes out of a raw bulk read. FTDI overrides this to drop its status bytes. */
    protected int unwrap(byte[] raw, int n, byte[] buf) {
        System.arraycopy(raw, 0, buf, 0, n);
        return n;
    }

    @Override public String describe() {
        return String.format(Locale.US, "%s USB %04X:%04X", getClass().getSimpleName(), device.getVendorId(), device.getProductId());
    }

    @Override public void close() {
        try { if (dataIface != null) conn.releaseInterface(dataIface); } catch (RuntimeException ignored) { }
        try { if (conn != null) conn.close(); } catch (RuntimeException ignored) { }
        conn = null;
    }

    // ---------------- FTDI ----------------
    static final class Ftdi extends UsbSerial {
        Ftdi(UsbDevice d) { super(d); }

        @Override protected void init(int baud) throws IOException {
            ctrl(0x40, 0, 0, 1, null);          // reset
            ctrl(0x40, 1, 0x0303, 1, null);     // DTR and RTS on
            ctrl(0x40, 2, 0, 1, null);          // no flow control
            setBaud(baud);
            ctrl(0x40, 4, 0x0008, 1, null);     // 8 data bits, no parity, 1 stop
            ctrl(0x40, 9, 2, 1, null);          // 2 ms latency timer, default 16
        }

        @Override public void setBaud(int baud) throws IOException {
            int divisor = (24000000 << 1) / baud;
            divisor = (divisor + 1) >> 1;
            int sub = divisor & 0x07;
            divisor >>= 3;
            int value = divisor, index = 0;
            switch (sub) {
                case 4: value |= 0x4000; break;
                case 2: value |= 0x8000; break;
                case 1: value |= 0xC000; break;
                case 3: index |= 1; break;
                case 5: value |= 0x4000; index |= 1; break;
                case 6: value |= 0x8000; index |= 1; break;
                case 7: value |= 0xC000; index |= 1; break;
                default: break;
            }
            ctrl(0x40, 3, value, index, null);
        }

        /** Every 64-byte packet from an FTDI chip starts with 2 modem status bytes. */
        @Override protected int unwrap(byte[] raw, int n, byte[] buf) {
            int packet = Math.max(64, in.getMaxPacketSize());
            int o = 0;
            for (int p = 0; p < n; p += packet) {
                int end = Math.min(p + packet, n);
                for (int i = p + 2; i < end && o < buf.length; i++) buf[o++] = raw[i];
            }
            return o;
        }
    }

    // ---------------- CH340 / CH341 ----------------
    static final class Ch34x extends UsbSerial {
        Ch34x(UsbDevice d) { super(d); }

        private void out(int req, int value, int index) throws IOException { ctrl(0x40, req, value, index, null); }

        private void in(int req, int value, int index, int len) throws IOException {
            byte[] b = new byte[len];
            if (conn.controlTransfer(0xC0, req, value, index, b, len, TIMEOUT) < 0) throw new IOException("CH34x init read failed");
        }

        @Override protected void init(int baud) throws IOException {
            in(0x5F, 0, 0, 2);
            out(0xA1, 0, 0);
            setBaud(baud);
            in(0x95, 0x2518, 0, 2);
            out(0x9A, 0x2518, 0x00C3);          // enable RX, TX, 8 data bits
            in(0x95, 0x0706, 0, 2);
            out(0xA1, 0x501F, 0xD90A);
            setBaud(baud);
            out(0xA4, ~(0x20 | 0x40) & 0xFFFF, 0); // DTR and RTS on (active low)
            in(0x95, 0x0706, 0, 2);
        }

        @Override public void setBaud(int baud) throws IOException {
            long factor, divisor;
            if (baud == 921600) {
                divisor = 7; factor = 0xF300;
            } else {
                factor = 1532620800L / baud;
                divisor = 3;
                while (factor > 0xFFF0 && divisor > 0) { factor >>= 3; divisor--; }
                if (factor > 0xFFF0) throw new IOException("Baud rate not supported by CH34x: " + baud);
                factor = 0x10000 - factor;
            }
            divisor |= 0x0080;
            out(0x9A, 0x1312, (int) ((factor & 0xFF00) | divisor));
            out(0x9A, 0x0F2C, (int) (factor & 0xFF));
        }
    }

    // ---------------- Silicon Labs CP210x ----------------
    static final class Cp210x extends UsbSerial {
        Cp210x(UsbDevice d) { super(d); }

        private int idx() { return dataIface.getId(); }

        @Override protected void init(int baud) throws IOException {
            ctrl(0x41, 0x00, 0x0001, idx(), null);   // enable UART
            ctrl(0x41, 0x07, 0x0303, idx(), null);   // DTR and RTS on
            ctrl(0x41, 0x03, 0x0800, idx(), null);   // 8N1
            setBaud(baud);
        }

        @Override public void setBaud(int baud) throws IOException {
            byte[] d = { (byte) baud, (byte) (baud >> 8), (byte) (baud >> 16), (byte) (baud >> 24) };
            ctrl(0x41, 0x1E, 0, idx(), d);
        }
    }

    // ---------------- CDC-ACM (STN chips, many newer adapters) ----------------
    static final class CdcAcm extends UsbSerial {
        private int controlIndex;

        CdcAcm(UsbDevice d) { super(d); }

        @Override protected void claim() throws IOException {
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface ui = device.getInterface(i);
                if (ui.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                    controlIndex = ui.getId();
                    conn.claimInterface(ui, true);
                }
            }
            super.claim();
        }

        @Override protected void init(int baud) throws IOException {
            setBaud(baud);
            ctrl(0x21, 0x22, 0x0003, controlIndex, null);
        }

        @Override public void setBaud(int baud) throws IOException {
            byte[] lc = { (byte) baud, (byte) (baud >> 8), (byte) (baud >> 16), (byte) (baud >> 24), 0, 0, 8 };
            ctrl(0x21, 0x20, 0, controlIndex, lc);
        }
    }
}
