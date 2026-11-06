package dev.kelton.drehzahl.obd;

import java.util.List;
import java.util.Locale;

/**
 * Picks the two Bluetooth LE characteristics an OBD2 adapter talks through: one the tablet writes
 * commands to and one that notifies replies. Known adapter layouts come first, then any service
 * that has both. Plain Java so the PC tests can check it.
 */
final class GattPick {
    static final int WRITE_NO_RESPONSE = 0x04, WRITE = 0x08, NOTIFY = 0x10, INDICATE = 0x20;

    static final class Ch {
        final String service, uuid;
        final int props;
        Ch(String service, String uuid, int props) {
            this.service = service.toLowerCase(Locale.US);
            this.uuid = uuid.toLowerCase(Locale.US);
            this.props = props;
        }
        boolean canWrite() { return (props & (WRITE | WRITE_NO_RESPONSE)) != 0; }
        boolean canNotify() { return (props & (NOTIFY | INDICATE)) != 0; }
    }

    private static String sig(String shortId) { return "0000" + shortId + "-0000-1000-8000-00805f9b34fb"; }

    /** service, notify characteristic, write characteristic. */
    static final String[][] KNOWN = {
            { "e7810a71-73ae-499d-8c15-faa9aef0c3f2", "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f", "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f" }, // Vgate iCar Pro, vLinker
            { sig("fff0"), sig("fff1"), sig("fff2") },   // Veepeak, OBDLink and many others
            { sig("ffe0"), sig("ffe1"), sig("ffe1") },   // HM-10 style modules in cheap adapters
            { sig("18f0"), sig("2af0"), sig("2af1") },   // second service on some Vgate adapters
    };

    /** Standard Bluetooth services that never carry the adapter's serial data. */
    private static boolean standard(String service) {
        if (!service.endsWith("-0000-1000-8000-00805f9b34fb") || !service.startsWith("0000")) return false;
        String id = service.substring(4, 8);
        return id.startsWith("180") || id.startsWith("181") || id.equals("1820") || id.equals("fe59") || id.equals("fe2c");
    }

    /** Returns { notify index, write index } into chars, or null when nothing fits. */
    static int[] pick(List<Ch> chars) {
        for (String[] k : KNOWN) {
            int n = -1, w = -1;
            for (int i = 0; i < chars.size(); i++) {
                Ch c = chars.get(i);
                if (!c.service.equals(k[0])) continue;
                if (n < 0 && c.uuid.equals(k[1]) && c.canNotify()) n = i;
                if (w < 0 && c.uuid.equals(k[2]) && c.canWrite()) w = i;
            }
            if (n >= 0 && w >= 0) return new int[] { n, w };
        }
        for (int i = 0; i < chars.size(); i++) {
            Ch c = chars.get(i);
            if (standard(c.service) || !c.canNotify() || !c.canWrite()) continue;
            return new int[] { i, i };
        }
        for (int i = 0; i < chars.size(); i++) {
            Ch n = chars.get(i);
            if (standard(n.service) || !n.canNotify()) continue;
            for (int j = 0; j < chars.size(); j++) {
                Ch w = chars.get(j);
                if (j != i && w.service.equals(n.service) && w.canWrite()) return new int[] { i, j };
            }
        }
        return null;
    }
}
