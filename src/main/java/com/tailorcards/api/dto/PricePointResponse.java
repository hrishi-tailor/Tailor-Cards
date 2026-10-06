package com.tailorcards.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Single historical price data point")
public record PricePointResponse(
    @Schema(description = "Date of the recorded price (YYYY-MM-DD)", example = "2024-03-15")
    String date,

    @Schema(description = "Recorded market price in CAD", example = "65.50")
    BigDecimal price,

    @Schema(description = "Trading volume / recorded transactions", example = "12")
    Integer volume
) {}
