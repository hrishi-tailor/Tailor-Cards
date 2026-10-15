package com.tailorcards.api.security.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** An admin's TOTP enrolment. Secrets are AES-GCM encrypted (SecretCipher); pending holds one being set up. */
@Entity
@Table(name = "admin_two_factor")
@Getter
@Setter
@NoArgsConstructor
public class AdminTwoFactor {

    @Id
    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "secret_ciphertext", length = 200)
    private String secretCiphertext;

    @Column(name = "pending_secret_ciphertext", length = 200)
    private String pendingSecretCiphertext;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Last accepted time step: codes from it or earlier are refused (no replay). */
    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
