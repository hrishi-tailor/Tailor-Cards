package com.tailorcards.api.trade.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public AnthropicClient(
            @Value("${app.anthropic.base-url:https://api.anthropic.com/v1}") String baseUrl,
            @Value("${ANTHROPIC_API_KEY:${app.anthropic.api-key:}}") String apiKey,
            @Value("${ANTHROPIC_MODEL:${app.anthropic.model:claude-haiku-4-5-20251001}}") String model,
            @Value("${app.anthropic.max-tokens:1024}") int maxTokens,
            @Value("${app.anthropic.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "claude-haiku-4-5-20251001";
        this.maxTokens = maxTokens > 0 ? maxTokens : 1024;
        this.objectMapper = new ObjectMapper();
        this.restClient = buildRestClient(baseUrl, this.apiKey, timeoutSeconds);
    }

    private static RestClient buildRestClient(String baseUrl, String apiKey, int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (!apiKey.isEmpty()) {
            builder.defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", "2023-06-01");
        }

        return builder.build();
    }

    // Testing constructor
    public AnthropicClient(RestClient restClient, String model, int maxTokens) {
        this.restClient = restClient;
        this.baseUrl = null;
        this.apiKey = "test-key";
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns a client sharing this client's API key and base URL but with its own model,
     * max tokens and timeout (e.g. slower vision calls). Not a Spring bean.
     */
    public AnthropicClient withSettings(String model, int maxTokens, int timeoutSeconds) {
        String effectiveModel = model != null && !model.isBlank() ? model : this.model;
        if (baseUrl == null) {
            return new AnthropicClient(this.restClient, effectiveModel, maxTokens);
        }
        return new AnthropicClient(baseUrl, apiKey, effectiveModel, maxTokens, timeoutSeconds);
    }

    public String getModel() {
        return this.model;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Sends a request to Anthropic Messages API.
     * Logs input tokens, output tokens, latency, and estimated cost per call based on actual token usage.
     */
    public Optional<AnthropicResponse> sendMessage(String systemPrompt, List<Map<String, String>> messages) {
        return send(systemPrompt, messages);
    }

    /**
     * Sends messages whose content may be a list of content blocks (text and base64 images).
     * Same logging and cost accounting as {@link #sendMessage}; request and response bodies are never logged.
     */
    public Optional<AnthropicResponse> sendContentMessage(String systemPrompt, List<Map<String, Object>> messages) {
        return send(systemPrompt, messages);
    }

    private Optional<AnthropicResponse> send(String systemPrompt, List<? extends Map<String, ?>> messages) {
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

            log.info("Anthropic call completed: model='{}', latency={}ms, inputTokens={}, outputTokens={}, totalTokens={}, actualCostUsd=${}",
                    this.model, latencyMs, inTokens, outTokens, (inTokens + outTokens), estimatedCostUsd);

            return Optional.of(new AnthropicResponse(text, inTokens, outTokens, latencyMs, estimatedCostUsd));
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            // Status code or exception type only: error bodies and messages can echo request content
            String reason = ex instanceof RestClientResponseException rre
                    ? "HTTP " + rre.getStatusCode().value()
                    : ex.getClass().getSimpleName();
            log.warn("Anthropic API call failed after {}ms: {}", latencyMs, reason);
            return Optional.empty();
        }
    }

    public BigDecimal calculateEstimatedCost(int inTokens, int outTokens) {
        String m = this.model.toLowerCase();
        BigDecimal inRate;
        BigDecimal outRate;
        if (m.contains("haiku-4-5") || m.contains("haiku-4.5") || m.contains("haiku-4")) {
            // Haiku 4.5: $1.00 / 1M input ($0.00000100), $5.00 / 1M output ($0.00000500)
            inRate = new BigDecimal("0.00000100");
            outRate = new BigDecimal("0.00000500");
        } else if (m.contains("haiku")) {
            // Haiku 3 / 3.5: $0.80 / 1M input ($0.00000080), $4.00 / 1M output ($0.00000400)
            inRate = new BigDecimal("0.00000080");
            outRate = new BigDecimal("0.00000400");
        } else if (m.contains("opus")) {
            // Opus: $15.00 / 1M input, $75.00 / 1M output
            inRate = new BigDecimal("0.00001500");
            outRate = new BigDecimal("0.00007500");
        } else {
            // Sonnet / default: $3.00 / 1M input, $15.00 / 1M output
            inRate = new BigDecimal("0.00000300");
            outRate = new BigDecimal("0.00001500");
        }

        BigDecimal inCost = BigDecimal.valueOf(inTokens).multiply(inRate);
        BigDecimal outCost = BigDecimal.valueOf(outTokens).multiply(outRate);
        return inCost.add(outCost).setScale(6, RoundingMode.HALF_UP);
    }
}
