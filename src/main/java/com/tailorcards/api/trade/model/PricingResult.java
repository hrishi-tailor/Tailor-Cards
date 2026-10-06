package com.tailorcards.api.trade.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Builder(toBuilder = true)
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

    public BigDecimal cashOffer() {
        return offerAmount;
    }

    public BigDecimal tradeCredit() {
        return openingOfferAmount != null ? openingOfferAmount : offerAmount;
    }

    public BigDecimal counterTopUp() {
        return topUpAmount;
    }

    public BigDecimal customerTotalMarketCad() {
        return totalCustomerMarketValue;
    }

    public BigDecimal storeTotalListPriceCad() {
        return totalStoreListPrice;
    }

    public static class PricingResultBuilder {
        public PricingResultBuilder cashOffer(BigDecimal amount) {
            this.offerAmount = amount;
            return this;
        }

        public PricingResultBuilder tradeCredit(BigDecimal amount) {
            this.openingOfferAmount = amount;
            this.offerAmount = amount;
            return this;
        }

        public PricingResultBuilder counterTopUp(BigDecimal amount) {
            this.topUpAmount = amount;
            return this;
        }

        public PricingResultBuilder customerTotalMarketValueCad(BigDecimal amount) {
            this.totalCustomerMarketValue = amount;
            return this;
        }

        public PricingResultBuilder storeTotalListPriceCad(BigDecimal amount) {
            this.totalStoreListPrice = amount;
            return this;
        }
    }
}
