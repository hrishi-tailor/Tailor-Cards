package com.tailorcards.api.listing.dto;

import java.math.BigDecimal;

/**
 * Market reference for a matched card, from CardPriceService (overrides, snapshots, provider).
 * Shown to the admin for comparison only; never used as the listing price.
 */
public record MarketReferenceResponse(
        String cardId,
        String name,
        String setName,
        String cardNumber,
        BigDecimal marketReferenceCad,
        String stockImageUrl
) {}
