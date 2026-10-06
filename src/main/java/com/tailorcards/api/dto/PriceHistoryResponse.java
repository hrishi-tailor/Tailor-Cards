package com.tailorcards.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Price history dossier for a trading card")
public record PriceHistoryResponse(
    @Schema(description = "Product database ID", example = "16")
    Long productId,

    @Schema(description = "Card name", example = "Mewtwo GX - Full Art")
    String productName,

    @Schema(description = "Expansion set", example = "Shining Legends")
    String cardSet,

    @Schema(description = "Card number in set", example = "78/73")
    String cardNumber,

    @Schema(description = "Card condition tier", example = "Near Mint (NM)")
    String condition,

    @Schema(description = "Grading authority and grade if applicable", example = "PSA 10 GEM MT")
    String grading,

    @Schema(description = "Timeframe range requested: 1M, 3M, or 1Y", example = "3M")
    String range,

    @Schema(description = "Currency denomination", example = "CAD")
    String currency,

    @Schema(description = "Current catalog listing price", example = "68.00")
    BigDecimal currentPrice,

    @Schema(description = "Lowest recorded price in the selected timeframe", example = "58.25")
    BigDecimal periodLow,

    @Schema(description = "Highest recorded price in the selected timeframe", example = "72.50")
    BigDecimal periodHigh,

    @Schema(description = "Net dollar change over the period", example = "4.50")
    BigDecimal changeAmount,

    @Schema(description = "Percentage change over the period (+/- %)", example = "7.09")
    Double changePercentage,

    @Schema(description = "Attribution label for market price data source", example = "TCGplayer market price via pokemontcg.io, converted to CAD")
    String sourceLabel,

    @Schema(description = "Whether the data points are synthetic sample data", example = "false")
    boolean isSampleData,

    @Schema(description = "Tracking start notice when fewer than 7 snapshots exist", example = "Tracking started 2026-03-01")
    String trackingStartDate,

    @Schema(description = "Number of snapshots available for this card", example = "12")
    int snapshotCount,

    @Schema(description = "Array of historical price points in chronological order")
    List<PricePointResponse> history
) {}
