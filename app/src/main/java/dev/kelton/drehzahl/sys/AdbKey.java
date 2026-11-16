package dev.kelton.drehzahl.sys;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Drehzahl's own adb key (RSA 2048, kept in the app's private files). The tablet's adb trusts it
 * once the "Allow USB debugging?" prompt is accepted with "Always allow".
 */
public final class AdbKey {
    private static AdbKey inst;

    final PrivateKey priv;
    final RSAPublicKey pub;
    private X509Certificate cert;

    private AdbKey(PrivateKey priv, RSAPublicKey pub) { this.priv = priv; this.pub = pub; }

    public static synchronized AdbKey get(Context c) throws Exception {
        if (inst != null) return inst;
        File dir = new File(c.getFilesDir(), "adbkey");
        File fp = new File(dir, "private.der"), fu = new File(dir, "public.der");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (fp.exists() && fu.exists()) {
            inst = new AdbKey(kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(fp.toPath()))),
                    (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(fu.toPath()))));
            return inst;
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        dir.mkdirs();
        write(fp, kp.getPrivate().getEncoded());
        write(fu, kp.getPublic().getEncoded());
        inst = new AdbKey(kp.getPrivate(), (RSAPublicKey) kp.getPublic());
        return inst;
    }

    private static void write(File f, byte[] b) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(b); }
    }

    /** The public key in adb's own format, as sent with AUTH RSAPUBLICKEY. */
    byte[] adbPublicKey(String name) throws Exception {
        BigInteger n = pub.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n.mod(r32).modInverse(r32).negate().mod(r32).intValue());
        putLittle(b, n);
        putLittle(b, BigInteger.ONE.shiftLeft(4096).mod(n));
        b.putInt(pub.getPublicExponent().intValue());
        return (Base64.encodeToString(b.array(), Base64.NO_WRAP) + " " + name + "\u0000").getBytes("UTF-8");
    }

    private static void putLittle(ByteBuffer b, BigInteger v) {
        byte[] be = v.toByteArray();
        for (int i = 0; i < 256; i++) b.put(i < be.length ? be[be.length - 1 - i] : 0);
    }

    /** Signs adbd's 20-byte token the way adb does: PKCS#1 v1.5 type 1 around a SHA-1 DigestInfo of the raw token. */
    byte[] sign(byte[] token) throws Exception {
        byte[] info = { 0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14 };
        byte[] block = new byte[256];
        block[1] = 1;
        int ff = 256 - 3 - info.length - token.length;
        for (int i = 0; i < ff; i++) block[2 + i] = (byte) 0xff;
        System.arraycopy(info, 0, block, 3 + ff, info.length);
        System.arraycopy(token, 0, block, 3 + ff + info.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, priv);
        return c.doFinal(block);
    }

    /** A self-signed certificate for the key, which wireless debugging (TLS) asks for. */
    synchronized X509Certificate certificate() throws Exception {
        if (cert != null) return cert;
        byte[] alg = seq(cat(new byte[] { 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b }, new byte[] { 0x05, 0x00 }));
        byte[] name = seq(tlv(0x31, seq(cat(new byte[] { 0x06, 0x03, 0x55, 0x04, 0x03 }, tlv(0x0c, "Drehzahl".getBytes("UTF-8"))))));
        byte[] validity = seq(cat(tlv(0x17, "250101000000Z".getBytes("US-ASCII")), tlv(0x17, "491231235959Z".getBytes("US-ASCII"))));
        byte[] tbs = seq(cat(new byte[] { (byte) 0xa0, 0x03, 0x02, 0x01, 0x02 }, new byte[] { 0x02, 0x01, 0x01 }, alg, name, validity, name, pub.getEncoded()));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(priv);
        s.update(tbs);
        byte[] sig = s.sign();
        byte[] bits = new byte[sig.length + 1];
        System.arraycopy(sig, 0, bits, 1, sig.length);
        byte[] der = seq(cat(tbs, alg, tlv(0x03, bits)));
        cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        return cert;
    }

    private static byte[] seq(byte[] body) { return tlv(0x30, body); }

    private static byte[] tlv(int tag, byte[] body) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int n = body.length;
        if (n < 0x80) o.write(n);
        else if (n < 0x100) { o.write(0x81); o.write(n); }
        else if (n < 0x10000) { o.write(0x82); o.write(n >> 8); o.write(n); }
        else { o.write(0x83); o.write(n >> 16); o.write(n >> 8); o.write(n); }
        o.write(body, 0, n);
        return o.toByteArray();
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }
}
