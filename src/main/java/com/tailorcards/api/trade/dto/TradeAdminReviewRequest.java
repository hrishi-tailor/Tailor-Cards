package com.tailorcards.api.trade.dto;

import java.math.BigDecimal;

public record TradeAdminReviewRequest(
        BigDecimal revisedAmount,
        BigDecimal revisedTopUp,
        String adminNotes
) {}
