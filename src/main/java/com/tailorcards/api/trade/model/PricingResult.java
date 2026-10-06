package com.tailorcards.api.trade.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record PricingResult(
    TradeDecision decision,
    TradeFlowType flowType,
    BigDecimal totalCustomerMarketValue,
    BigDecimal totalStoreListPrice,
    BigDecimal offerAmount,
    BigDecimal openingOfferAmount,
    BigDecimal walkAwayCreditAmount,
    BigDecimal topUpAmount,
    BigDecimal effectiveRate,
    BigDecimal openingRate,
    RuleTrace ruleTrace,
    String summaryReason,
    List<String> unmetConditions
) {
    public List<String> unmetConditions() {
        return unmetConditions == null ? Collections.emptyList() : Collections.unmodifiableList(unmetConditions);
    }
}
