package com.tailorcards.api.trade.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record TradeSubmissionResponse(
        Long id,
        String referenceCode,
        String status,
        String flowType,
        String decision,
        BigDecimal offeredAmount,
        BigDecimal counterTopUp,
        BigDecimal customerTotalMarketCad,
        BigDecimal storeTotalListPriceCad,
        String customerName,
        String customerEmail,
        String explanation,
        String adminNotes,
        Instant createdAt
) {}
