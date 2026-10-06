package com.tailorcards.api.trade.model;

import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
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
    String imageUrl,
    Boolean confirmed
) {
    public CustomerCardItem(
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
        this(name, set, cardNumber, pokemontcgId, condition, grading, isSealed, quantity, marketPriceCad, imageUrl, null);
    }

    public String cardId() {
        return pokemontcgId;
    }

    public String getCardId() {
        return pokemontcgId;
    }

    public Boolean sealed() {
        return isSealed;
    }

    public Boolean getSealed() {
        return isSealed;
    }

    public boolean isConfirmed() {
        return !Boolean.FALSE.equals(confirmed);
    }

    public int effectiveQuantity() {
        return (quantity == null || quantity <= 0) ? 1 : quantity;
    }

    public BigDecimal totalMarketPrice() {
        if (marketPriceCad == null) {
            return BigDecimal.ZERO;
        }
        return marketPriceCad.multiply(BigDecimal.valueOf(effectiveQuantity()));
    }

    public static class CustomerCardItemBuilder {
        public CustomerCardItemBuilder cardId(String id) {
            this.pokemontcgId = id;
            return this;
        }

        public CustomerCardItemBuilder sealed(Boolean s) {
            this.isSealed = s;
            return this;
        }
    }
}
