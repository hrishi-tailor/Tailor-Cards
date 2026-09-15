package com.tailorcards.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "submission_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "submission")
public class SubmissionMessage {

    public static final Set<String> VALID_ROLES = Set.of("ADMIN", "CUSTOMER");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private BuylistSubmission submission;

    @Column(name = "sender_role", nullable = false)
    private String senderRole;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.senderRole == null || this.senderRole.isBlank()) {
            this.senderRole = "CUSTOMER";
        } else {
            String normalized = this.senderRole.trim().toUpperCase();
            if (!VALID_ROLES.contains(normalized)) {
                throw new IllegalArgumentException("Invalid sender role: " + this.senderRole + ". Allowed: " + VALID_ROLES);
            }
            this.senderRole = normalized;
        }
    }
}
