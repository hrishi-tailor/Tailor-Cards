package com.tailorcards.api.trade.provider;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CardMarketPrice(
        String cardId,
        String name,
        String setName,
        String cardNumber,
        String imageUrl,
        String largeImageUrl,
        BigDecimal marketPriceUsd,
        String source
) {}
