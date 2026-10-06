package com.tailorcards.api.trade.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AnthropicClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public AnthropicClient(
            @Value("${app.anthropic.base-url:https://api.anthropic.com/v1}") String baseUrl,
            @Value("${ANTHROPIC_API_KEY:${app.anthropic.api-key:}}") String apiKey,
            @Value("${app.anthropic.model:claude-3-5-sonnet-20241022}") String model,
            @Value("${app.anthropic.max-tokens:1024}") int maxTokens,
            @Value("${app.anthropic.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "claude-3-5-sonnet-20241022";
        this.maxTokens = maxTokens > 0 ? maxTokens : 1024;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (!this.apiKey.isEmpty()) {
            builder.defaultHeader("x-api-key", this.apiKey)
                    .defaultHeader("anthropic-version", "2023-06-01");
        }

        this.restClient = builder.build();
    }

    // Testing constructor
    public AnthropicClient(RestClient restClient, String model, int maxTokens) {
        this.restClient = restClient;
        this.apiKey = "test-key";
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Sends a request to Anthropic Messages API.
     * Logs input tokens, output tokens, latency, and estimated cost per call.
     */
    public Optional<AnthropicResponse> sendMessage(String systemPrompt, List<Map<String, String>> messages) {
        if (!isConfigured()) {
            log.info("Anthropic API key not configured. Using deterministic fallback.");
            return Optional.empty();
        }

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", this.model);
            requestBody.put("max_tokens", this.maxTokens);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                requestBody.put("system", systemPrompt);
            }
            requestBody.put("messages", messages);

            String requestJson = objectMapper.writeValueAsString(requestBody);

            String responseBody = restClient.post()
                    .uri("/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            long latencyMs = System.currentTimeMillis() - startTime;

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("Empty response received from Anthropic API (latency={}ms)", latencyMs);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String text = "";
            JsonNode contentArray = root.path("content");
            if (contentArray.isArray() && !contentArray.isEmpty()) {
                text = contentArray.get(0).path("text").asText("");
            }

            int inTokens = root.path("usage").path("input_tokens").asInt(0);
            int outTokens = root.path("usage").path("output_tokens").asInt(0);

            BigDecimal estimatedCostUsd = calculateEstimatedCost(inTokens, outTokens);

            log.info("Anthropic call completed: model={}, latency={}ms, inputTokens={}, outputTokens={}, estCostUsd=${}",
                    this.model, latencyMs, inTokens, outTokens, estimatedCostUsd);

            return Optional.of(new AnthropicResponse(text, inTokens, outTokens, latencyMs, estimatedCostUsd));
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("Anthropic API call failed after {}ms: {}", latencyMs, ex.getMessage());
            return Optional.empty();
        }
    }

    private BigDecimal calculateEstimatedCost(int inTokens, int outTokens) {
        // Sonnet rates: $3.00/1M input, $15.00/1M output
        // Haiku rates: $0.80/1M input, $4.00/1M output
        BigDecimal inRate = model.contains("haiku") ?
                new BigDecimal("0.0000008") : new BigDecimal("0.000003");
        BigDecimal outRate = model.contains("haiku") ?
                new BigDecimal("0.000004") : new BigDecimal("0.000015");

        BigDecimal inCost = BigDecimal.valueOf(inTokens).multiply(inRate);
        BigDecimal outCost = BigDecimal.valueOf(outTokens).multiply(outRate);
        return inCost.add(outCost).setScale(6, RoundingMode.HALF_UP);
    }
}
