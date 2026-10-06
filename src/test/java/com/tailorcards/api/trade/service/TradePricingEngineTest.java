package com.tailorcards.api.trade.service;

import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.StoreCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradePricingEngineTest {

    @Mock
    private TradeConfigService configService;

    private TradePricingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TradePricingEngine(configService);

        // Standard default parameters
        when(configService.getParameter(TradeConfigService.PARAM_VARIABLE_RESALE_FEE)).thenReturn(BigDecimal.valueOf(0.12));
        when(configService.getParameter(TradeConfigService.PARAM_FIXED_HANDLING_FEE)).thenReturn(BigDecimal.valueOf(0.50));
        when(configService.getParameter(TradeConfigService.PARAM_TARGET_PROFIT_MARGIN)).thenReturn(BigDecimal.valueOf(0.08));
        when(configService.getParameter(TradeConfigService.PARAM_DEFAULT_COST_BASIS_RATIO)).thenReturn(BigDecimal.valueOf(0.77));
        when(configService.getParameter(TradeConfigService.PARAM_HARD_CAP_RATE)).thenReturn(BigDecimal.valueOf(0.90));
        when(configService.getParameter(TradeConfigService.PARAM_COUNTER_FLOOR_RATE)).thenReturn(BigDecimal.valueOf(0.55));
        when(configService.getParameter(TradeConfigService.PARAM_COUNTER_MAX_TOPUP_RATIO)).thenReturn(BigDecimal.valueOf(0.25));
        when(configService.getParameter(TradeConfigService.PARAM_OPENING_OFFER_DISCOUNT)).thenReturn(BigDecimal.valueOf(0.03));
        when(configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_THRESHOLD_COUNT)).thenReturn(BigDecimal.valueOf(3));
        when(configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_MIN_LARGEST_RATIO)).thenReturn(BigDecimal.valueOf(0.25));
        when(configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_PENALTY_THRESHOLD)).thenReturn(BigDecimal.valueOf(0.50));
        when(configService.getParameter(TradeConfigService.PARAM_CONSOLIDATION_PENALTY)).thenReturn(BigDecimal.valueOf(0.05));
        when(configService.getParameter(TradeConfigService.PARAM_MAX_CUSTOMER_CARDS)).thenReturn(BigDecimal.valueOf(8));
        when(configService.getParameter(TradeConfigService.PARAM_HAIRCUT_MEDIUM)).thenReturn(BigDecimal.valueOf(0.03));

        // Default buy rules for selling
        when(configService.getActiveBuyRules()).thenReturn(List.of(
                BuyRule.builder().priority(1).categoryCode("PSA10_BGS_BLACK_LABEL").displayName("PSA 10 or BGS Black Label").rate(BigDecimal.valueOf(0.82)).active(true).build(),
                BuyRule.builder().priority(2).categoryCode("SEALED").displayName("Sealed Product").rate(BigDecimal.valueOf(0.70)).active(true).build(),
                BuyRule.builder().priority(3).categoryCode("RAW_NEAR_MINT").displayName("Near-Mint Raw Single").rate(BigDecimal.valueOf(0.77)).active(true).build(),
                BuyRule.builder().priority(4).categoryCode("DEFAULT").displayName("Everything Else").rate(BigDecimal.valueOf(0.75)).active(true).build()
        ));
    }

    // =========================================================================
    // SELLING FLOW TESTS (I PAY CASH)
    // =========================================================================

    @Test
    @DisplayName("Sell: PSA 10 card receives 82% cash offer")
    void evaluateSell_psa10_receives82Percent() {
        CustomerCardItem item = new CustomerCardItem(
                "Charizard GX", "Hidden Fates", "SV49", "sm115-SV49",
                null, "PSA 10 GEM MT", false, 1, BigDecimal.valueOf(500.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        assertEquals(TradeFlowType.SELL, result.flowType());
        assertEquals(BigDecimal.valueOf(500.00).setScale(2, RoundingMode.HALF_UP), result.totalCustomerMarketValue());
        // 500 * 0.82 = 410.00
        assertEquals(BigDecimal.valueOf(410.00).setScale(2, RoundingMode.HALF_UP), result.offerAmount());
        assertEquals(BigDecimal.valueOf(0.82).setScale(4, RoundingMode.HALF_UP), result.effectiveRate().setScale(4, RoundingMode.HALF_UP));
        assertNotNull(result.ruleTrace().toSummaryString());
    }

    @Test
    @DisplayName("Sell: BGS 10 Black Label card receives 82% cash offer")
    void evaluateSell_bgsBlackLabel_receives82Percent() {
        CustomerCardItem item = new CustomerCardItem(
                "Mewtwo GX", "Shining Legends", "78", "sm35-78",
                null, "BGS 10 Black Label", false, 1, BigDecimal.valueOf(1000.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        // 1000 * 0.82 = 820.00
        assertEquals(BigDecimal.valueOf(820.00).setScale(2, RoundingMode.HALF_UP), result.offerAmount());
    }

    @Test
    @DisplayName("Sell: Sealed product receives 70% cash offer")
    void evaluateSell_sealedProduct_receives70Percent() {
        CustomerCardItem item = new CustomerCardItem(
                "Evolving Skies Booster Box", "Evolving Skies", null, "swsh7-box",
                null, null, true, 2, BigDecimal.valueOf(600.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        // Total market: 2 * 600 = 1200.00. Offer: 1200 * 0.70 = 840.00
        assertEquals(BigDecimal.valueOf(1200.00).setScale(2, RoundingMode.HALF_UP), result.totalCustomerMarketValue());
        assertEquals(BigDecimal.valueOf(840.00).setScale(2, RoundingMode.HALF_UP), result.offerAmount());
    }

    @Test
    @DisplayName("Sell: Raw Near Mint single card receives 77% cash offer")
    void evaluateSell_nearMintRawSingle_receives77Percent() {
        CustomerCardItem item = new CustomerCardItem(
                "Gengar VMAX Alt Art", "Fusion Strike", "271", "swsh8-271",
                "Near Mint", null, false, 1, BigDecimal.valueOf(200.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        // 200 * 0.77 = 154.00
        assertEquals(BigDecimal.valueOf(154.00).setScale(2, RoundingMode.HALF_UP), result.offerAmount());
    }

    @Test
    @DisplayName("Sell: Raw Lightly Played single falls into Everything Else (75%)")
    void evaluateSell_lightlyPlayedRawSingle_receives75Percent() {
        CustomerCardItem item = new CustomerCardItem(
                "Blastoise Holo", "Base Set", "2", "base1-2",
                "Lightly Played", null, false, 1, BigDecimal.valueOf(100.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        // 100 * 0.75 = 75.00
        assertEquals(BigDecimal.valueOf(75.00).setScale(2, RoundingMode.HALF_UP), result.offerAmount());
    }

    @Test
    @DisplayName("Sell: PSA 9 slab falls into Everything Else (75%)")
    void evaluateSell_psa9Slab_receives75Percent() {
        CustomerCardItem item = new CustomerCardItem(
                "Pikachu Illustrator Copy", "Promo", null, "promo-1",
                null, "PSA 9 Mint", false, 1, BigDecimal.valueOf(300.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        // 300 * 0.75 = 225.00
        assertEquals(BigDecimal.valueOf(225.00).setScale(2, RoundingMode.HALF_UP), result.offerAmount());
    }

    @Test
    @DisplayName("Sell: Missing condition on raw card returns NEEDS_REVIEW")
    void evaluateSell_missingCondition_returnsNeedsReview() {
        CustomerCardItem item = new CustomerCardItem(
                "Charizard", "Base Set", "4", "base1-4",
                null, null, false, 1, BigDecimal.valueOf(400.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.NEEDS_REVIEW, result.decision());
        assertTrue(result.unmetConditions().stream().anyMatch(c -> c.toLowerCase().contains("condition")));
        assertTrue(result.ruleTrace().toSummaryString().contains("AMBIGUOUS_CONDITION"));
    }

    @Test
    @DisplayName("Sell: Ambiguous grade returns NEEDS_REVIEW")
    void evaluateSell_ambiguousGrading_returnsNeedsReview() {
        CustomerCardItem item = new CustomerCardItem(
                "Lugia", "Neo Genesis", "9", "neo1-9",
                null, "graded ?", false, 1, BigDecimal.valueOf(250.00), null
        );

        PricingResult result = engine.evaluateSell(List.of(item));

        assertEquals(TradeDecision.NEEDS_REVIEW, result.decision());
        assertTrue(result.unmetConditions().stream().anyMatch(c -> c.toLowerCase().contains("ambiguous")));
    }

    // =========================================================================
    // TRADING FLOW TESTS (FORMULA & CONSOLIDATION)
    // =========================================================================

    @Test
    @DisplayName("Trade: Expensive card receives a higher credit rate than a cheap card due to handling fee drag")
    void evaluateTrade_expensiveCardGetsHigherRateThanCheapCard() {
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.03));

        // Expensive card: 1 card worth $500 CAD (handling fee $0.50 is 0.1% of value)
        CustomerCardItem expensiveCard = new CustomerCardItem(
                "Charizard 1st Edition", "Base Set", "4", "base1-4",
                "Near Mint", null, false, 1, BigDecimal.valueOf(500.00), null
        );
        StoreCardItem storeTargetExpensive = new StoreCardItem(
                1L, "Target Slab", BigDecimal.valueOf(500.00), BigDecimal.valueOf(385.00), 1
        );

        PricingResult expensiveResult = engine.evaluateTrade(List.of(expensiveCard), List.of(storeTargetExpensive));

        // Cheap card: 1 card worth $2.50 CAD (handling fee $0.50 is 20% drag on value)
        CustomerCardItem cheapCard = new CustomerCardItem(
                "Caterpie", "Base Set", "45", "base1-45",
                "Near Mint", null, false, 1, BigDecimal.valueOf(2.50), null
        );
        StoreCardItem storeTargetCheap = new StoreCardItem(
                2L, "Target Single", BigDecimal.valueOf(2.50), BigDecimal.valueOf(1.92), 1
        );

        PricingResult cheapResult = engine.evaluateTrade(List.of(cheapCard), List.of(storeTargetCheap));

        assertTrue(expensiveResult.effectiveRate().compareTo(cheapResult.effectiveRate()) > 0,
                String.format("Expected expensive card rate (%s) to exceed cheap card rate (%s)",
                        expensiveResult.effectiveRate(), cheapResult.effectiveRate()));
    }

    @Test
    @DisplayName("Trade: Offering more than 8 cards triggers NEEDS_REVIEW volume limit")
    void evaluateTrade_tenCardsForFiftyDollarCard_triggersNeedsReview() {
        CustomerCardItem item = new CustomerCardItem(
                "Bulk Card", "Set", "1", "ptcg-1",
                "Near Mint", null, false, 10, BigDecimal.valueOf(5.00), null
        );
        StoreCardItem storeCard = new StoreCardItem(
                1L, "Mewtwo GX", BigDecimal.valueOf(50.00), BigDecimal.valueOf(35.00), 1
        );

        PricingResult result = engine.evaluateTrade(List.of(item), List.of(storeCard));

        assertEquals(TradeDecision.NEEDS_REVIEW, result.decision());
        assertTrue(result.ruleTrace().toSummaryString().contains("MAX_CARDS_EXCEEDED"));
    }

    @Test
    @DisplayName("Trade: Consolidation rule declines 4x $5 cards for a $50 card (largest < 25% of target)")
    void evaluateTrade_consolidation_declinesWhenLargestUnder25Percent() {
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.03));

        // 4 cards at $5 each ($20 total). Target card is $50.
        // Largest is $5. $5 / $50 = 10% < 25%.
        CustomerCardItem card1 = new CustomerCardItem("Card 1", "Set", "1", "ptcg-1", "Near Mint", null, false, 1, BigDecimal.valueOf(5.00), null);
        CustomerCardItem card2 = new CustomerCardItem("Card 2", "Set", "2", "ptcg-2", "Near Mint", null, false, 1, BigDecimal.valueOf(5.00), null);
        CustomerCardItem card3 = new CustomerCardItem("Card 3", "Set", "3", "ptcg-3", "Near Mint", null, false, 1, BigDecimal.valueOf(5.00), null);
        CustomerCardItem card4 = new CustomerCardItem("Card 4", "Set", "4", "ptcg-4", "Near Mint", null, false, 1, BigDecimal.valueOf(5.00), null);

        StoreCardItem storeCard = new StoreCardItem(1L, "Target $50 Card", BigDecimal.valueOf(50.00), BigDecimal.valueOf(38.00), 1);

        PricingResult result = engine.evaluateTrade(List.of(card1, card2, card3, card4), List.of(storeCard));

        assertEquals(TradeDecision.DECLINE, result.decision());
        assertTrue(result.ruleTrace().toSummaryString().contains("CONSOLIDATION_DECLINE"));
    }

    @Test
    @DisplayName("Trade: Consolidation rule applies penalty when largest card is between 25% and 50% of target")
    void evaluateTrade_consolidation_appliesPenaltyWhenLargestBetween25And50Percent() {
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.03));

        // 3 cards: $20, $20, $20 (total $60). Target card is $50.
        // Largest is $20. $20 / $50 = 40% (between 25% and 50%).
        CustomerCardItem c1 = new CustomerCardItem("Card 1", "Set", "1", "ptcg-1", "Near Mint", null, false, 1, BigDecimal.valueOf(20.00), null);
        CustomerCardItem c2 = new CustomerCardItem("Card 2", "Set", "2", "ptcg-2", "Near Mint", null, false, 1, BigDecimal.valueOf(20.00), null);
        CustomerCardItem c3 = new CustomerCardItem("Card 3", "Set", "3", "ptcg-3", "Near Mint", null, false, 1, BigDecimal.valueOf(20.00), null);

        StoreCardItem storeCard = new StoreCardItem(1L, "Target $50 Card", BigDecimal.valueOf(50.00), BigDecimal.valueOf(38.00), 1);

        PricingResult result = engine.evaluateTrade(List.of(c1, c2, c3), List.of(storeCard));

        assertTrue(result.ruleTrace().toSummaryString().contains("CONSOLIDATION_PENALTY"));
    }

    @Test
    @DisplayName("Trade: Liquidity haircut affects r_max (High 0.00 vs Medium 0.03 vs Low 0.08)")
    void evaluateTrade_liquidityHaircutEffects() {
        StoreCardItem storeCard = new StoreCardItem(1L, "Target Card", BigDecimal.valueOf(100.00), BigDecimal.valueOf(85.00), 1);

        // High liquidity card (haircut = 0.00)
        when(configService.getCardLiquidityHaircut("high-id")).thenReturn(BigDecimal.valueOf(0.00));
        CustomerCardItem highCard = new CustomerCardItem("High Card", "Set", "1", "high-id", "Near Mint", null, false, 1, BigDecimal.valueOf(100.00), null);
        PricingResult highResult = engine.evaluateTrade(List.of(highCard), List.of(storeCard));

        // Low liquidity card (haircut = 0.08)
        when(configService.getCardLiquidityHaircut("low-id")).thenReturn(BigDecimal.valueOf(0.08));
        CustomerCardItem lowCard = new CustomerCardItem("Low Card", "Set", "2", "low-id", "Near Mint", null, false, 1, BigDecimal.valueOf(100.00), null);
        PricingResult lowResult = engine.evaluateTrade(List.of(lowCard), List.of(storeCard));

        assertTrue(highResult.effectiveRate().compareTo(lowResult.effectiveRate()) > 0);
        // Difference should reflect the 0.08 haircut in the numerator
        assertTrue(highResult.ruleTrace().toSummaryString().contains("raw r_max"));
    }

    @Test
    @DisplayName("Trade: Maximum credit rate is capped at hard ceiling of 0.90")
    void evaluateTrade_hardCapAt90Percent() {
        // High liquidity (0.00 haircut), zero handling drag on huge card
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.00));

        // Store card with very low cost basis (e.g. 0.50 ratio), making denominator small
        StoreCardItem storeCard = new StoreCardItem(1L, "Store Grail", BigDecimal.valueOf(1000.00), BigDecimal.valueOf(400.00), 1);
        CustomerCardItem customerGrail = new CustomerCardItem("Customer Grail", "Set", "1", "grail-1", "Near Mint", null, false, 1, BigDecimal.valueOf(1200.00), null);

        PricingResult result = engine.evaluateTrade(List.of(customerGrail), List.of(storeCard));

        assertTrue(result.effectiveRate().compareTo(BigDecimal.valueOf(0.90)) <= 0);
        assertTrue(result.ruleTrace().toSummaryString().contains("HARD_CAP"));
    }

    @Test
    @DisplayName("Trade: Accept scenario when r_needed <= r_max")
    void evaluateTrade_acceptScenario() {
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.00));

        // Customer card: $150 CAD. Target store card: $100 CAD.
        // r_needed = 100 / 150 = 0.6667. r_max ~ 0.88.
        // r_needed <= r_max -> ACCEPT!
        CustomerCardItem customerCard = new CustomerCardItem("Card A", "Set", "1", "card-1", "Near Mint", null, false, 1, BigDecimal.valueOf(150.00), null);
        StoreCardItem storeCard = new StoreCardItem(1L, "Store Card", BigDecimal.valueOf(100.00), BigDecimal.valueOf(77.00), 1);

        PricingResult result = engine.evaluateTrade(List.of(customerCard), List.of(storeCard));

        assertEquals(TradeDecision.ACCEPT, result.decision());
        assertEquals(BigDecimal.ZERO, result.topUpAmount());
        assertTrue(result.ruleTrace().toSummaryString().contains("TRADE_ACCEPT"));
    }

    @Test
    @DisplayName("Trade: Counter offer with cash top-up when required top-up <= 25% of list price")
    void evaluateTrade_counterOfferScenario() {
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.03));

        // Target: $100 CAD. Customer card: $100 CAD.
        // r_max ~ 0.82. Customer card gets ~$82 credit.
        // Required top-up = $100 - $82 = $18 CAD (< 25% of $100).
        // Rate 0.82 >= floor 0.55.
        // Result: COUNTER!
        CustomerCardItem customerCard = new CustomerCardItem("Card B", "Set", "2", "card-2", "Near Mint", null, false, 1, BigDecimal.valueOf(100.00), null);
        StoreCardItem storeCard = new StoreCardItem(1L, "Store Card", BigDecimal.valueOf(100.00), BigDecimal.valueOf(77.00), 1);

        PricingResult result = engine.evaluateTrade(List.of(customerCard), List.of(storeCard));

        assertEquals(TradeDecision.COUNTER, result.decision());
        assertTrue(result.topUpAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.topUpAmount().compareTo(BigDecimal.valueOf(25.00)) <= 0);
        assertTrue(result.ruleTrace().toSummaryString().contains("TRADE_COUNTER"));
    }

    @Test
    @DisplayName("Trade: Decline when top-up exceeds 25% of store list price")
    void evaluateTrade_declineWhenTopUpExceeds25Percent() {
        when(configService.getCardLiquidityHaircut(anyString())).thenReturn(BigDecimal.valueOf(0.03));

        // Target: $200 CAD. Customer card: $100 CAD.
        // Customer card gets ~$82 credit.
        // Required top-up = $200 - $82 = $118 CAD (> 25% of $200 = $50).
        // Result: DECLINE!
        CustomerCardItem customerCard = new CustomerCardItem("Card C", "Set", "3", "card-3", "Near Mint", null, false, 1, BigDecimal.valueOf(100.00), null);
        StoreCardItem storeCard = new StoreCardItem(1L, "Expensive Store Card", BigDecimal.valueOf(200.00), BigDecimal.valueOf(154.00), 1);

        PricingResult result = engine.evaluateTrade(List.of(customerCard), List.of(storeCard));

        assertEquals(TradeDecision.DECLINE, result.decision());
        assertTrue(result.ruleTrace().toSummaryString().contains("TRADE_DECLINE"));
    }
}
