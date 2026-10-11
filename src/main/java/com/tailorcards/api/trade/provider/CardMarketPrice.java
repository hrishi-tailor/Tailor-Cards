package com.tailorcards.api.trade.provider;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Card metadata and market prices from a provider. {@code marketPriceUsd} is the headline USD
 * market price (preferred variant); {@code variantPricesUsd} has every variant the provider priced
 * (e.g. "normal", "holofoil", "reverse-holofoil"). Null prices mean "no price", never zero.
 */
@Builder(toBuilder = true)
public record CardMarketPrice(
        String cardId,
        String name,
        String setName,
        String cardNumber,
        String imageUrl,
        String largeImageUrl,
        BigDecimal marketPriceUsd,
        String source,
        String rarity,
        String category,
        Map<String, BigDecimal> variantPricesUsd,
        BigDecimal eurTrend,
        String pricesUpdatedAt,
        /** Printed set size (the "102" in "4/102"); null when the provider doesn't say. */
        Integer setOfficialCount
) {}
