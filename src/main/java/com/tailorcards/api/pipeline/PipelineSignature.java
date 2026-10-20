package com.tailorcards.api.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifies requests from the price pipeline (an AWS Lambda) without any AWS credentials on the app's host. Each
 * request carries X-TC-Timestamp (Unix seconds) and X-TC-Signature: {@code v1=} + hex HMAC-SHA256, keyed with the
 * shared secret, of {@code timestamp \n METHOD \n path \n sha256-hex(body)}. A request older or newer than five
 * minutes is refused, so a captured request can't be replayed later. The same scheme is implemented in
 * pipeline/price_ingest/signing.py.
 */
@Component
public class PipelineSignature {

    static final Duration MAX_SKEW = Duration.ofMinutes(5);
    static final int MIN_SECRET_LENGTH = 32;

    private final byte[] secret;
    private final Clock clock;

    @Autowired
    public PipelineSignature(@Value("${app.pipeline.shared-secret:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    PipelineSignature(String secret, Clock clock) {
        String value = secret == null ? "" : secret.trim();
        this.secret = value.length() >= MIN_SECRET_LENGTH ? value.getBytes(StandardCharsets.UTF_8) : null;
        this.clock = clock;
    }

    /** False when no shared secret (of at least 32 characters) is configured: the pipeline endpoints stay off. */
    public boolean enabled() {
        return secret != null;
    }

    public boolean verify(String method, String path, byte[] body, String timestamp, String signature) {
        if (!enabled() || timestamp == null || signature == null || !signature.startsWith("v1=")) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - seconds);
        if (skew > MAX_SKEW.toSeconds()) {
            return false;
        }
        byte[] expected = sign(method, path, body, seconds).getBytes(StandardCharsets.US_ASCII);
        byte[] given = signature.trim().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }

    /** The signature header value for a request; used by tests and documented for other clients. */
    String sign(String method, String path, byte[] body, long timestamp) {
        String payload = timestamp + "\n" + method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + sha256Hex(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
