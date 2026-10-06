package com.tailorcards.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceSnapshotResponse(
        Long id,
        String cardId,
        BigDecimal priceUsd,
        BigDecimal priceCad,
        String source,
        Instant fetchedAt
) {}
