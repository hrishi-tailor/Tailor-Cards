package com.tailorcards.api.trade.dto;

import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.TradeFlowType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TradeQuoteRequest(
        @NotNull(message = "Trade flow type is required (SELL or TRADE)")
        TradeFlowType flowType,

        @NotEmpty(message = "At least one customer card is required for pricing")
        List<CustomerCardItem> customerCards,

        List<Long> storeProductIds
) {}
