package com.tailorcards.api.trade.dto;

import com.tailorcards.api.trade.model.CustomerCardItem;
import lombok.Builder;

import java.util.List;

@Builder
public record TradeConversationResponse(
        String conversationId,
        String reply,
        List<CustomerCardItem> extractedItems,
        boolean requiresConfirmation
) {}
