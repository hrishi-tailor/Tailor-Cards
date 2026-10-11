package com.tailorcards.api.dto;

import java.math.BigDecimal;
import java.util.List;

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
    String pokemontcgId,
    /** The seller's own photos, shown after the default picture (imageUrl). */
    List<String> photoUrls
) {
    public ProductResponse(Long id, String name, String description, BigDecimal price, String imageUrl, Integer stock,
                           CategoryResponse category, String cardNumber, String set, String condition, String grading,
                           String status, String pokemontcgId) {
        this(id, name, description, price, imageUrl, stock, category, cardNumber, set, condition, grading, status,
                pokemontcgId, List.of());
    }

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
        this(id, name, description, price, imageUrl, stock, category, cardNumber, set, condition, grading, status, null, List.of());
    }
}
