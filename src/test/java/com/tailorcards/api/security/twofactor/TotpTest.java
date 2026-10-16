package com.tailorcards.api.security.twofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TOTP (RFC 6238)")
class TotpTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("Matches the RFC 6238 SHA-1 test vectors")
    void rfcVectors() {
        assertThat(Totp.code(RFC_SECRET, Totp.step(59), 8)).isEqualTo("94287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1111111109), 8)).isEqualTo("07081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1234567890), 8)).isEqualTo("89005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(2000000000), 8)).isEqualTo("69279037");
        assertThat(Totp.code(RFC_SECRET, Totp.step(59))).isEqualTo("287082"); // 6-digit form
    }

    @Test
    @DisplayName("Accepts one step of clock drift either way, refuses older codes and replays")
    void windowAndReplay() {
        long now = 1_700_000_000L;
        long step = Totp.step(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, 0)).isEqualTo(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 0)).isEqualTo(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 0)).isEqualTo(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 0)).isEqualTo(-1);
        // Already used this step: the same code is refused
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, step)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, "12345", now, 0)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, "abcdef", now, 0)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, null, now, 0)).isEqualTo(-1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step).substring(0, 3) + " " + Totp.code(RFC_SECRET, step).substring(3), now, 0))
                .isEqualTo(step); // spaces are fine
    }

    @Test
    @DisplayName("Base32 follows RFC 4648 and round-trips; the otpauth URI has what authenticator apps need")
    void base32AndUri() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(new String(Totp.fromBase32("MZXW6YTBOI"), StandardCharsets.US_ASCII)).isEqualTo("foobar");
        byte[] secret = Totp.newSecret(new SecureRandom());
        assertThat(Totp.fromBase32(Totp.base32(secret))).isEqualTo(secret);
        assertThatThrownBy(() -> Totp.fromBase32("not base32!")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Totp.otpauthUri("Tailor Cards", "admin", RFC_SECRET))
                .startsWith("otpauth://totp/Tailor%20Cards:admin?secret=" + Totp.base32(RFC_SECRET))
                .contains("issuer=Tailor%20Cards").contains("digits=6").contains("period=30");
    }
}
