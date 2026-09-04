package com.tailorcards.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    String cartSessionId,
    List<CartItemResponse> items,
    BigDecimal totalPrice,
    int totalItems
) {}
