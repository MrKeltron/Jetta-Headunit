package dev.kelton.drehzahl.sys;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Runs shell scripts as root through su (Magisk, KernelSU, APatch). Call off the main thread. */
public final class Root {
    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/su/bin/su",
            "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/system_ext/bin/su", "/vendor/bin/su" };

    public static final class Result {
        public final boolean ok;
        public final int code;
        public final String out;
        Result(boolean ok, int code, String out) { this.ok = ok; this.code = code; this.out = out; }
    }

    private Root() { }

    /** True when an su binary exists. It does not ask for permission. */
    public static boolean present() {
        for (String p : SU_PATHS) if (new File(p).exists()) return true;
        String path = System.getenv("PATH");
        if (path != null) for (String d : path.split(":")) if (new File(d, "su").exists()) return true;
        return false;
    }

    /** Feeds the script to su on stdin. The root manager may show a Grant prompt on the tablet. */
    public static Result run(String script, long timeoutMs) {
        Process p = null;
        try {
            p = new ProcessBuilder("su").redirectErrorStream(true).start();
            OutputStream in = p.getOutputStream();
            in.write((script + "\nexit\n").getBytes("UTF-8"));
            in.flush();
            in.close();
            final InputStream is = p.getInputStream();
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Thread reader = new Thread(new Runnable() {
                public void run() {
                    byte[] b = new byte[4096];
                    int n;
                    try { while ((n = is.read(b)) > 0) synchronized (buf) { buf.write(b, 0, n); } }
                    catch (Exception ignored) { }
                }
            }, "su-out");
            reader.start();
            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) p.destroy();
            reader.join(1000);
            String out;
            synchronized (buf) { out = buf.toString("UTF-8"); }
            int code = finished ? p.exitValue() : -1;
            return new Result(finished && code == 0, code, finished ? out : out + "\n(timed out)");
        } catch (Exception e) {
            if (p != null) p.destroy();
            return new Result(false, -1, String.valueOf(e.getMessage()));
        }
    }
}
