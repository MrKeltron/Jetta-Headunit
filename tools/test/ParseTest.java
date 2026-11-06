import dev.kelton.drehzahl.obd.ObdParse;
import java.util.*;
public class ParseTest {
    static int fails = 0;
    static void eq(String name, Object got, Object want) {
        boolean ok = Objects.equals(String.valueOf(got), String.valueOf(want));
        if (!ok) fails++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  got=" + got + (ok ? "" : "  want=" + want));
    }
    public static void main(String[] a) {
        int[] d = ObdParse.modeResponse("410C1AF8", 1, 0x0C);
        eq("rpm", ((d[0] * 256) + d[1]) / 4, 1726);
        d = ObdParse.modeResponse("SEARCHING...\r410D32\r", 1, 0x0D);
        eq("speed after searching", d[0], 50);
        d = ObdParse.modeResponse("410B98\n410B97", 1, 0x0B);
        eq("map two ECUs takes first", d[0], 0x98);
        eq("no data", ObdParse.modeResponse("NO DATA", 1, 0x0C), null);
        eq("wrong pid", ObdParse.modeResponse("410D32", 1, 0x0C), null);
        boolean[] sup = new boolean[256];
        boolean more = ObdParse.supported("4100BE3EB813", 0x00, sup);
        eq("pid 0C supported", sup[0x0C], true);
        eq("pid 02 not supported", sup[0x02], false);
        eq("pid 01 supported", sup[0x01], true);
        eq("next range", more, true);
        // VIN multi-frame, headers off
        String vinResp = "014\r0:490201335657\r1:345437414A3646\r2:4D303030303030\r";
        eq("vin", ObdParse.vin(vinResp), "3VW4T7AJ6FM000000");
        eq("vin spaces", ObdParse.vin("014\r0: 49 02 01 33 56 57\r1: 34 54 37 41 4A 36 46\r2: 4D 30 30 30 30 30 30"), "3VW4T7AJ6FM000000");
        // DTCs headers on, single frame: engine 2 codes, transmission 1
        String dtcResp = "7E8064302010304200\r7E904430107710000";
        List<String> codes = ObdParse.dtcs(dtcResp, 3);
        eq("dtc list", codes, "[P0103|Engine, P0420|Engine, P0771|Transmission]");
        // multi frame mode 03 with headers
        String mf = "7E8100E4306010101020103\r7E8210104010501060000";
        eq("dtc multi frame", ObdParse.dtcs(mf, 3), "[P0101, P0102, P0103, P0104, P0105, P0106]".replace(", ", "|Engine, ").replace("]", "|Engine]"));
        eq("no codes", ObdParse.dtcs("7E8024300", 3), "[]");
        eq("dtc letters", ObdParse.dtc(0xC1, 0x23) + " " + ObdParse.dtc(0x41, 0x00) + " " + ObdParse.dtc(0x81, 0x55), "U0123 C0100 B0155");
        eq("volts", ObdParse.volts("12.6V"), 12.6f);
        eq("protocol", ObdParse.protocolName("A6"), "ISO 15765-4 CAN 11 bit 500k");
        eq("no car", ObdParse.noCar("SEARCHING...\nUNABLE TO CONNECT"), true);
        eq("car ok", ObdParse.noCar("410C1AF8"), false);
        eq("engine header", ObdParse.engineHeader("7E804410C1AF8"), "7E8");
        eq("engine header after gearbox refusal", ObdParse.engineHeader("7E9037F0112\r7E804410C1AF8"), "7E8");
        eq("engine header with spaces", ObdParse.engineHeader("7E8 04 41 0C 1A F8"), "7E8");
        eq("engine header without headers", ObdParse.engineHeader("410C1AF8"), null);
        eq("engine header no data", ObdParse.engineHeader("NO DATA"), null);
        eq("request header", ObdParse.requestHeader("7E8") + " " + ObdParse.requestHeader("7E9"), "7E0 7E1");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
    }
}
