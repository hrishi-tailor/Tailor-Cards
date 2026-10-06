package com.tailorcards.api.trade.service;

import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.RuleTrace;
import com.tailorcards.api.trade.model.StoreCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TradePricingEngine {

    private final TradeConfigService configService;

    public TradePricingEngine(TradeConfigService configService) {
        this.configService = configService;
    }

    /**
     * Unified evaluation entrypoint dispatching to evaluateSell or evaluateTrade based on flowType.
     */
    public PricingResult evaluate(TradeFlowType flowType, List<CustomerCardItem> customerCards, List<StoreCardItem> storeCards) {
        if (flowType == TradeFlowType.SELL) {
            return evaluateSell(customerCards);
        } else {
            return evaluateTrade(customerCards, storeCards);
        }
    }

    /**
     * Calculates cash offer for selling cards to Tailor Cards.
     */
    public PricingResult evaluateSell(List<CustomerCardItem> customerCards) {
        RuleTrace trace = new RuleTrace();
        List<String> unmetConditions = new ArrayList<>();

        if (customerCards == null || customerCards.isEmpty()) {
            trace.add("EMPTY_INPUT", "No customer cards provided", "NEEDS_REVIEW");
            return buildNeedsReviewResult(TradeFlowType.SELL, BigDecimal.ZERO, BigDecimal.ZERO, trace,
                    "No cards submitted for evaluation", List.of("No cards provided"));
        }

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalOfferAmount = BigDecimal.ZERO;
        boolean hasAmbiguousCard = false;

        List<BuyRule> buyRules = configService.getActiveBuyRules();

        for (int i = 0; i < customerCards.size(); i++) {
            CustomerCardItem item = customerCards.get(i);
            int qty = item.effectiveQuantity();
            String cardDesc = (item.name() != null ? item.name() : "Item #" + (i + 1));

            if (item.marketPriceCad() == null || item.marketPriceCad().compareTo(BigDecimal.ZERO) <= 0) {
                hasAmbiguousCard = true;
                String err = String.format("Card '%s' is missing market price", cardDesc);
                unmetConditions.add(err);
                trace.add("MISSING_PRICE", err, "NEEDS_REVIEW");
                continue;
            }

            BigDecimal cardMarket = item.totalMarketPrice();
            totalMarketValue = totalMarketValue.add(cardMarket);

            // Determine if card condition/grading/sealed status is valid or ambiguous
            ConditionEvaluation condEval = evaluateCardCondition(item);
            if (condEval.isAmbiguous()) {
                hasAmbiguousCard = true;
                String err = String.format("Card '%s' has missing or ambiguous condition/grading: %s", cardDesc, condEval.reason());
                unmetConditions.add(err);
                trace.add("AMBIGUOUS_CONDITION", err, "NEEDS_REVIEW");
                continue;
            }

            // Match first active buy rule
            BuyRule matchedRule = matchBuyRule(condEval, buyRules);
            BigDecimal rate = matchedRule.getRate();
            BigDecimal cardOffer = item.marketPriceCad().multiply(rate).multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            totalOfferAmount = totalOfferAmount.add(cardOffer);

            trace.add("BUY_RULE_" + matchedRule.getCategoryCode(),
                    String.format("%s (x%d) matched %s at %s%%", cardDesc, qty, matchedRule.getDisplayName(), rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()),
                    String.format("rate %s -> $%s CAD", rate, cardOffer), rate);
        }

        if (hasAmbiguousCard) {
            return buildNeedsReviewResult(TradeFlowType.SELL, totalMarketValue, BigDecimal.ZERO, trace,
                    "One or more cards have missing or ambiguous condition/pricing; manual review required.",
                    unmetConditions);
        }

        BigDecimal effectiveRate = totalMarketValue.compareTo(BigDecimal.ZERO) > 0
                ? totalOfferAmount.divide(totalMarketValue, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        trace.add("FINAL_OFFER",
                String.format("Calculated cash offer for %d items", customerCards.size()),
                String.format("$%s CAD (effective rate %s)", totalOfferAmount.setScale(2, RoundingMode.HALF_UP), effectiveRate),
                totalOfferAmount);

        return new PricingResult(
                TradeDecision.ACCEPT,
                TradeFlowType.SELL,
                totalMarketValue.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                totalOfferAmount.setScale(2, RoundingMode.HALF_UP),
                totalOfferAmount.setScale(2, RoundingMode.HALF_UP),
                totalOfferAmount.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                effectiveRate,
                effectiveRate,
                trace,
                String.format("Cash offer of $%s CAD generated at %s%% effective market rate.",
                        totalOfferAmount.setScale(2, RoundingMode.HALF_UP),
                        effectiveRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)),
                List.of()
        );
    }

    /**
     * Calculates trade credit for customer cards in exchange for store inventory cards.
     */
    public PricingResult evaluateTrade(List<CustomerCardItem> customerCards, List<StoreCardItem> storeCards) {
        RuleTrace trace = new RuleTrace();
        List<String> unmetConditions = new ArrayList<>();

        if (customerCards == null || customerCards.isEmpty()) {
            trace.add("EMPTY_CUSTOMER_CARDS", "No customer trade cards provided", "NEEDS_REVIEW");
            return buildNeedsReviewResult(TradeFlowType.TRADE, BigDecimal.ZERO, BigDecimal.ZERO, trace,
                    "No trade cards submitted", List.of("Customer cards missing"));
        }

        if (storeCards == null || storeCards.isEmpty()) {
            trace.add("EMPTY_STORE_CARDS", "No store target cards selected", "NEEDS_REVIEW");
            return buildNeedsReviewResult(TradeFlowType.TRADE, BigDecimal.ZERO, BigDecimal.ZERO, trace,
                    "No target cards selected from inventory", List.of("Store target cards missing"));
        }

        // 1. Calculate totals and check for missing/ambiguous card properties
        int totalCustomerCardCount = 0;
        BigDecimal mTotal = BigDecimal.ZERO;
        BigDecimal largestCustomerCardValue = BigDecimal.ZERO;
        boolean hasAmbiguousCard = false;

        for (int i = 0; i < customerCards.size(); i++) {
            CustomerCardItem item = customerCards.get(i);
            int qty = item.effectiveQuantity();
            totalCustomerCardCount += qty;
            String cardDesc = (item.name() != null ? item.name() : "Item #" + (i + 1));

            if (item.marketPriceCad() == null || item.marketPriceCad().compareTo(BigDecimal.ZERO) <= 0) {
                hasAmbiguousCard = true;
                String err = String.format("Trade card '%s' is missing market price", cardDesc);
                unmetConditions.add(err);
                trace.add("MISSING_PRICE", err, "NEEDS_REVIEW");
                continue;
            }

            BigDecimal singlePrice = item.marketPriceCad();
            if (singlePrice.compareTo(largestCustomerCardValue) > 0) {
                largestCustomerCardValue = singlePrice;
            }

            mTotal = mTotal.add(item.totalMarketPrice());

            ConditionEvaluation condEval = evaluateCardCondition(item);
            if (condEval.isAmbiguous()) {
                hasAmbiguousCard = true;
                String err = String.format("Trade card '%s' has ambiguous condition/grade: %s", cardDesc, condEval.reason());
                unmetConditions.add(err);
                trace.add("AMBIGUOUS_CONDITION", err, "NEEDS_REVIEW");
            }
        }

        BigDecimal targetStorePrice = BigDecimal.ZERO;
        BigDecimal defaultCostRatio = configService.getParameter(TradeConfigService.PARAM_DEFAULT_COST_BASIS_RATIO);
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        for (StoreCardItem sCard : storeCards) {
            targetStorePrice = targetStorePrice.add(sCard.totalListPrice());
            totalCostBasis = totalCostBasis.add(sCard.totalCostBasis(defaultCostRatio));
        }

        if (hasAmbiguousCard) {
            return buildNeedsReviewResult(TradeFlowType.TRADE, mTotal, targetStorePrice, trace,
                    "Customer cards contain ambiguous condition or price data; staff appraisal needed.",
                    unmetConditions);
        }

        if (mTotal.compareTo(BigDecimal.ZERO) <= 0 || targetStorePrice.compareTo(BigDecimal.ZERO) <= 0) {
            trace.add("ZERO_VALUATION", "Customer market value or target store price is zero", "DECLINE");
            return buildDeclineResult(TradeFlowType.TRADE, mTotal, targetStorePrice, trace,
                    "Trade cannot be evaluated for zero-value items.");
        }

        // 2. Volume threshold: more than 8 cards total -> NEEDS_REVIEW
        int maxCardsThreshold = configService.getParameter(TradeConfigService.PARAM_MAX_CUSTOMER_CARDS).intValue();
        if (totalCustomerCardCount > maxCardsThreshold) {
            trace.add("MAX_CARDS_EXCEEDED",
                    String.format("Customer offered %d cards, exceeding the %d card automated threshold", totalCustomerCardCount, maxCardsThreshold),
                    "NEEDS_REVIEW", totalCustomerCardCount);
            return buildNeedsReviewResult(TradeFlowType.TRADE, mTotal, targetStorePrice, trace,
                    String.format("Large collection trade (%d cards) requires in-person / manual review.", totalCustomerCardCount),
                    List.of(String.format("Offered card count %d exceeds automated threshold of %d", totalCustomerCardCount, maxCardsThreshold)));
        }

        // 3. Consolidation Rules:
        // largest = single most valuable card, target = total list price of store cards
        int consolidationCardCountThreshold = configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_THRESHOLD_COUNT).intValue();
        BigDecimal minLargestRatio = configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_MIN_LARGEST_RATIO); // 0.25
        BigDecimal penaltyThresholdRatio = configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_PENALTY_THRESHOLD); // 0.50
        BigDecimal consolidationPenalty = configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_PENALTY); // 0.05

        boolean applyConsolidationPenalty = false;

        if (totalCustomerCardCount >= consolidationCardCountThreshold) {
            BigDecimal largestRatio = largestCustomerCardValue.divide(targetStorePrice, 4, RoundingMode.HALF_UP);

            if (largestRatio.compareTo(minLargestRatio) < 0) {
                // largest < 25% of target -> DECLINE
                trace.add("CONSOLIDATION_DECLINE",
                        String.format("Customer offered %d cards with largest card ($%s) at %s%% of target ($%s) [< %s%%]",
                                totalCustomerCardCount,
                                largestCustomerCardValue.setScale(2, RoundingMode.HALF_UP),
                                largestRatio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                                targetStorePrice.setScale(2, RoundingMode.HALF_UP),
                                minLargestRatio.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()),
                        "DECLINE");
                return buildDeclineResult(TradeFlowType.TRADE, mTotal, targetStorePrice, trace,
                        String.format("We do not accept multiple small cards for higher-tier inventory unless the lead card constitutes at least %s%% of the target value.",
                                minLargestRatio.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()));
            } else if (largestRatio.compareTo(penaltyThresholdRatio) < 0) {
                // largest between 25% and 50% -> consolidation penalty
                applyConsolidationPenalty = true;
                trace.add("CONSOLIDATION_PENALTY",
                        String.format("Largest card ($%s) is %s%% of target [25%%-50%% band], applying %s penalty to r_max",
                                largestCustomerCardValue.setScale(2, RoundingMode.HALF_UP),
                                largestRatio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                                consolidationPenalty.negate()),
                        "-" + consolidationPenalty.stripTrailingZeros().toPlainString());
            }
        }

        // 4. Calculate r_max components
        BigDecimal f = configService.getParameter(TradeConfigService.PARAM_VARIABLE_RESALE_FEE); // 0.12
        BigDecimal bigF = configService.getParameter(TradeConfigService.PARAM_FIXED_HANDLING_FEE); // 0.50
        BigDecimal g = configService.getParameter(TradeConfigService.PARAM_TARGET_PROFIT_MARGIN); // 0.08
        BigDecimal hardCap = configService.getParameter(TradeConfigService.PARAM_HARD_CAP_RATE); // 0.90

        // Cost basis ratio c = totalCostBasis / targetStorePrice
        BigDecimal c = targetStorePrice.compareTo(BigDecimal.ZERO) > 0
                ? totalCostBasis.divide(targetStorePrice, 4, RoundingMode.HALF_UP)
                : defaultCostRatio;

        // Value-weighted liquidity haircut l
        BigDecimal totalWeightedHaircut = BigDecimal.ZERO;
        for (CustomerCardItem item : customerCards) {
            BigDecimal cardHaircut = configService.getCardLiquidityHaircut(item.pokemontcgId());
            BigDecimal cardVal = item.totalMarketPrice();
            totalWeightedHaircut = totalWeightedHaircut.add(cardHaircut.multiply(cardVal));
        }
        BigDecimal l = mTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalWeightedHaircut.divide(mTotal, 4, RoundingMode.HALF_UP)
                : configService.getParameter(TradeConfigService.PARAM_HAIRCUT_MEDIUM);

        // Fixed handling drag: (n * F) / M_total
        BigDecimal totalFixedHandling = bigF.multiply(BigDecimal.valueOf(totalCustomerCardCount));
        BigDecimal handlingDrag = totalFixedHandling.divide(mTotal, 6, RoundingMode.HALF_UP);

        // Numerator: (1 - f - l) - (n * F / M_total)
        BigDecimal numerator = BigDecimal.ONE.subtract(f).subtract(l).subtract(handlingDrag);

        // Denominator: c + g
        BigDecimal denominator = c.add(g);

        BigDecimal rawRMax = numerator.divide(denominator, 4, RoundingMode.HALF_UP);

        trace.add("BASE_R_MAX",
                String.format("Formula: [(1 - f:%.2f - l:%.4f) - (n:%d * F:%.2f / M:%.2f)] / (c:%.4f + g:%.2f)",
                        f, l, totalCustomerCardCount, bigF, mTotal, c, g),
                String.format("raw r_max = %s", rawRMax), rawRMax);

        if (l.compareTo(BigDecimal.ZERO) > 0) {
            trace.add("LIQUIDITY_HAIRCUT", String.format("Weighted liquidity haircut: -%s", l), "-" + l);
        }

        // Apply consolidation penalty if triggered
        BigDecimal adjustedRMax = rawRMax;
        if (applyConsolidationPenalty) {
            adjustedRMax = adjustedRMax.subtract(consolidationPenalty);
            trace.add("ADJUSTED_R_MAX", "r_max after consolidation penalty", adjustedRMax.toString(), adjustedRMax);
        }

        // Cap r_max at hard cap (0.90)
        BigDecimal rMax = adjustedRMax.min(hardCap);
        if (adjustedRMax.compareTo(hardCap) > 0) {
            trace.add("HARD_CAP", String.format("Capped from %s down to hard ceiling of %s", adjustedRMax, hardCap), hardCap.toString());
        }

        // Calculate r_needed = list price of my cards / market value of theirs
        BigDecimal rNeeded = targetStorePrice.divide(mTotal, 4, RoundingMode.HALF_UP);
        trace.add("R_NEEDED", String.format("Rate needed to cover target ($%s / $%s)", targetStorePrice, mTotal), rNeeded.toString(), rNeeded);

        BigDecimal openingDiscount = configService.getParameter(TradeConfigService.PARAM_OPENING_OFFER_DISCOUNT); // 0.03
        BigDecimal rOpening = rMax.subtract(openingDiscount).max(BigDecimal.ZERO);

        // 5. Decision Evaluation
        if (rNeeded.compareTo(rMax) <= 0) {
            // ACCEPT
            BigDecimal walkAwayCredit = rMax.multiply(mTotal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal openingCredit = rOpening.multiply(mTotal).min(targetStorePrice).setScale(2, RoundingMode.HALF_UP);

            trace.add("TRADE_ACCEPT",
                    String.format("r_needed (%s) <= r_max (%s): Trade Accepted", rNeeded, rMax),
                    String.format("Credit offered: $%s CAD (opening %s%%)", openingCredit, rOpening.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));

            return new PricingResult(
                    TradeDecision.ACCEPT,
                    TradeFlowType.TRADE,
                    mTotal.setScale(2, RoundingMode.HALF_UP),
                    targetStorePrice.setScale(2, RoundingMode.HALF_UP),
                    walkAwayCredit,
                    openingCredit,
                    walkAwayCredit,
                    BigDecimal.ZERO,
                    rMax,
                    rOpening,
                    trace,
                    String.format("Trade accepted. We can offer up to $%s in trade credit towards your selected card(s).",
                            openingCredit),
                    List.of()
            );
        } else {
            // Check for COUNTER offer with cash top-up
            // cash top-up = list price of my cards - (r_max * their market value)
            BigDecimal maxCustomerCredit = rMax.multiply(mTotal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal requiredTopUp = targetStorePrice.subtract(maxCustomerCredit).setScale(2, RoundingMode.HALF_UP);

            BigDecimal counterMaxTopUpRatio = configService.getParameter(TradeConfigService.PARAM_COUNTER_MAX_TOPUP_RATIO); // 0.25
            BigDecimal counterFloor = configService.getParameter(TradeConfigService.PARAM_COUNTER_FLOOR_RATE); // 0.55

            BigDecimal allowedMaxTopUp = targetStorePrice.multiply(counterMaxTopUpRatio).setScale(2, RoundingMode.HALF_UP);

            trace.add("COUNTER_EVALUATION",
                    String.format("Required top-up $%s CAD vs 25%% ceiling ($%s CAD), r_max %s vs floor %s",
                            requiredTopUp, allowedMaxTopUp, rMax, counterFloor),
                    null);

            if (requiredTopUp.compareTo(allowedMaxTopUp) <= 0 && rMax.compareTo(counterFloor) >= 0) {
                // COUNTER
                BigDecimal openingCredit = rOpening.multiply(mTotal).setScale(2, RoundingMode.HALF_UP);
                BigDecimal openingTopUp = targetStorePrice.subtract(openingCredit).setScale(2, RoundingMode.HALF_UP);

                trace.add("TRADE_COUNTER",
                        String.format("Counter with cash top-up of $%s CAD", openingTopUp),
                        "COUNTER", openingTopUp);

                return new PricingResult(
                        TradeDecision.COUNTER,
                        TradeFlowType.TRADE,
                        mTotal.setScale(2, RoundingMode.HALF_UP),
                        targetStorePrice.setScale(2, RoundingMode.HALF_UP),
                        maxCustomerCredit,
                        openingCredit,
                        maxCustomerCredit,
                        openingTopUp,
                        rMax,
                        rOpening,
                        trace,
                        String.format("We can offer $%s in trade credit plus a cash top-up of $%s CAD to complete this trade.",
                                openingCredit, openingTopUp),
                        List.of()
                );
            } else {
                // DECLINE
                String reason;
                if (rMax.compareTo(counterFloor) < 0) {
                    reason = String.format("Credit rate (%s%%) is below the minimum allowable floor (%s%%).",
                            rMax.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                            counterFloor.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
                } else {
                    reason = String.format("Cash top-up ($%s CAD) exceeds our allowable 25%% ceiling ($%s CAD) for this target card.",
                            requiredTopUp, allowedMaxTopUp);
                }

                trace.add("TRADE_DECLINE", reason, "DECLINE");
                return buildDeclineResult(TradeFlowType.TRADE, mTotal, targetStorePrice, trace, reason);
            }
        }
    }

    // --- Helper Methods ---

    private ConditionEvaluation evaluateCardCondition(CustomerCardItem item) {
        if (Boolean.TRUE.equals(item.isSealed())) {
            return new ConditionEvaluation(false, false, true, "SEALED", null);
        }

        String grading = item.grading() != null ? item.grading().trim().toUpperCase(Locale.ROOT) : "";
        if (!grading.isEmpty()) {
            // Check for ambiguous grading
            if (grading.contains("?") || grading.equals("GRADED") || grading.equals("SLAB") ||
                    grading.equals("PSA") || grading.equals("BGS") || grading.equals("CGC")) {
                return new ConditionEvaluation(true, false, false, null, "Grading company or score is ambiguous: " + item.grading());
            }

            boolean isPsa10 = grading.contains("PSA 10") || grading.equals("PSA10") || grading.contains("GEM MT 10");
            boolean isBgsBlackLabel = grading.contains("BLACK LABEL") || (grading.contains("BGS 10") && grading.contains("BLACK"));

            if (isPsa10 || isBgsBlackLabel) {
                return new ConditionEvaluation(false, true, false, "PSA10_BGS_BLACK_LABEL", null);
            }

            // Other graded card
            return new ConditionEvaluation(false, true, false, "OTHER_GRADED", null);
        }

        // Raw single card condition
        String condition = item.condition() != null ? item.condition().trim().toUpperCase(Locale.ROOT) : "";
        if (condition.isEmpty() || condition.contains("?") || condition.equals("UNKNOWN") || condition.equals("UNSURE")) {
            return new ConditionEvaluation(true, false, false, null, "Raw single card is missing valid condition tier");
        }

        if (condition.equals("NM") || condition.equals("NEAR MINT") || condition.equals("MINT") || condition.equals("NM/M") || condition.equals("NEAR-MINT")) {
            return new ConditionEvaluation(false, false, false, "RAW_NEAR_MINT", null);
        }

        if (condition.equals("LP") || condition.equals("LIGHTLY PLAYED") ||
                condition.equals("MP") || condition.equals("MODERATELY PLAYED") ||
                condition.equals("HP") || condition.equals("HEAVILY PLAYED") ||
                condition.equals("DMG") || condition.equals("DAMAGED")) {
            return new ConditionEvaluation(false, false, false, "DEFAULT", null);
        }

        // Unrecognized text
        return new ConditionEvaluation(true, false, false, null, "Unrecognized condition specification: " + item.condition());
    }

    private BuyRule matchBuyRule(ConditionEvaluation cond, List<BuyRule> buyRules) {
        // Priority order:
        // 1. PSA 10 or BGS Black Label
        // 2. Sealed product
        // 3. Near-mint raw single
        // 4. Everything else
        String targetCategoryCode;
        if ("PSA10_BGS_BLACK_LABEL".equals(cond.matchedTier())) {
            targetCategoryCode = "PSA10_BGS_BLACK_LABEL";
        } else if (cond.isSealed()) {
            targetCategoryCode = "SEALED";
        } else if ("RAW_NEAR_MINT".equals(cond.matchedTier())) {
            targetCategoryCode = "RAW_NEAR_MINT";
        } else {
            targetCategoryCode = "DEFAULT";
        }

        for (BuyRule rule : buyRules) {
            if (rule.getActive() && rule.getCategoryCode().equalsIgnoreCase(targetCategoryCode)) {
                return rule;
            }
        }

        // Fallback default
        return buyRules.stream()
                .filter(r -> "DEFAULT".equalsIgnoreCase(r.getCategoryCode()))
                .findFirst()
                .orElse(BuyRule.builder().categoryCode("DEFAULT").displayName("Everything Else").rate(BigDecimal.valueOf(0.75)).active(true).build());
    }

    private PricingResult buildNeedsReviewResult(
            TradeFlowType flowType,
            BigDecimal mTotal,
            BigDecimal storeListPrice,
            RuleTrace trace,
            String summaryReason,
            List<String> unmetConditions
    ) {
        return new PricingResult(
                TradeDecision.NEEDS_REVIEW,
                flowType,
                mTotal != null ? mTotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                storeListPrice != null ? storeListPrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                trace,
                summaryReason,
                unmetConditions
        );
    }

    private PricingResult buildDeclineResult(
            TradeFlowType flowType,
            BigDecimal mTotal,
            BigDecimal storeListPrice,
            RuleTrace trace,
            String summaryReason
    ) {
        return new PricingResult(
                TradeDecision.DECLINE,
                flowType,
                mTotal != null ? mTotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                storeListPrice != null ? storeListPrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                trace,
                summaryReason,
                List.of(summaryReason)
        );
    }

    private record ConditionEvaluation(
            boolean isAmbiguous,
            boolean isGraded,
            boolean isSealed,
            String matchedTier,
            String reason
    ) {}
}
