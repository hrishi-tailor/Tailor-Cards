package com.tailorcards.api.dto;

import java.util.List;

public record CheckoutSessionRequest(
    String cartId,
    List<Long> productIds
) {}
