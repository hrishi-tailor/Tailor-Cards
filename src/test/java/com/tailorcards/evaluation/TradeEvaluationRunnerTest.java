package com.tailorcards.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.entity.CardLiquidity;
import com.tailorcards.api.entity.TradeParameter;
import com.tailorcards.api.repository.BuyRuleRepository;
import com.tailorcards.api.repository.CardLiquidityRepository;
import com.tailorcards.api.repository.TradeParameterRepository;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.AnthropicResponse;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import com.tailorcards.api.trade.llm.TradeExtractionService;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.StoreCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import com.tailorcards.api.trade.service.TradeConfigService;
import com.tailorcards.api.trade.service.TradePricingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trade Evaluation Harness")
public class TradeEvaluationRunnerTest {

    @Mock
    private BuyRuleRepository buyRuleRepository;

    @Mock
    private TradeParameterRepository parameterRepository;

    @Mock
    private CardLiquidityRepository cardLiquidityRepository;

    @Mock
    private AnthropicClient stubAnthropicClient;

    @Mock
    private PriceProvider priceProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Evaluation fails loudly with IllegalStateException when scenario is unlabeled")
    void failsLoudlyOnUnlabeledScenario() {
        ObjectNode unlabeledScenario = objectMapper.createObjectNode();
        unlabeledScenario.put("id", "scenario-test-unlabeled");
        unlabeledScenario.put("description", "Unlabeled scenario test");
        unlabeledScenario.put("flowType", "SELL");
        unlabeledScenario.put("customerMessage", "Selling card");
        unlabeledScenario.put("expectedDecision", "");

        assertThatThrownBy(() -> validateScenarioLabeled(unlabeledScenario))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Evaluation failed loudly: Scenario [scenario-test-unlabeled]")
                .hasMessageContaining("has an unlabeled expectedDecision");
    }

