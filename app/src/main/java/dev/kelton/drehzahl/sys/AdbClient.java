package dev.kelton.drehzahl.sys;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * A small adb client that talks to the tablet's own adbd, the way Shizuku does, so Drehzahl can
 * run shell-user commands without a PC. Plain adb (port 5555 after "adb tcpip") and wireless
 * debugging (TLS) both work. Only one command at a time.
 */
public final class AdbClient implements Closeable {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257, STLS = 0x534c5453;
    private static final int VERSION = 0x01000001, MAXDATA = 256 * 1024, STLS_VERSION = 0x01000000;

    /** Thrown when adbd wants the user to allow this key and asking was not allowed. */
    public static final class NeedsApproval extends IOException {
        NeedsApproval() { super("Drehzahl's adb key is not allowed yet"); }
    }

    private Socket sock;
    private InputStream in;
    private OutputStream out;
    private final AdbKey key;

    private AdbClient(AdbKey key) { this.key = key; }

    /**
     * Connects and logs in. With mayAsk, an unknown key makes the tablet show "Allow USB debugging?"
     * and this waits up to a minute for the tap.
     */
    public static AdbClient connect(AdbKey key, String host, int port, boolean mayAsk) throws Exception {
        AdbClient c = new AdbClient(key);
        try {
            c.sock = new Socket();
            c.sock.connect(new InetSocketAddress(host, port), 3000);
            c.sock.setSoTimeout(10000);
            c.in = c.sock.getInputStream();
            c.out = c.sock.getOutputStream();
            c.login(host, port, mayAsk);
            return c;
        } catch (Exception e) {
            c.close();
            throw e;
        }
    }

    private void login(String host, int port, boolean mayAsk) throws Exception {
        send(CNXN, VERSION, MAXDATA, "host::\u0000".getBytes("UTF-8"));
        boolean signed = false, offered = false;
        while (true) {
            Msg m = read();
            if (m.cmd == CNXN) return;
            if (m.cmd == STLS) {
                send(STLS, STLS_VERSION, 0, new byte[0]);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(new X509ExtendedKeyManager[] { keyManager() }, new TrustManager[] { trustAll() }, null);
                SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket(sock, host, port, true);
                s.setUseClientMode(true);
                s.setEnabledProtocols(new String[] { "TLSv1.3" });
                s.startHandshake();
                sock = s;
                in = s.getInputStream();
                out = s.getOutputStream();
                continue;
            }
            if (m.cmd != AUTH || m.arg0 != 1) throw new IOException("unexpected adb reply " + Integer.toHexString(m.cmd));
            if (!signed) {
                signed = true;
                send(AUTH, 2, 0, key.sign(m.data));
            } else if (!offered) {
                if (!mayAsk) throw new NeedsApproval();
                offered = true;
                send(AUTH, 3, 0, key.adbPublicKey("Drehzahl@tablet"));
                sock.setSoTimeout(60000);
            } else {
                throw new IOException("adb key refused");
            }
        }
    }

    /** Runs a shell command as the shell user and returns what it printed. */
    public String shell(String command) throws IOException {
        int local = 1, remote = 0;
        send(OPEN, local, 0, ("shell:" + command + "\u0000").getBytes("UTF-8"));
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        sock.setSoTimeout(30000);
        while (true) {
            Msg m = read();
            if (m.cmd == OKAY) { remote = m.arg0; continue; }
            if (m.cmd == WRTE) {
                text.write(m.data, 0, m.data.length);
                send(OKAY, local, m.arg0, new byte[0]);
                continue;
            }
            if (m.cmd == CLSE) {
                if (remote != 0) send(CLSE, local, remote, new byte[0]);
                return text.toString("UTF-8");
            }
        }
    }

    @Override public void close() {
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
    }

    private static final class Msg { int cmd, arg0, arg1; byte[] data; }

    private void send(int cmd, int a0, int a1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(a0).putInt(a1).putInt(data.length).putInt(sum).putInt(~cmd).put(data);
        out.write(h.array());
        out.flush();
    }

    private Msg read() throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] head = new byte[24];
        d.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = h.getInt();
        m.arg0 = h.getInt();
        m.arg1 = h.getInt();
        int len = h.getInt();
        if (len < 0 || len > MAXDATA * 4) throw new IOException("bad adb packet");
        m.data = new byte[len];
        d.readFully(m.data);
        return m;
    }

    private X509ExtendedKeyManager keyManager() {
        return new X509ExtendedKeyManager() {
            public String chooseClientAlias(String[] t, Principal[] p, Socket s) { return "drehzahl"; }
            public String chooseEngineClientAlias(String[] t, Principal[] p, SSLEngine e) { return "drehzahl"; }
            public String[] getClientAliases(String t, Principal[] p) { return new String[] { "drehzahl" }; }
            public String[] getServerAliases(String t, Principal[] p) { return null; }
            public String chooseServerAlias(String t, Principal[] p, Socket s) { return null; }
            public X509Certificate[] getCertificateChain(String a) {
                try { return new X509Certificate[] { key.certificate() }; } catch (Exception e) { return null; }
            }
            public PrivateKey getPrivateKey(String a) { return key.priv; }
        };
    }

    /** The tablet's own adbd uses a self-signed certificate; this only ever connects to the tablet itself. */
    private static X509ExtendedTrustManager trustAll() {
        return new X509ExtendedTrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) { }
            public void checkServerTrusted(X509Certificate[] c, String a) { }
            public void checkClientTrusted(X509Certificate[] c, String a, Socket s) { }
            public void checkServerTrusted(X509Certificate[] c, String a, Socket s) { }
            public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) { }
            public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) { }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }
}
