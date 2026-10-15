package com.tailorcards.api.security.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A second-factor session: issued after a valid code, sent as X-Admin-Session. Only the SHA-256 is stored. */
@Entity
@Table(name = "admin_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AdminSession {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "created_ip", length = 64)
    private String createdIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
