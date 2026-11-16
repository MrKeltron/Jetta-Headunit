package dev.kelton.drehzahl.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import dev.kelton.drehzahl.Prefs;

/** Drehzahl's side of sys.ShellHelper: sends one command line and reads the answer. Never on the main thread. */
public final class ShellLink {
    private ShellLink() { }

    /** The PC toolkit has started a helper at some point. It is gone after a restart until started again. */
    public static boolean configured() { return Prefs.i(Prefs.SHELL_PORT, 0) > 0; }

    public static void set(int port, String token) {
        Prefs.put(Prefs.SHELL_TOKEN, token);
        Prefs.put(Prefs.SHELL_PORT, port);
    }

    /** Returns the helper's answer, or "error ..." when it is not running. */
    public static String call(String command) {
        int port = Prefs.i(Prefs.SHELL_PORT, 0);
        if (port <= 0) return "error no PC helper";
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            s.setSoTimeout(15000);
            OutputStream out = s.getOutputStream();
            out.write((Prefs.s(Prefs.SHELL_TOKEN, "") + " " + command + "\n").getBytes("UTF-8"));
            out.flush();
            String r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8")).readLine();
            return r == null ? "error no answer" : r;
        } catch (java.net.ConnectException e) {
            return "error PC helper not running (start it again from the PC toolkit after a tablet restart)";
        } catch (Exception e) {
            return "error " + e.getMessage();
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }
}
