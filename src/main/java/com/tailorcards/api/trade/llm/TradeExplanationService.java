package com.tailorcards.api.trade.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class TradeExplanationService {

    private static final String SYSTEM_PROMPT = """
            You are a helpful, professional customer service assistant for Tailor Cards, an online Pokémon card boutique.
            Write a concise, friendly 1 to 3 sentence explanation for the customer explaining the result of their card evaluation.
            
            STRICT CONFIDENTIALITY & SAFETY RULES:
            - NEVER reveal or reference internal financial formulas, calculations, walk-away numbers, or proprietary business parameters.
            - Specifically, NEVER mention or hint at:
              * r_max, walk-away rate, or reservation prices
              * Target profit margins (g)
              * Variable platform/payment resale fees (f)
              * Fixed handling costs (F)
              * Store cost basis or product inventory costs (c)
              * Liquidity haircut percentages or liquidity tiers (l)
              * Consolidation rule penalties or thresholds
            - Communicate ONLY the final offer, credit, or cash top-up in CAD, with a courteous plain-language reason.
            - Maintain a clear, warm, and transparent customer-facing tone.
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TradeExplanationService(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    /**
     * Generates a friendly, plain-language customer explanation based strictly on the PricingResult.
     * Guaranteed never to leak r_max, internal margins, fees, or cost basis.
     */
    public String explainResult(PricingResult result, TradeFlowType flowType) {
        if (result == null) {
            return "No pricing evaluation available.";
        }

        if (anthropicClient.isConfigured()) {
            try {
                Map<String, Object> safeContext = new LinkedHashMap<>();
                safeContext.put("flowType", flowType != null ? flowType.name() : "SELL");
                safeContext.put("decision", result.decision() != null ? result.decision().name() : "NEEDS_REVIEW");
                safeContext.put("cashOfferCad", result.cashOffer());
                safeContext.put("tradeCreditCad", result.tradeCredit());
                safeContext.put("counterTopUpCad", result.counterTopUp());
                safeContext.put("customerMarketTotalCad", result.customerTotalMarketCad());
                safeContext.put("storeListTotalCad", result.storeTotalListPriceCad());

                String userPrompt = "Explain this pricing result to the customer in 1-2 friendly sentences:\n" +
                        objectMapper.writeValueAsString(safeContext);

                List<Map<String, String>> messages = List.of(
                        Map.of("role", "user", "content", userPrompt)
                );

                Optional<AnthropicResponse> response = anthropicClient.sendMessage(SYSTEM_PROMPT, messages);
                if (response.isPresent() && !response.get().text().isBlank()) {
                    String cleanExplanation = response.get().text().trim();
                    // Additional sanity check: ensure no internal leak keywords slipped through
                    if (!containsLeakKeywords(cleanExplanation)) {
                        return cleanExplanation;
                    } else {
                        log.warn("Anthropic explanation contained prohibited internal terms. Using deterministic fallback.");
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to generate LLM explanation, falling back to deterministic explanation: {}", ex.getMessage());
            }
        }

        return generateDeterministicExplanation(result, flowType);
    }

    public String generateDeterministicExplanation(PricingResult result, TradeFlowType flowType) {
        if (result.decision() == TradeDecision.NEEDS_REVIEW) {
            return "Your submission requires an in-person or manual appraisal. Please submit it so our staff can verify card condition and pricing.";
        }

        if (flowType == TradeFlowType.SELL) {
            if (result.cashOffer() != null && result.cashOffer().compareTo(BigDecimal.ZERO) > 0) {
                return String.format("We can offer $%.2f CAD cash for your card(s) based on current market value.",
                        result.cashOffer());
            } else {
                return "We are currently unable to make a cash offer for these items.";
            }
        }

        // TRADE flow
        return switch (result.decision()) {
            case ACCEPT -> String.format(
                    "Trade accepted! Your trade credit of $%.2f CAD fully covers the store inventory value of $%.2f CAD.",
                    result.tradeCredit(), result.storeTotalListPriceCad()
            );
            case COUNTER -> String.format(
                    "We can accept this trade with a cash top-up of $%.2f CAD (trade credit $%.2f CAD applied towards $%.2f CAD).",
                    result.counterTopUp(), result.tradeCredit(), result.storeTotalListPriceCad()
            );
            case DECLINE -> "The current market disparity or lot consolidation ratio prevents us from accepting this trade.";
            default -> "Trade status under review.";
        };
    }

    private boolean containsLeakKeywords(String text) {
        String lower = text.toLowerCase();
        return lower.contains("r_max") ||
                lower.contains("cost basis") ||
                lower.contains("target profit margin") ||
                lower.contains("platform fee") ||
                lower.contains("handling cost") ||
                lower.contains("liquidity haircut") ||
                lower.contains("consolidation penalty");
    }
}
