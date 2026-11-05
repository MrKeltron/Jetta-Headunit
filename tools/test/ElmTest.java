import dev.kelton.drehzahl.obd.*;
import java.util.*;
public class ElmTest {
    public static void main(String[] a) throws Exception {
        FakeElm f = new FakeElm();
        Elm327 e = new Elm327(f, new Elm327.Log() { public void line(String s) { System.out.println("  log " + s); } });
        System.out.println("probe: " + e.probe());
        e.init();
        System.out.println("version: " + e.version);
        String r = e.cmd("0100", 15000);
        boolean[] has = new boolean[256];
        boolean more = ObdParse.supported(r, 0, has);
        for (int base = 0x20; more && base <= 0xA0; base += 0x20) more = ObdParse.supported(e.cmd(String.format("01%02X", base), 2000), base, has);
        StringBuilder pids = new StringBuilder();
        for (int i = 0; i < 256; i++) if (has[i]) pids.append(String.format("%02X ", i));
        System.out.println("supported: " + pids);
        System.out.println("has 0C 0D 0B 5C 46: " + has[0x0C] + " " + has[0x0D] + " " + has[0x0B] + " " + has[0x5C] + " " + has[0x46]);
        System.out.println("protocol: " + ObdParse.protocolName(e.cmd("ATDPN", 1000)));
        System.out.println("vin: " + ObdParse.vin(e.cmd("0902", 4000)));
        e.countSuffix = ObdParse.modeResponse(e.cmd("010D1", 1000), 1, 0x0D) != null;
        System.out.println("count suffix ok: " + e.countSuffix);
        int[] d = ObdParse.modeResponse(e.cmd("010C1", 1000), 1, 0x0C);
        System.out.println("rpm: " + ((d[0] << 8) | d[1]) / 4f);
        int baro = ObdParse.modeResponse(e.cmd("01331", 1000), 1, 0x33)[0];
        int map = ObdParse.modeResponse(e.cmd("010B1", 1000), 1, 0x0B)[0];
        System.out.println("boost kPa: " + (map - baro) + "  psi: " + String.format("%.1f", (map - baro) * 0.145038));
        System.out.println("missing pid: " + ObdParse.modeResponse(e.cmd("015C1", 1000), 1, 0x5C));
        e.cmd("ATH1", 1000);
        String c3 = e.cmd("03", 6000), c7 = e.cmd("07", 6000), ca = e.cmd("0A", 6000);
        e.cmd("ATH0", 1000);
        System.out.println("stored: " + ObdParse.dtcs(c3, 3) + "  pending: " + ObdParse.dtcs(c7, 7) + "  permanent: " + ObdParse.dtcs(ca, 0x0A));
        long t0 = System.nanoTime();
        try { new Elm327(new Link() {
            public void write(byte[] x) {} public int read(byte[] b, int t) { try { Thread.sleep(t); } catch (Exception ex) {} return 0; }
            public void setBaud(int b) {} public String describe() { return "dead"; } public void close() {} }, s -> {}).cmd("ATI", 300);
            System.out.println("dead link: no exception (BAD)");
        } catch (java.io.IOException ex) { System.out.println("dead link throws after " + (System.nanoTime() - t0) / 1000000 + " ms: " + ex.getMessage()); }
    }
}
