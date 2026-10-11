package com.tailorcards.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name cannot exceed 255 characters")
    String name,

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must be a valid monetary amount")
    BigDecimal price,

    String imageUrl,

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    Integer stock,

    @NotNull(message = "Category ID is required")
    Long categoryId,

    String cardNumber,

    String set,

    String condition,

    String grading,

    @Pattern(regexp = "^(AVAILABLE|SOLD)$", message = "Status must be either AVAILABLE or SOLD")
    String status,

    // Optional catalog link (e.g. "base1-4", "sv03.5-151"); product writes are ADMIN-only
    @Pattern(regexp = ProductRequest.POKEMONTCG_ID_PATTERN, message = "pokemontcgId must look like 'base1-4'")
    String pokemontcgId,
    // The seller's own photos after the default picture; null leaves existing photos unchanged on update
    @Size(max = ProductRequest.MAX_PHOTOS, message = "A product can have at most 12 photos")
    List<@NotBlank @Size(max = 500) @Pattern(regexp = "^(https?://|/uploads/).+", message = "Photo URLs must be http(s) or /uploads/ links") String> photoUrls
) {
    public static final int MAX_PHOTOS = 12;
    public static final String POKEMONTCG_ID_PATTERN = "^[A-Za-z0-9.]{1,30}-[A-Za-z0-9]{1,15}$";

    public ProductRequest(String name, String description, BigDecimal price, String imageUrl, Integer stock,
                          Long categoryId, String cardNumber, String set, String condition, String grading,
                          String status) {
        this(name, description, price, imageUrl, stock, categoryId, cardNumber, set, condition, grading, status, null, null);
    }

    public ProductRequest(String name, String description, BigDecimal price, String imageUrl, Integer stock,
                          Long categoryId, String cardNumber, String set, String condition, String grading,
                          String status, String pokemontcgId) {
        this(name, description, price, imageUrl, stock, categoryId, cardNumber, set, condition, grading, status,
                pokemontcgId, null);
    }
}
