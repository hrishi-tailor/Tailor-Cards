package com.tailorcards.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record BuylistSubmissionRequest(
    @NotBlank(message = "Customer email is required")
    @Email(message = "Customer email must be a valid email address")
    String customerEmail,

    String customerName,

    @NotBlank(message = "Card name is required")
    String cardName,

    String cardSet,

    BigDecimal askingPrice,

    String additionalComments,

    List<String> imageUrls
) {}
