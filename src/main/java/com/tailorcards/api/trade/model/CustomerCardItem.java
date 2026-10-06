package com.tailorcards.api.trade.model;

import java.math.BigDecimal;

public record CustomerCardItem(
    String name,
    String set,
    String cardNumber,
    String pokemontcgId,
    String condition,
    String grading,
    Boolean isSealed,
    Integer quantity,
    BigDecimal marketPriceCad,
    String imageUrl
) {
    public int effectiveQuantity() {
        return (quantity == null || quantity <= 0) ? 1 : quantity;
    }

    public BigDecimal totalMarketPrice() {
        if (marketPriceCad == null) {
            return BigDecimal.ZERO;
        }
        return marketPriceCad.multiply(BigDecimal.valueOf(effectiveQuantity()));
    }
}
