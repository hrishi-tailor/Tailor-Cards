package com.tailorcards.evaluation;

import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.entity.TradeParameter;
import com.tailorcards.api.repository.BuyRuleRepository;
import com.tailorcards.api.repository.CardLiquidityRepository;
import com.tailorcards.api.repository.TradeParameterRepository;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.StoreCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import com.tailorcards.api.trade.service.TradeConfigService;
import com.tailorcards.api.trade.service.TradePricingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trade Backtesting Harness")
class TradeBacktesterTest {

    @Mock
    private BuyRuleRepository buyRuleRepository;

    @Mock
    private TradeParameterRepository parameterRepository;

    @Mock
    private CardLiquidityRepository cardLiquidityRepository;

    @Test
    @DisplayName("Replays historical trades from CSV dataset and reports decision agreement and offer variance")
    void replayTradesFromCsv() throws Exception {
        String csvPath = System.getProperty("backtest.csv", "evaluation/past_trades.csv");
        File csvFile = new File(csvPath);
        assertThat(csvFile).exists();

        setupMockRepositories();
        TradeConfigService configService = new TradeConfigService(parameterRepository, buyRuleRepository, cardLiquidityRepository);
        TradePricingEngine engine = new TradePricingEngine(configService);

        List<ReplayResult> results = new ArrayList<>();
        int agreements = 0;
        int total = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("trade_id,")) continue;
                String[] cols = line.split(",", -1);
                if (cols.length < 11) continue;

                total++;
                String tradeId = cols[0].trim();
                TradeFlowType flowType = TradeFlowType.valueOf(cols[1].trim());
                String cardName = cols[2].trim();
                String condition = cols[3].trim();
                String grade = cols[4].isBlank() ? null : cols[4].trim();
                boolean sealed = Boolean.parseBoolean(cols[5].trim());
                int qty = Integer.parseInt(cols[6].trim());
                BigDecimal marketPrice = new BigDecimal(cols[7].trim());

                String storeCardName = cols[8].isBlank() ? null : cols[8].trim();
                BigDecimal storeListPrice = cols[9].isBlank() ? null : new BigDecimal(cols[9].trim());
                String historicalDecision = cols[10].trim();
                BigDecimal historicalOffer = cols.length > 11 && !cols[11].isBlank() ?
                        new BigDecimal(cols[11].trim()) : BigDecimal.ZERO;

                List<CustomerCardItem> customerCards = List.of(CustomerCardItem.builder()
                        .name(cardName)
                        .condition(condition)
                        .grading(grade)
                        .isSealed(sealed)
                        .quantity(qty)
                        .marketPriceCad(marketPrice)
                        .confirmed(true)
                        .build());

                List<StoreCardItem> storeCards = new ArrayList<>();
                if (flowType == TradeFlowType.TRADE && storeListPrice != null) {
                    storeCards.add(new StoreCardItem(
                            1L,
                            storeCardName != null ? storeCardName : "Target Store Card",
                            storeListPrice,
                            storeListPrice.multiply(new BigDecimal("0.75")),
                            1
                    ));
                }

                PricingResult pricingResult = engine.evaluate(flowType, customerCards, storeCards);
                boolean agrees = pricingResult.decision().name().equalsIgnoreCase(historicalDecision);
                if (agrees) {
                    agreements++;
                }

                BigDecimal codeOffer = flowType == TradeFlowType.SELL ?
                        pricingResult.cashOffer() : pricingResult.tradeCredit();

                results.add(new ReplayResult(
                        tradeId, flowType.name(), cardName, historicalDecision,
                        pricingResult.decision().name(), agrees, historicalOffer, codeOffer
                ));
            }
        }

        assertThat(total).isGreaterThan(0);
        double agreementRate = (double) agreements / total * 100.0;

        System.out.println("========================================================================");
        System.out.printf("  TRADE BACKTEST REPORT (%s)%n", csvPath);
        System.out.println("========================================================================");
        System.out.printf("  Total Trades Replayed:    %d%n", total);
        System.out.printf("  Historical Agreement:     %.1f%% (%d/%d)%n", agreementRate, agreements, total);
        System.out.println("------------------------------------------------------------------------");
        System.out.printf("  %-8s %-6s %-25s %-10s %-10s %-8s%n", "TradeID", "Flow", "Card", "Past Dec", "Engine Dec", "Match");
        System.out.println("------------------------------------------------------------------------");
        for (ReplayResult r : results) {
            System.out.printf("  %-8s %-6s %-25s %-10s %-10s %-8s%n",
                    r.tradeId(), r.flowType(),
                    r.cardName().length() > 25 ? r.cardName().substring(0, 22) + "..." : r.cardName(),
                    r.pastDecision(), r.engineDecision(),
                    r.agrees() ? "MATCH" : "DIFF"
            );
        }
        System.out.println("========================================================================");

        assertThat(agreementRate).isGreaterThanOrEqualTo(90.0);
    }

    private void setupMockRepositories() {
        List<BuyRule> rules = List.of(
                BuyRule.builder().categoryCode("GRADED_GEM").rate(new BigDecimal("0.82")).priority(1).active(true).build(),
                BuyRule.builder().categoryCode("SEALED").rate(new BigDecimal("0.70")).priority(2).active(true).build(),
                BuyRule.builder().categoryCode("NEAR_MINT_RAW").rate(new BigDecimal("0.77")).priority(3).active(true).build(),
                BuyRule.builder().categoryCode("OTHER").rate(new BigDecimal("0.75")).priority(4).active(true).build()
        );
        when(buyRuleRepository.findAllByOrderByPriorityAsc()).thenReturn(rules);

        List<TradeParameter> params = List.of(
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_VARIABLE_RESALE_FEE).paramValue(new BigDecimal("0.12")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_FIXED_HANDLING_FEE).paramValue(new BigDecimal("0.50")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_TARGET_PROFIT_MARGIN).paramValue(new BigDecimal("0.08")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_DEFAULT_COST_BASIS_RATIO).paramValue(new BigDecimal("0.77")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_HARD_CAP_RATE).paramValue(new BigDecimal("0.90")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_COUNTER_MAX_TOPUP_RATIO).paramValue(new BigDecimal("0.25")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_COUNTER_FLOOR_RATE).paramValue(new BigDecimal("0.55")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_OPENING_OFFER_DISCOUNT).paramValue(new BigDecimal("0.03")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_CONSOLIDATION_THRESHOLD_COUNT).paramValue(new BigDecimal("3")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_CONSOLIDATION_MIN_LARGEST_RATIO).paramValue(new BigDecimal("0.25")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_CONSOLIDATION_PENALTY_THRESHOLD).paramValue(new BigDecimal("0.50")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_CONSOLIDATION_PENALTY).paramValue(new BigDecimal("0.05")).build(),
                TradeParameter.builder().paramKey(TradeConfigService.PARAM_MAX_CUSTOMER_CARDS).paramValue(new BigDecimal("8")).build()
        );
        when(parameterRepository.findAll()).thenReturn(params);
        when(cardLiquidityRepository.findAll()).thenReturn(List.of());
    }

    private record ReplayResult(
            String tradeId,
            String flowType,
            String cardName,
            String pastDecision,
            String engineDecision,
            boolean agrees,
            BigDecimal pastOffer,
            BigDecimal engineOffer
    ) {}
}
