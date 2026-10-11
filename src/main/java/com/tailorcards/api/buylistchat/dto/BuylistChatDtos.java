package com.tailorcards.api.buylistchat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response bodies for the customer-facing buylist chat API. */
public final class BuylistChatDtos {

    private BuylistChatDtos() {
    }

    public record StatusResponse(boolean enabled, boolean emailVerificationRequired, String turnstileSiteKey,
                                 int maxLines, int maxMessageLength, int maxNoteLength) {}

    public record GuestSessionRequest(String turnstileToken) {}

    public record OtpRequest(String email, String turnstileToken) {}

    public record OtpVerifyRequest(String email, String code) {}

    public record SessionResponse(String sessionToken, String email, Instant expiresAt) {}

    public record ChatMessageRequest(String message) {}

    public record PasteRequest(String text) {}

    /**
     * requestedUnitUsd: the customer's ask per card for SELL deals; 0 clears it.
     * grading: slab grade such as "PSA 10"; "RAW" (or blank) makes the line an ungraded card.
     */
    public record LineUpdateRequest(Integer quantity, String condition, String variant, BigDecimal requestedUnitUsd,
                                    String grading) {
        public LineUpdateRequest(Integer quantity, String condition, String variant, BigDecimal requestedUnitUsd) {
            this(quantity, condition, variant, requestedUnitUsd, null);
        }
    }

    /** dealType: SELL, TRADE or PARTIAL; requestedCashUsd: PARTIAL cash on top of the shop cards (0 clears). */
    public record DealRequest(String dealType, BigDecimal requestedCashUsd) {}

    public record TradeItemRequest(Long productId) {}

    public record StoreCardView(Long productId, String name, String setName, String cardNumber, String condition,
                                String grading, String imageUrl, BigDecimal priceCad, BigDecimal priceUsd, int stock,
                                boolean available) {}

    /**
     * Deal summary in USD market terms. cashOfferUsd / tradeCreditUsd follow the shop's published rates;
     * askRatio = request / what the rates allow (1.0 = at our rates). Not an offer.
     */
    public record DealView(String dealType, String currency, String ratesText, BigDecimal offerableMarketUsd,
                           BigDecimal cashOfferUsd, BigDecimal tradeCreditUsd, List<StoreCardView> storeCards,
                           BigDecimal storeTotalUsd, BigDecimal storeTotalCad, BigDecimal usdCadRate,
                           BigDecimal requestedCashUsd, BigDecimal askTotalUsd, BigDecimal askRatio,
                           boolean withinRules, String message, boolean needsStoreCards, BigDecimal overByUsd) {}

    /** {@code email} is required (and unverified) only when email verification is turned off. */
    public record ConfirmRequest(String contentHash, String customerName, String notes, String email) {}

    public record ConfirmResponse(Long submissionId, String trackingToken, Integer likelihoodPct, String currency,
                                  BigDecimal totalMarketUsd, Instant quoteExpiresAt, String quoteNotice) {}

    public record CandidateView(String cardId, String name, String setName, String cardNumber) {}

    /**
     * Amounts are USD market references (TCGplayer via TCGdex); null means "no price", never zero.
     * For graded lines priceBasis says whether the price is for that exact grade (GRADED, with
     * priceSampleSize recent sales behind it) or an ungraded copy (RAW: left out of totals).
     * offerUnitUsd is our cash offer per card at the published rates; null when we would not offer on it.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LineView(long id, int lineNo, String kind, String name, String setName, String cardNumber,
                           String variant, String condition, String grading, int quantity, String cardId, String matchedName,
                           String matchedSet, String matchedNumber, String rarity, String imageUrl, String currency,
                           BigDecimal unitMarketUsd, BigDecimal lineMarketUsd, BigDecimal eurTrend,
                           String priceUpdatedAt, String priceSource, String priceBasis, Integer priceSampleSize,
                           BigDecimal requestedUnitUsd, BigDecimal offerUnitUsd, String status, String statusReason,
                           boolean photoRequired, String photoUrl, List<CandidateView> candidates) {}

    public record Progress(int total, int pending) {}

    public record Summary(String currency, BigDecimal totalMarketUsd, BigDecimal eligibleMarketUsd,
                          Map<String, Integer> statusCounts, int likelihoodPct, String likelihoodLabel,
                          String likelihoodDisclaimer, List<String> likelihoodReasons, String contentHash, boolean ready,
                          boolean canSubmitToday, String dailyLimitNotice, String quoteNotice, DealView deal) {}

    public record ChatMessageView(String role, String content, Instant createdAt) {}

    public record DraftView(String draftId, String status, int maxLines, Progress progress, List<LineView> lines,
                            Summary summary, List<ChatMessageView> messages) {}

    public record ChatTurnResponse(String reply, DraftView draft) {}
}
