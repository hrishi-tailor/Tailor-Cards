package com.tailorcards.api.dto;

import java.time.Instant;

public record SubmissionMessageResponse(
    Long id,
    String senderRole,
    String senderEmail,
    String message,
    Instant createdAt
) {}
