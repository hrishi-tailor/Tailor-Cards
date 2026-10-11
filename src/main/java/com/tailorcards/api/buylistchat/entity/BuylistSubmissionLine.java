package com.tailorcards.api.buylistchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Snapshot of a draft line at confirmation time. */
@Entity
@Table(name = "buylist_submission_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuylistSubmissionLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "set_name", length = 150)
    private String setName;

    @Column(name = "card_number", length = 40)
    private String cardNumber;

    @Column(name = "variant", length = 40)
    private String variant;

    @Column(name = "item_condition", length = 30)
    private String itemCondition;

    /** Grading company and grade for slabs, e.g. "PSA 10"; null for raw cards. */
    @Column(name = "grading", length = 30)
    private String grading;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "card_id", length = 60)
    private String cardId;

    @Column(name = "matched_name", length = 200)
    private String matchedName;

    @Column(name = "matched_set", length = 150)
    private String matchedSet;

    @Column(name = "rarity", length = 80)
    private String rarity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "unit_market_usd", precision = 12, scale = 2)
    private BigDecimal unitMarketUsd;

    @Column(name = "line_market_usd", precision = 14, scale = 2)
    private BigDecimal lineMarketUsd;

    @Column(name = "eur_trend", precision = 12, scale = 2)
    private BigDecimal eurTrend;

    @Column(name = "price_updated_at", length = 40)
    private String priceUpdatedAt;

    @Column(name = "id_confidence", precision = 4, scale = 3)
    private BigDecimal idConfidence;

    @Column(name = "price_basis", length = 10)
    private String priceBasis;

    @Column(name = "requested_unit_usd", precision = 12, scale = 2)
    private BigDecimal requestedUnitUsd;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "status_reason", length = 200)
    private String statusReason;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;
}
