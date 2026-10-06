package com.tailorcards.api.trade.llm;

import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.RuleTrace;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeExplanationService Tests")
class TradeExplanationServiceTest {

    @Mock
    private AnthropicClient anthropicClient;

    private TradeExplanationService explanationService;

    @BeforeEach
    void setUp() {
        explanationService = new TradeExplanationService(anthropicClient);
    }

    @Test
    @DisplayName("Generates friendly explanation via Anthropic when configured")
    void testLlmExplanationSuccess() {
        when(anthropicClient.isConfigured()).thenReturn(true);
        when(anthropicClient.sendMessage(anyString(), any())).thenReturn(
                Optional.of(new AnthropicResponse(
                        "We'd love to buy your Charizard for $231.00 CAD cash!",
                        40, 20, 150, BigDecimal.valueOf(0.0004)
                ))
        );

        PricingResult result = PricingResult.builder()
                .flowType(TradeFlowType.SELL)
                .decision(TradeDecision.ACCEPT)
                .cashOffer(new BigDecimal("231.00"))
                .customerTotalMarketValueCad(new BigDecimal("300.00"))
                .ruleTrace(new RuleTrace())
                .build();

        String explanation = explanationService.explainResult(result, TradeFlowType.SELL);

        assertThat(explanation).isEqualTo("We'd love to buy your Charizard for $231.00 CAD cash!");
    }

    @Test
    @DisplayName("Intercepts and prevents leakage of internal proprietary parameters")
    void testPreventsInternalLeakage() {
        when(anthropicClient.isConfigured()).thenReturn(true);
        // LLM mistakenly generates internal variables
        when(anthropicClient.sendMessage(anyString(), any())).thenReturn(
                Optional.of(new AnthropicResponse(
                        "We evaluated your r_max at 0.86 with target profit margin 0.08 and cost basis 150.",
                        50, 25, 180, BigDecimal.valueOf(0.0005)
                ))
        );

        PricingResult result = PricingResult.builder()
                .flowType(TradeFlowType.SELL)
                .decision(TradeDecision.ACCEPT)
                .cashOffer(new BigDecimal("231.00"))
                .customerTotalMarketValueCad(new BigDecimal("300.00"))
                .ruleTrace(new RuleTrace())
                .build();

        String explanation = explanationService.explainResult(result, TradeFlowType.SELL);

        // Verify leak was rejected and deterministic explanation was returned instead
        assertThat(explanation).doesNotContain("r_max");
        assertThat(explanation).doesNotContain("profit margin");
        assertThat(explanation).doesNotContain("cost basis");
        assertThat(explanation).contains("$231.00 CAD cash");
    }

    @Test
    @DisplayName("Generates deterministic explanations across all trade decisions")
    void testDeterministicExplanations() {
        // 1. SELL cash offer
        PricingResult sellResult = PricingResult.builder()
                .flowType(TradeFlowType.SELL)
                .decision(TradeDecision.ACCEPT)
                .cashOffer(new BigDecimal("150.00"))
                .build();
        assertThat(explanationService.generateDeterministicExplanation(sellResult, TradeFlowType.SELL))
                .contains("$150.00 CAD cash");

        // 2. TRADE ACCEPT
        PricingResult tradeAccept = PricingResult.builder()
                .flowType(TradeFlowType.TRADE)
                .decision(TradeDecision.ACCEPT)
                .tradeCredit(new BigDecimal("200.00"))
                .storeTotalListPriceCad(new BigDecimal("180.00"))
                .build();
        assertThat(explanationService.generateDeterministicExplanation(tradeAccept, TradeFlowType.TRADE))
                .contains("Trade accepted!")
                .contains("$200.00 CAD");

        // 3. TRADE COUNTER
        PricingResult tradeCounter = PricingResult.builder()
                .flowType(TradeFlowType.TRADE)
                .decision(TradeDecision.COUNTER)
                .tradeCredit(new BigDecimal("100.00"))
                .counterTopUp(new BigDecimal("50.00"))
                .storeTotalListPriceCad(new BigDecimal("150.00"))
                .build();
        assertThat(explanationService.generateDeterministicExplanation(tradeCounter, TradeFlowType.TRADE))
                .contains("cash top-up of $50.00 CAD");

        // 4. TRADE DECLINE
        PricingResult tradeDecline = PricingResult.builder()
                .flowType(TradeFlowType.TRADE)
                .decision(TradeDecision.DECLINE)
                .build();
        assertThat(explanationService.generateDeterministicExplanation(tradeDecline, TradeFlowType.TRADE))
                .contains("disparity or lot consolidation");

        // 5. NEEDS_REVIEW
        PricingResult needsReview = PricingResult.builder()
                .decision(TradeDecision.NEEDS_REVIEW)
                .build();
        assertThat(explanationService.generateDeterministicExplanation(needsReview, TradeFlowType.SELL))
                .contains("manual appraisal");
    }
}
