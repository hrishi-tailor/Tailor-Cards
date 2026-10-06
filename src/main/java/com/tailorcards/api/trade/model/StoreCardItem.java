package com.tailorcards.api.trade.model;

import java.math.BigDecimal;

public record StoreCardItem(
    Long productId,
    String name,
    BigDecimal listPriceCad,
    BigDecimal costBasisCad,
    Integer quantity
) {
    public int effectiveQuantity() {
        return (quantity == null || quantity <= 0) ? 1 : quantity;
    }

    public BigDecimal totalListPrice() {
        if (listPriceCad == null) {
            return BigDecimal.ZERO;
        }
        return listPriceCad.multiply(BigDecimal.valueOf(effectiveQuantity()));
    }

    public BigDecimal totalCostBasis(BigDecimal defaultCostBasisRatio) {
        BigDecimal effectiveCostPerItem;
        if (costBasisCad != null && costBasisCad.compareTo(BigDecimal.ZERO) > 0) {
            effectiveCostPerItem = costBasisCad;
        } else if (listPriceCad != null) {
            effectiveCostPerItem = listPriceCad.multiply(defaultCostBasisRatio);
        } else {
            effectiveCostPerItem = BigDecimal.ZERO;
        }
        return effectiveCostPerItem.multiply(BigDecimal.valueOf(effectiveQuantity()));
    }
}