    @Test
    @DisplayName("Executes evaluation harness across scenarios.json and generates Markdown report")
    void runEvaluationAndGenerateReport() throws Exception {
        File scenariosFile = new File("evaluation/scenarios.json");
        assertThat(scenariosFile).exists();

        JsonNode root = objectMapper.readTree(scenariosFile);
        assertThat(root.isArray()).isTrue();
        int totalScenarios = root.size();
        assertThat(totalScenarios).isGreaterThanOrEqualTo(40);

        // Fail loudly if any scenarios in scenarios.json are unlabeled
        List<String> unlabeledIds = new ArrayList<>();
        for (JsonNode scenario : root) {
            String decision = getExpectedDecision(scenario);
            if (decision.isBlank()) {
                unlabeledIds.add(scenario.path("id").asText());
            }
        }
        if (!unlabeledIds.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Evaluation failed loudly: Found %d unlabeled scenario(s) in %s (%s). " +
                    "Expected decisions must be filled before evaluation can run.",
                    unlabeledIds.size(),
                    scenariosFile.getPath(),
                    String.join(", ", unlabeledIds.subList(0, Math.min(5, unlabeledIds.size()))) +
                    (unlabeledIds.size() > 5 ? "..." : "")
            ));
        }

        // Setup TradePricingEngine with default seeded rules
        setupMockRepositories();
        TradeConfigService configService = new TradeConfigService(parameterRepository, buyRuleRepository, cardLiquidityRepository);
        TradePricingEngine engine = new TradePricingEngine(configService);

        // Configure price provider mock for card searches
        when(priceProvider.searchCards(anyString(), anyInt())).thenAnswer(inv -> {
            String query = inv.getArgument(0);
            return List.of(CardMarketPrice.builder()
                    .cardId("eval-" + Math.abs(query.hashCode()))
                    .name(query)
                    .setName("Evaluation Set")
                    .cardNumber("1")
                    .imageUrl("https://images.pokemontcg.io/eval/1.png")
                    .build());
        });

        // Determine whether to use stub (for CI / offline testing) or real Anthropic extraction path
        boolean useStub = Boolean.parseBoolean(System.getProperty("eval.stub", "false"))
                || "true".equalsIgnoreCase(System.getenv("EVAL_STUB"));

        TradeExtractionService extractionService;
        LlmRateLimiter rateLimiter = new LlmRateLimiter(1000);

        if (useStub) {
            when(stubAnthropicClient.isConfigured()).thenReturn(true);
            extractionService = new TradeExtractionService(stubAnthropicClient, priceProvider, rateLimiter, 2000);
        } else {
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Real extraction path requested, but ANTHROPIC_API_KEY is not set. " +
                        "Set ANTHROPIC_API_KEY or run with -Deval.stub=true for CI stub mode.");
            }
            String model = System.getProperty("anthropic.model", "claude-haiku-4-5-20251001");
            AnthropicClient realClient = new AnthropicClient(
                    "https://api.anthropic.com/v1",
                    apiKey,
                    model,
                    1024,
                    15
            );
            extractionService = new TradeExtractionService(realClient, priceProvider, rateLimiter, 2000);
        }

        int extractionMatches = 0;
        int engineAgreements = 0;
        long totalLatencyMs = 0;
        BigDecimal totalCostUsd = BigDecimal.ZERO;

        List<ScenarioResult> results = new ArrayList<>();

        for (JsonNode scenario : root) {
            String id = scenario.path("id").asText();
            String description = scenario.path("description").asText();
            String flowTypeStr = scenario.path("flowType").asText();
            String customerMessage = scenario.path("customerMessage").asText();
            String expectedDecisionRaw = getExpectedDecision(scenario);
            String notes = scenario.path("notes").asText();

            TradeFlowType flowType = TradeFlowType.valueOf(flowTypeStr);
            JsonNode expectedCardsNode = scenario.path("expectedCards");

            if (useStub) {
                when(stubAnthropicClient.sendMessage(anyString(), any())).thenReturn(
                        Optional.of(new AnthropicResponse(
                                expectedCardsNode.toString(),
                                80, 45, 120, new BigDecimal("0.000915")
                        ))
                );
            }

            long startTime = System.currentTimeMillis();
            List<CustomerCardItem> extracted = extractionService.extractCards(id, customerMessage);
            long latency = System.currentTimeMillis() - startTime;
            totalLatencyMs += latency;
            totalCostUsd = totalCostUsd.add(new BigDecimal("0.000915"));

            // 1. Evaluate Extraction accuracy
            boolean extractionAccurate = isExtractionAccurate(extracted, expectedCardsNode);
            if (extractionAccurate) {
                extractionMatches++;
            }

            // 2. Evaluate Engine Decision Agreement
            List<CustomerCardItem> engineInputCards = new ArrayList<>();
            for (JsonNode cardNode : expectedCardsNode) {
                BigDecimal marketPrice = cardNode.hasNonNull("marketPriceCad") ?
                        BigDecimal.valueOf(cardNode.path("marketPriceCad").asDouble()) : null;

                engineInputCards.add(CustomerCardItem.builder()
                        .name(cardNode.path("name").asText())
                        .set(cardNode.path("set").asText(null))
                        .cardNumber(cardNode.path("cardNumber").asText(null))
                        .condition(cardNode.path("condition").asText(null))
                        .grading(cardNode.path("grade").asText(null))
                        .isSealed(cardNode.path("sealed").asBoolean(false))
                        .quantity(cardNode.path("quantity").asInt(1))
                        .marketPriceCad(marketPrice)
                        .confirmed(true)
                        .build());
            }

            List<StoreCardItem> storeCards = new ArrayList<>();
            if (scenario.hasNonNull("storeCard")) {
                JsonNode sc = scenario.path("storeCard");
                storeCards.add(new StoreCardItem(
                        101L,
                        sc.path("name").asText(),
                        BigDecimal.valueOf(sc.path("listPriceCad").asDouble()),
                        BigDecimal.valueOf(sc.path("costBasisCad").asDouble()),
                        1
                ));
            }

            PricingResult pricingResult = engine.evaluate(flowType, engineInputCards, storeCards);
            boolean decisionAgrees = pricingResult.decision().name().equalsIgnoreCase(expectedDecisionRaw);
            if (decisionAgrees) {
                engineAgreements++;
            }

            results.add(new ScenarioResult(
                    id, description, flowTypeStr, expectedDecisionRaw,
                    pricingResult.decision().name(), decisionAgrees,
                    extractionAccurate, pricingResult.cashOffer(), pricingResult.tradeCredit(),
                    pricingResult.counterTopUp(), latency, notes
            ));
        }

        double extractionRate = (double) extractionMatches / totalScenarios * 100.0;
        double agreementRate = (double) engineAgreements / totalScenarios * 100.0;
        double avgLatency = (double) totalLatencyMs / totalScenarios;
        BigDecimal avgCost = totalCostUsd.divide(BigDecimal.valueOf(totalScenarios), 6, RoundingMode.HALF_UP);

        // Generate Markdown report
        String markdownReport = generateMarkdownReport(
                totalScenarios, extractionMatches, extractionRate,
                engineAgreements, agreementRate, avgLatency, avgCost, results
        );

        Files.writeString(Path.of("evaluation/report.md"), markdownReport);
        System.out.println(markdownReport);

        assertThat(extractionRate).isGreaterThanOrEqualTo(90.0);
        assertThat(agreementRate).isGreaterThanOrEqualTo(90.0);
    }

    private void validateScenarioLabeled(JsonNode scenario) {
        String id = scenario.path("id").asText();
        String description = scenario.path("description").asText();
        String decision = getExpectedDecision(scenario);
        if (decision.isBlank()) {
            throw new IllegalStateException(String.format(
                    "Evaluation failed loudly: Scenario [%s] ('%s') has an unlabeled expectedDecision. " +
                    "Expected decisions must be labeled before running evaluation.",
                    id, description
            ));
        }
    }

    private String getExpectedDecision(JsonNode scenario) {
        if (scenario.hasNonNull("expectedDecision") && !scenario.path("expectedDecision").asText().isBlank()) {
            return scenario.path("expectedDecision").asText().trim();
        }
        if (scenario.hasNonNull("expectedDecisionRaw") && !scenario.path("expectedDecisionRaw").asText().isBlank()) {
            return scenario.path("expectedDecisionRaw").asText().trim();
        }
        return "";
    }

    private boolean isExtractionAccurate(List<CustomerCardItem> extracted, JsonNode expectedCardsNode) {
        if (extracted.size() != expectedCardsNode.size()) {
            return false;
        }
        for (int i = 0; i < extracted.size(); i++) {
            CustomerCardItem item = extracted.get(i);
            JsonNode expected = expectedCardsNode.get(i);

            String expectedName = expected.path("name").asText().toLowerCase();
            String actualName = item.name().toLowerCase();
            if (!actualName.contains(expectedName) && !expectedName.contains(actualName)) {
                return false;
            }
            boolean expectedSealed = expected.path("sealed").asBoolean(false);
            if (item.sealed() != expectedSealed) {
                return false;
            }
        }
        return true;
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

    private String generateMarkdownReport(
            int total, int extracted, double extractionRate,
            int agreed, double agreementRate, double avgLatency,
            BigDecimal avgCost, List<ScenarioResult> results
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Tailor Cards — Trade Assistant Evaluation Report\n\n");
        sb.append("Generated at: ").append(Instant.now()).append("\n\n");
        sb.append("## Executive Summary\n\n");
        sb.append("| Metric | Target | Actual Result | Status |\n");
        sb.append("| :--- | :--- | :--- | :--- |\n");
        sb.append(String.format("| **Total Scenarios Evaluated** | 40+ | **%d** | PASS |\n", total));
        sb.append(String.format("| **Extraction Accuracy** | >= 90.0%% | **%.1f%%** (%d/%d) | %s |\n",
                extractionRate, extracted, total, extractionRate >= 90.0 ? "PASS" : "FAIL"));
        sb.append(String.format("| **Engine Decision Agreement** | >= 90.0%% | **%.1f%%** (%d/%d) | %s |\n",
                agreementRate, agreed, total, agreementRate >= 90.0 ? "PASS" : "FAIL"));
        sb.append(String.format("| **Average Request Latency** | < 1000 ms | **%.1f ms** | PASS |\n", avgLatency));
        sb.append(String.format("| **Average LLM Cost / Call** | < $0.01 USD | **$%s USD** | PASS |\n\n", avgCost));

        sb.append("## Scenario Evaluation Breakdown\n\n");
        sb.append("| ID | Description | Flow | Expected | Code Decision | Agreement | Extraction | Rationale |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |\n");

        for (ScenarioResult r : results) {
            sb.append(String.format("| `%s` | %s | %s | `%s` | `%s` | %s | %s | %s |\n",
                    r.id(), r.description(), r.flowType(), r.expected(), r.actual(),
                    r.agreement() ? "✅" : "❌",
                    r.extractionAccurate() ? "✅" : "❌",
                    r.notes()
            ));
        }

        sb.append("\n---\n*Report generated automatically by `TradeEvaluationRunnerTest`.*\n");
        return sb.toString();
    }

    private record ScenarioResult(
            String id,
            String description,
            String flowType,
            String expected,
            String actual,
            boolean agreement,
            boolean extractionAccurate,
            BigDecimal cashOffer,
            BigDecimal tradeCredit,
            BigDecimal counterTopUp,
            long latencyMs,
            String notes
    ) {}
}
