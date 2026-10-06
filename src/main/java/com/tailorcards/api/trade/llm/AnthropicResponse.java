package com.tailorcards.api.trade.llm;

import java.math.BigDecimal;

public record AnthropicResponse(
        String text,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        BigDecimal estimatedCostUsd
) {}
