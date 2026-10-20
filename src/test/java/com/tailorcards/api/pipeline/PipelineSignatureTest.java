package com.tailorcards.api.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pipeline request signatures")
class PipelineSignatureTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-123";
    private static final long NOW = 1_792_500_000L;
    private static final byte[] BODY = "{\"prices\":[]}".getBytes(StandardCharsets.UTF_8);

    private final PipelineSignature signatures =
            new PipelineSignature(SECRET, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    @Test
    @DisplayName("A correctly signed request is accepted")
    void validSignature() {
        String signature = signatures.sign("POST", "/api/internal/pricing/snapshots", BODY, NOW);
        assertThat(signatures.verify("POST", "/api/internal/pricing/snapshots", BODY, String.valueOf(NOW), signature)).isTrue();
    }

    @Test
    @DisplayName("Matches the Python signer (pipeline/price_ingest/signing.py) for the same inputs")
    void knownVector() {
        // Same vector as test_signing.py: both sides must produce this exact value
        assertThat(signatures.sign("GET", "/api/internal/pricing/watchlist", new byte[0], NOW))
                .isEqualTo("v1=2284e0ec1dd0e72c0e143053d801f5d6d1566f4eb9564040f0d4aaebc4518134");
    }

    @Test
    @DisplayName("A changed body, path, method or secret is refused")
    void tampering() {
        String signature = signatures.sign("POST", "/api/internal/pricing/snapshots", BODY, NOW);
        String ts = String.valueOf(NOW);
        assertThat(signatures.verify("POST", "/api/internal/pricing/snapshots", "{}".getBytes(), ts, signature)).isFalse();
        assertThat(signatures.verify("POST", "/api/internal/pricing/watchlist", BODY, ts, signature)).isFalse();
        assertThat(signatures.verify("GET", "/api/internal/pricing/snapshots", BODY, ts, signature)).isFalse();
        PipelineSignature other = new PipelineSignature("another-secret-that-is-also-long-enough",
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        assertThat(other.verify("POST", "/api/internal/pricing/snapshots", BODY, ts, signature)).isFalse();
    }

    @Test
    @DisplayName("Requests more than five minutes old or ahead are refused, so they can't be replayed")
    void staleTimestamps() {
        long old = NOW - 301;
        String signature = signatures.sign("POST", "/p", BODY, old);
        assertThat(signatures.verify("POST", "/p", BODY, String.valueOf(old), signature)).isFalse();
        long ahead = NOW + 301;
        assertThat(signatures.verify("POST", "/p", BODY, String.valueOf(ahead), signatures.sign("POST", "/p", BODY, ahead))).isFalse();
        long recent = NOW - 299;
        assertThat(signatures.verify("POST", "/p", BODY, String.valueOf(recent), signatures.sign("POST", "/p", BODY, recent))).isTrue();
    }

    @Test
    @DisplayName("Without a secret of 32+ characters the pipeline endpoints are off and nothing verifies")
    void disabledWithoutSecret() {
        PipelineSignature off = new PipelineSignature("too-short", Clock.systemUTC());
        assertThat(off.enabled()).isFalse();
        assertThat(off.verify("GET", "/p", new byte[0], String.valueOf(NOW), "v1=00")).isFalse();
        assertThat(signatures.verify("GET", "/p", new byte[0], "not-a-number", "v1=00")).isFalse();
        assertThat(signatures.verify("GET", "/p", new byte[0], String.valueOf(NOW), "sha1=00")).isFalse();
    }
}
