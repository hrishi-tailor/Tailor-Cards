package com.tailorcards.api.trade.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BuyRuleUpdateRequest(
        @NotNull(message = "Rate is required")
        @DecimalMin(value = "0.01", message = "Rate must be positive")
        @DecimalMax(value = "1.00", message = "Rate cannot exceed 1.00")
        BigDecimal rate,

        Integer priority,
        Boolean active
) {}
