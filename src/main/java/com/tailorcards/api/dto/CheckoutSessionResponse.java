package com.tailorcards.api.dto;

public record CheckoutSessionResponse(
    String url,
    String sessionId
) {}
