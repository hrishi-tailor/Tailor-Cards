package com.tailorcards.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import java.util.UUID;

@Entity
@Table(
    name = "trade_assistant_requests",
    indexes = {
        @Index(name = "idx_trade_req_reference", columnList = "reference_code"),
        @Index(name = "idx_trade_req_status", columnList = "status"),
        @Index(name = "idx_trade_req_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TradeAssistantRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_code", nullable = false, unique = true, length = 64)
    private String referenceCode;

    @Column(name = "flow_type", nullable = false, length = 20)
    private String flowType; // SELL or TRADE

    @Column(name = "status", nullable = false, length = 30)
    private String status; // PENDING_REVIEW, APPROVED, COUNTERED, DECLINED, COMPLETED

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 150)
    private String customerEmail;

    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    @Column(name = "decision", nullable = false, length = 30)
    private String decision; // ACCEPT, COUNTER, DECLINE, NEEDS_REVIEW

    @Column(name = "offered_amount", precision = 10, scale = 2)
    private BigDecimal offeredAmount;

    @Column(name = "counter_top_up", precision = 10, scale = 2)
    private BigDecimal counterTopUp;

    @Column(name = "customer_total_market_cad", precision = 10, scale = 2)
    private BigDecimal customerTotalMarketCad;

    @Column(name = "store_total_list_price_cad", precision = 10, scale = 2)
    private BigDecimal storeTotalListPriceCad;

    @Column(name = "customer_cards_json", columnDefinition = "TEXT")
    private String customerCardsJson;

    @Column(name = "store_products_json", columnDefinition = "TEXT")
    private String storeProductsJson;

    @Column(name = "rule_trace_json", columnDefinition = "TEXT")
    private String ruleTraceJson;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
        if (this.referenceCode == null || this.referenceCode.isBlank()) {
            this.referenceCode = "TR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING_REVIEW";
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
