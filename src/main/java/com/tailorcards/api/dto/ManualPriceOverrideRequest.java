package com.tailorcards.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ManualPriceOverrideRequest(
        @NotBlank(message = "Card ID is required")
        String cardId,

        @NotBlank(message = "Condition or grade is required (e.g. PSA 10, BGS BL, SEALED, RAW_NM)")
        String conditionOrGrade,

        @NotNull(message = "Override price in CAD is required")
        @DecimalMin(value = "0.01", message = "Override price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Price must be a valid monetary amount")
        BigDecimal overridePriceCad,

        String notes
) {}
