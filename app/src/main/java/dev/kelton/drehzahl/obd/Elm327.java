package dev.kelton.drehzahl.obd;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

/** Talks the ELM327 text protocol over any Link: send a command, read until the '>' prompt. */
public final class Elm327 {
    public interface Log { void line(String s); }

    private static final Charset ASCII = Charset.forName("US-ASCII");
    private final Link link;
    private final Log log;
    private final byte[] buf = new byte[512];
    public boolean traffic;
    public boolean countSuffix;
    public String version = "";

    public Elm327(Link link, Log log) { this.link = link; this.log = log; }

    /** Sends one command and returns the reply without echo, prompt or blank lines. */
    public synchronized String cmd(String c, int timeoutMs) throws IOException {
        while (link.read(buf, 5) > 0) { }
        link.write((c + "\r").getBytes(ASCII));
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        boolean prompt = false;
        while (!prompt && System.currentTimeMillis() < end) {
            int n = link.read(buf, 40);
            for (int i = 0; i < n; i++) {
                char ch = (char) (buf[i] & 0xFF);
                if (ch == '>') { prompt = true; break; }
                if (ch != 0) sb.append(ch);
            }
        }
        String out = clean(sb.toString(), c);
        if (traffic) log.line("> " + c + "  " + out.replace('\n', ' '));
        if (!prompt) throw new IOException("No reply to " + c + (out.isEmpty() ? "" : " (got: " + out + ")"));
        return out;
    }

    static String clean(String raw, String cmd) {
        StringBuilder out = new StringBuilder();
        for (String l : raw.replace('\r', '\n').split("\n")) {
            String t = l.trim();
            if (t.isEmpty()) continue;
            if (t.equalsIgnoreCase(cmd)) continue;
            if (t.toUpperCase(Locale.US).startsWith("SEARCHING")) continue;
            if (out.length() > 0) out.append('\n');
            out.append(t);
        }
        return out.toString();
    }

    /** True when something that speaks ELM327 answers on this link at the current speed. */
    public boolean probe() {
        try {
            cmd("", 300);
        } catch (IOException ignored) { }
        try {
            String r = cmd("ATI", 1200);
            return r.toUpperCase(Locale.US).contains("ELM") || r.toUpperCase(Locale.US).contains("STN") || r.matches("(?s).*[vV]\\d.*");
        } catch (IOException e) {
            return false;
        }
    }

    public void init() throws IOException {
        try { cmd("ATZ", 3000); } catch (IOException e) { cmd("ATWS", 2000); }
        cmd("ATE0", 1000);
        cmd("ATL0", 1000);
        cmd("ATS0", 1000);
        cmd("ATH0", 1000);
        try { cmd("ATAT2", 1000); } catch (IOException ignored) { }
        cmd("ATSP0", 1000);
        version = cmd("ATI", 1000);
        try {
            String d = cmd("AT@1", 1000);
            if (!d.contains("?") && !d.isEmpty()) version = d + " (" + version + ")";
        } catch (IOException ignored) { }
    }
}
