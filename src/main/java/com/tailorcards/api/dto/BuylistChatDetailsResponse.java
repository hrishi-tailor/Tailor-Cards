package com.tailorcards.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Chatbot fields on a buylist submission. Amounts are USD market references. Admin-only fields
 * (red flags, transcript, owner decision) are null in customer responses; lines and transcript are
 * only loaded for single-submission views.
 */
public record BuylistChatDetailsResponse(
        String source,
        String currency,
        BigDecimal totalMarketUsd,
        BigDecimal eligibleMarketUsd,
        Integer likelihoodPct,
        String likelihoodLabel,
        List<String> likelihoodReasons,
        Instant quoteExpiresAt,
        Integer lineCount,
        List<Line> lines,
        List<String> redFlags,
        List<Map<String, Object>> transcript,
        String ownerDecision,
        BigDecimal counterAmountUsd,
        String dealType,
        BigDecimal requestedCashUsd,
        List<Map<String, Object>> storeCards,
        BigDecimal storeTotalUsd,
        BigDecimal cashOfferUsd,
        BigDecimal tradeCreditUsd,
        BigDecimal askRatio,
        BigDecimal usdCadRate
) {
    public record Line(Integer lineNo, String kind, String name, String setName, String cardNumber, String variant,
                       String condition, String grading, Integer quantity, String cardId, String matchedName, String matchedSet,
                       String rarity, String imageUrl, String currency, BigDecimal unitMarketUsd,
                       BigDecimal lineMarketUsd, BigDecimal eurTrend, String priceUpdatedAt, String priceBasis,
                       BigDecimal requestedUnitUsd, String status, String statusReason, String photoUrl) {}
}
