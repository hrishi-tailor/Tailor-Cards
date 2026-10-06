package com.tailorcards.api.trade.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CardLiquidityRequest(
        @NotBlank(message = "pokemontcg_id is required")
        String pokemontcgId,

        @NotBlank(message = "Liquidity tier is required (HIGH, MEDIUM, LOW)")
        String liquidityTier,

        @NotNull(message = "Haircut is required")
        @DecimalMin(value = "0.00", message = "Haircut cannot be negative")
        @DecimalMax(value = "0.50", message = "Haircut cannot exceed 0.50")
        BigDecimal haircut,

        String notes
) {}
