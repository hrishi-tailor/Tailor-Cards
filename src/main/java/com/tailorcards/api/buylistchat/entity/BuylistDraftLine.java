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
import java.time.Instant;

/** One line item in a draft (a card with quantity, or a bulk lot). */
@Entity
@Table(name = "buylist_draft_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuylistDraftLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false, length = 36)
    private String draftId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /** CARD or BULK. */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "input_text", length = 500)
    private String inputText;

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

    @Column(name = "matched_number", length = 40)
    private String matchedNumber;

    @Column(name = "rarity", length = 80)
    private String rarity;

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** TCGplayer market price in USD for the chosen variant; null means "no price". */
    @Column(name = "unit_market_usd", precision = 12, scale = 2)
    private BigDecimal unitMarketUsd;

    @Column(name = "eur_trend", precision = 12, scale = 2)
    private BigDecimal eurTrend;

    @Column(name = "price_updated_at", length = 40)
    private String priceUpdatedAt;

    @Column(name = "price_source", length = 40)
    private String priceSource;

    /** RAW (ungraded market price) or GRADED (price for this exact grade). */
    @Column(name = "price_basis", length = 10)
    private String priceBasis;

    /** Graded prices: number of recent sales behind the median; null for raw prices. */
    @Column(name = "price_sample_size")
    private Integer priceSampleSize;

    /** SELL deals: what the customer asks per card, in USD; null = no ask. */
    @Column(name = "requested_unit_usd", precision = 12, scale = 2)
    private BigDecimal requestedUnitUsd;

    @Column(name = "id_confidence", precision = 4, scale = 3)
    private BigDecimal idConfidence;

    /** PENDING, RESOLVED, AMBIGUOUS, NOT_FOUND or ERROR. */
    @Column(name = "resolve_state", nullable = false, length = 20)
    private String resolveState;

    @Column(name = "candidates_json", columnDefinition = "TEXT")
    private String candidatesJson;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
