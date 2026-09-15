package com.tailorcards.api.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "buylist_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"messages"})
public class BuylistSubmission {

    public static final Set<String> VALID_STATUSES = Set.of(
            "PENDING",
            "UNDER_REVIEW",
            "OFFERED",
            "ACCEPTED",
            "REJECTED"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_token", nullable = false, unique = true, updatable = false)
    private String trackingToken;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "card_name", nullable = false)
    private String cardName;

    @Column(name = "card_set")
    private String cardSet;

    @Column(name = "asking_price", precision = 10, scale = 2)
    private BigDecimal askingPrice;

    @Column(name = "additional_comments", columnDefinition = "TEXT")
    private String additionalComments;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "buylist_submission_images", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<SubmissionMessage> messages = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.trackingToken == null || this.trackingToken.isBlank()) {
            this.trackingToken = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        validateAndNormalizeStatus();
    }

    @PreUpdate
    public void preUpdate() {
        validateAndNormalizeStatus();
    }

    public void validateAndNormalizeStatus() {
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING";
            return;
        }
        String normalized = this.status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid status: " + this.status + ". Allowed statuses: " + VALID_STATUSES);
        }
        this.status = normalized;
    }

    public void setStatus(String status) {
        if (status == null || status.isBlank()) {
            this.status = "PENDING";
            return;
        }
        String normalized = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Allowed statuses: " + VALID_STATUSES);
        }
        this.status = normalized;
    }
}
