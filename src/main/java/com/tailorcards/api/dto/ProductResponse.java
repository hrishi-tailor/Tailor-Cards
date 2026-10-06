package com.tailorcards.api.dto;

import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    Integer stock,
    CategoryResponse category,
    String cardNumber,
    String set,
    String condition,
    String grading,
    String status,
    String pokemontcgId
) {
    public ProductResponse(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            Integer stock,
            CategoryResponse category,
            String cardNumber,
            String set,
            String condition,
            String grading,
            String status
    ) {
        this(id, name, description, price, imageUrl, stock, category, cardNumber, set, condition, grading, status, null);
    }
}
