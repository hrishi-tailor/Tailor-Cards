package com.tailorcards.api.trade.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TradeParameterUpdateRequest(
        @NotNull(message = "Parameter value is required")
        BigDecimal paramValue,

        String description
) {}
