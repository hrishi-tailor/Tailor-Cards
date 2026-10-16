package com.tailorcards.api.security.twofactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Time-based one-time passwords (RFC 6238, HMAC-SHA1, 30-second steps, 6 digits): the scheme Google Authenticator,
 * 1Password, Authy and others use. Secrets are shared as Base32 in an otpauth:// URI (the QR code).
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    /** Steps either side of now that are accepted, for clock drift between phone and server. */
    public static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    public static byte[] newSecret(SecureRandom random) {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    public static long step(long epochSeconds) {
        return Math.floorDiv(epochSeconds, STEP_SECONDS);
    }

    /** The code for one time step. */
    public static String code(byte[] secret, long step) {
        return code(secret, step, DIGITS);
    }

    static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /**
     * The time step a code matches within the drift window, or -1. Steps at or before {@code lastUsedStep} are
     * refused, so a code can't be used twice (replay).
     */
    public static long verify(byte[] secret, String code, long epochSeconds, long lastUsedStep) {
        if (code == null) {
            return -1;
        }
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        long now = step(epochSeconds);
        long match = -1;
        for (long s = now - WINDOW; s <= now + WINDOW; s++) {
            // Check every step (no early exit) so timing doesn't reveal which one matched
            if (MessageDigest.isEqual(code(secret, s).getBytes(StandardCharsets.US_ASCII), digits.getBytes(StandardCharsets.US_ASCII))
                    && s > lastUsedStep) {
                match = s;
            }
        }
        return match;
    }

    /** otpauth URI for the QR code, e.g. otpauth://totp/Tailor%20Cards:admin?secret=...&issuer=Tailor%20Cards */
    public static String otpauthUri(String issuer, String account, byte[] secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32(secret) + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    public static byte[] fromBase32(String text) {
        String clean = text.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Not Base32");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }
}
