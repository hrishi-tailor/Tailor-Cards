package com.tailorcards.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record BuylistStatusUpdateRequest(
    @NotBlank(message = "Status is required")
    String status,

    // Optional counter amount (USD) when making an offer on a chatbot submission
    @DecimalMin(value = "0.0", inclusive = false, message = "Counter amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Counter amount must be a valid monetary amount")
    BigDecimal counterAmountUsd
) {
    public BuylistStatusUpdateRequest(String status) {
        this(status, null);
    }
}
