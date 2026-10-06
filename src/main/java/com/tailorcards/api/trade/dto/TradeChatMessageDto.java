package com.tailorcards.api.trade.dto;

import jakarta.validation.constraints.NotBlank;

public record TradeChatMessageDto(
        @NotBlank(message = "Role is required (user or assistant)")
        String role,

        @NotBlank(message = "Message content is required")
        String content
) {}
