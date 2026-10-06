package com.tailorcards.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ManualPriceOverrideResponse(
        Long id,
        String cardId,
        String conditionOrGrade,
        BigDecimal overridePriceCad,
        String notes,
        Instant updatedAt
) {}
