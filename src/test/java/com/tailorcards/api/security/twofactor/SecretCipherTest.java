package com.tailorcards.api.security.twofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TOTP secret encryption (AES-256-GCM)")
class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz012345".getBytes());

    @Test
    @DisplayName("Round-trips, uses a fresh IV each time, and detects tampering or the wrong key")
    void roundTrip() {
        SecretCipher cipher = new SecretCipher(KEY);
        byte[] secret = "a totp secret".getBytes();
        String a = cipher.encrypt(secret);
        String b = cipher.encrypt(secret);
        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo(secret);

        byte[] tampered = Base64.getDecoder().decode(a);
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(a)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Rejects keys that aren't 32 bytes of Base64")
    void badKeys() {
        assertThatThrownBy(() -> new SecretCipher("short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(null)).isInstanceOf(IllegalStateException.class);
    }
}
