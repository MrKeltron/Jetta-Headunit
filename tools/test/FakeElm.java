import dev.kelton.drehzahl.obd.Link;
import java.io.IOException;
import java.util.*;
/** Pretends to be a vLinker on a 2015 EA888: echo on until ATE0, SEARCHING on first 0100, headers when ATH1. */
public class FakeElm implements Link {
    private final StringBuilder in = new StringBuilder();
    private final ArrayDeque<Byte> out = new ArrayDeque<>();
    boolean echo = true, headers = false, searched = false;
    int rpm = 3000;
    public void write(byte[] d) {
        for (byte b : d) {
            char c = (char) b;
            if (c == '\r') { handle(in.toString().trim().toUpperCase()); in.setLength(0); }
            else in.append(c);
        }
    }
    private void reply(String s) { for (char c : s.toCharArray()) out.add((byte) c); }
    private void handle(String cmd) {
        if (echo) reply(cmd + "\r");
        String r;
        switch (cmd) {
            case "ATZ": r = "\r\rELM327 v2.2"; echo = true; break;
            case "ATE0": echo = false; r = "OK"; break;
            case "ATI": r = "ELM327 v2.2"; break;
            case "AT@1": r = "vLinker FS"; break;
            case "ATRV": r = "12.4V"; break;
            case "ATH1": headers = true; r = "OK"; break;
            case "ATH0": headers = false; r = "OK"; break;
            case "ATDPN": r = "A6"; break;
            case "0100": r = (searched ? "" : "SEARCHING...\r") + "4100BE3FA813"; searched = true; break;
            case "0120": r = "4120A007B011"; break;
            case "0140": r = "4140FED00400"; break;
            case "0902": r = "014\r0:490201335657\r1:345437414A3646\r2:4D303030303030"; break;
            case "010D1": r = "410D32"; break;
            case "010C1": rpm += 50; r = String.format("410C%04X", rpm * 4); break;
            case "010B1": r = "410BC8"; break;
            case "01331": r = "413364"; break;
            case "03": r = headers ? "7E8100A4304010301\r7E821042000000000\r7E906430107710000" : "4304010301"; break;
            case "07": r = headers ? "7E8024300" : "4300"; break;
            case "0A": r = "NO DATA"; break;
            default:
                if (cmd.startsWith("AT")) r = "OK";
                else if (cmd.startsWith("01")) r = "NO DATA";
                else r = "?";
        }
        reply(r + "\r\r>");
    }
    public int read(byte[] buf, int timeoutMs) {
        int n = 0;
        while (n < buf.length && !out.isEmpty()) buf[n++] = out.poll();
        return n;
    }
    public void setBaud(int b) { }
    public String describe() { return "fake vLinker"; }
    public void close() { }
}
