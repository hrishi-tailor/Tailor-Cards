package com.tailorcards.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BuylistSubmissionResponse(
    Long id,
    String trackingToken,
    String customerEmail,
    String customerName,
    String cardName,
    String cardSet,
    BigDecimal askingPrice,
    String additionalComments,
    List<String> imageUrls,
    String status,
    Instant createdAt,
    List<SubmissionMessageResponse> messages
) {}
