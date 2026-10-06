package com.tailorcards.api.trade.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.RuleTrace;
import com.tailorcards.api.trade.model.StoreCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record TradeQuoteResponse(
        TradeFlowType flowType,
        TradeDecision decision,
        BigDecimal cashOffer,
        BigDecimal tradeCredit,
        BigDecimal counterTopUp,
        BigDecimal customerTotalMarketCad,
        BigDecimal storeTotalListPriceCad,
        String explanation,
        @JsonIgnore
        RuleTrace ruleTrace,
        List<CustomerCardItem> customerCards,
        List<StoreCardItem> storeCards
) {}
