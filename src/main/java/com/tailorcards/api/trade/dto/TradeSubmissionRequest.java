package com.tailorcards.api.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TradeSubmissionRequest(
        @NotNull(message = "Quote request is required")
        @Valid
        TradeQuoteRequest quote,

        @NotBlank(message = "Customer name is required")
        String customerName,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be valid")
        String customerEmail,

        String customerPhone,
        String customerNotes
) {}
