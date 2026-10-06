package com.tailorcards.api.trade.dto;

import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.TradeFlowType;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TradeConversationRequest(
        String conversationId,
        TradeFlowType flowType,
        @NotEmpty(message = "Message history cannot be empty")
        List<TradeChatMessageDto> messages,
        List<CustomerCardItem> confirmedItems,
        List<Long> targetProductIds
) {}
