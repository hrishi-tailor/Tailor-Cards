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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "buylist_submissions", uniqueConstraints = {
        // One confirmed chatbot submission per email per America/Toronto day (also created by V3)
        @UniqueConstraint(name = "uk_buylist_email_local_date", columnNames = {"customer_email", "local_date"})
})
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

    // ---- Chatbot intake (null for manual submissions) ----

    /** CHAT for chatbot submissions; null for the manual form. */
    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "total_market_usd", precision = 12, scale = 2)
    private BigDecimal totalMarketUsd;

    @Column(name = "eligible_market_usd", precision = 12, scale = 2)
    private BigDecimal eligibleMarketUsd;

    @Column(name = "likelihood_pct")
    private Integer likelihoodPct;

    /** JSON array of customer-safe reasons. */
    @Column(name = "likelihood_reasons", columnDefinition = "TEXT")
    private String likelihoodReasons;

    /** JSON array of admin-only red flags. */
    @Column(name = "red_flags", columnDefinition = "TEXT")
    private String redFlags;

    /** APPROVED, COUNTERED or DECLINED; stored for likelihood calibration. */
    @Column(name = "owner_decision", length = 20)
    private String ownerDecision;

    @Column(name = "owner_decided_at")
    private Instant ownerDecidedAt;

    @Column(name = "counter_amount_usd", precision = 12, scale = 2)
    private BigDecimal counterAmountUsd;

    /** America/Toronto date of confirmation; cleared when an admin resets the daily limit. */
    @Column(name = "local_date")
    private LocalDate localDate;

    @Column(name = "limit_reset_at")
    private Instant limitResetAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "submitter_ip", length = 64)
    private String submitterIp;

    @Column(name = "draft_id", length = 36)
    private String draftId;

    /** JSON array of {role, content, at}. */
    @Column(name = "chat_transcript", columnDefinition = "TEXT")
    private String chatTranscript;

    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;

    /** SELL, TRADE or PARTIAL. */
    @Column(name = "deal_type", length = 10)
    private String dealType;

    @Column(name = "requested_cash_usd", precision = 12, scale = 2)
    private BigDecimal requestedCashUsd;

    /** JSON array of the shop cards picked for trade: {productId, name, priceCad, priceUsd}. */
    @Column(name = "store_cards_json", columnDefinition = "TEXT")
    private String storeCardsJson;

    @Column(name = "store_total_usd", precision = 12, scale = 2)
    private BigDecimal storeTotalUsd;

    /** Cash we'd pay under the buy rules for the eligible lines (market reference). */
    @Column(name = "cash_offer_usd", precision = 12, scale = 2)
    private BigDecimal cashOfferUsd;

    /** Store credit at the trade rate for the eligible lines. */
    @Column(name = "trade_credit_usd", precision = 12, scale = 2)
    private BigDecimal tradeCreditUsd;

    /** Customer's request relative to our rules: 1.0 = exactly at our rates, above 1 = asking more. */
    @Column(name = "ask_ratio", precision = 8, scale = 3)
    private BigDecimal askRatio;

    @Column(name = "usd_cad_rate", precision = 10, scale = 4)
    private BigDecimal usdCadRate;

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
