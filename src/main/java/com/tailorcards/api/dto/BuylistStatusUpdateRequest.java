package com.tailorcards.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BuylistStatusUpdateRequest(
    @NotBlank(message = "Status is required")
    String status
) {}
