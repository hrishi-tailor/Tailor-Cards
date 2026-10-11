package com.tailorcards.api.buylistchat.pricing;

import java.math.BigDecimal;

/**
 * What the scrap filter and likelihood meter need to know about one line.
 *
 * @param resolveState PENDING, RESOLVED, AMBIGUOUS, NOT_FOUND or ERROR
 * @param unitMarketUsd null means "no price" (never treated as zero); for graded lines it is the ungraded price
 * @param grading slab grade such as "PSA 10", or null for raw cards
 * @param priceBasis RAW (ungraded price) or GRADED (price for this exact grade)
 * @param priceSampleSize graded prices: recent sales behind the median; null for raw prices
 */
public record LineFacts(
        long lineId,
        boolean bulk,
        String resolveState,
        String category,
        String condition,
        int quantity,
        BigDecimal unitMarketUsd,
        BigDecimal idConfidence,
        boolean hasPhoto,
        String cardId,
        String grading,
        String priceBasis,
        Integer priceSampleSize
) {
    public LineFacts(long lineId, boolean bulk, String resolveState, String category, String condition, int quantity,
                     BigDecimal unitMarketUsd, BigDecimal idConfidence, boolean hasPhoto, String cardId) {
        this(lineId, bulk, resolveState, category, condition, quantity, unitMarketUsd, idConfidence, hasPhoto, cardId, null, null, null);
    }

    public LineFacts(long lineId, boolean bulk, String resolveState, String category, String condition, int quantity,
                     BigDecimal unitMarketUsd, BigDecimal idConfidence, boolean hasPhoto, String cardId, String grading) {
        this(lineId, bulk, resolveState, category, condition, quantity, unitMarketUsd, idConfidence, hasPhoto, cardId, grading, null, null);
    }

    public LineFacts(long lineId, boolean bulk, String resolveState, String category, String condition, int quantity,
                     BigDecimal unitMarketUsd, BigDecimal idConfidence, boolean hasPhoto, String cardId, String grading,
                     String priceBasis) {
        this(lineId, bulk, resolveState, category, condition, quantity, unitMarketUsd, idConfidence, hasPhoto, cardId, grading,
                priceBasis, null);
    }

    /** A graded price that rests on fewer recent sales than {@code minSales}. */
    public boolean thinGradedData(int minSales) {
        return graded() && "GRADED".equals(priceBasis) && (priceSampleSize == null || priceSampleSize < minSales);
    }

    public boolean graded() {
        return grading != null && !grading.isBlank();
    }

    /** A slab whose only price is for an ungraded copy (no graded market data): priced by hand. */
    public boolean ungradedPriceForSlab() {
        return graded() && !"GRADED".equals(priceBasis);
    }

    public BigDecimal lineMarketUsd() {
        return unitMarketUsd == null ? null : unitMarketUsd.multiply(BigDecimal.valueOf(quantity));
    }
}
