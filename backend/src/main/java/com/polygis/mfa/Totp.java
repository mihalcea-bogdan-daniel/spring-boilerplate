package com.polygis.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s step) - what Google Authenticator, 1Password, Authy... expect. */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static String newSecret() {
        byte[] key = new byte[20];
        RANDOM.nextBytes(key);
        return base32Encode(key);
    }

    public static long currentStep() {
        return System.currentTimeMillis() / 1000 / STEP_SECONDS;
    }

    /**
     * @param window  number of steps of clock drift tolerated on each side
     * @param minStep exclusive lower bound (last accepted step) so a code cannot be replayed
     * @return the matching time step, or -1 if the code is wrong or already used
     */
    public static long verify(String base32Secret, String code, long nowStep, int window, long minStep) {
        if (code == null || !code.matches("\\d{6}")) return -1;
        byte[] key = base32Decode(base32Secret);
        for (long step = nowStep - window; step <= nowStep + window; step++) {
            if (step > minStep && constantTimeEquals(code(key, step), code)) return step;
        }
        return -1;
    }

    public static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = h[h.length - 1] & 0x0f;
            int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** otpauth:// URI encoded in the QR code. */
    public static String uri(String issuer, String account, String secret) {
        String i = java.net.URLEncoder.encode(issuer, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String a = java.net.URLEncoder.encode(account, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + i + ":" + a + "?secret=" + secret + "&issuer=" + i + "&digits=6&period=30";
    }

    public static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    public static byte[] base32Decode(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Invalid base32 character");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
