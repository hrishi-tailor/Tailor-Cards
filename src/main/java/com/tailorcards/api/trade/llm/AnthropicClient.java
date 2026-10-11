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
import java.util.ArrayList;
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
        this.apiKey = cleanKey(apiKey);
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

    /**
     * Strips terminal bracketed-paste markers (ESC[200~ / ESC[201~), control characters and
     * whitespace that pasting a key into a shell prompt can leave behind; they cause HTTP 401.
     */
    static String cleanKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        return apiKey.replaceAll("\u001B?\\[20[01]~", "")
                .replaceAll("\\p{Cntrl}", "")
                .trim();
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
        return sendRaw(systemPrompt, messages, null).map(raw -> {
            String text = "";
            JsonNode contentArray = raw.root().path("content");
            if (contentArray.isArray() && !contentArray.isEmpty()) {
                text = contentArray.get(0).path("text").asText("");
            }
            return new AnthropicResponse(text, raw.inputTokens(), raw.outputTokens(), raw.latencyMs(), raw.costUsd());
        });
    }

    /** Raw Messages API response with usage and computed cost. */
    public record RawResponse(JsonNode root, int inputTokens, int outputTokens, long latencyMs, BigDecimal costUsd) {}

    /**
     * Single Messages API call, optionally with tool definitions. Logs model, latency, tokens and
     * cost only; request and response bodies are never logged.
     */
    public Optional<RawResponse> sendRaw(String systemPrompt, List<? extends Map<String, ?>> messages,
                                         List<Map<String, Object>> tools) {
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
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools);
            }

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
            int inTokens = root.path("usage").path("input_tokens").asInt(0);
            int outTokens = root.path("usage").path("output_tokens").asInt(0);

            BigDecimal estimatedCostUsd = calculateEstimatedCost(inTokens, outTokens);

            log.info("Anthropic call completed: model='{}', latency={}ms, inputTokens={}, outputTokens={}, totalTokens={}, actualCostUsd=${}",
                    this.model, latencyMs, inTokens, outTokens, (inTokens + outTokens), estimatedCostUsd);

            return Optional.of(new RawResponse(root, inTokens, outTokens, latencyMs, estimatedCostUsd));
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.warn("Anthropic API call failed after {}ms: {}", latencyMs, failureReason(ex));
            return Optional.empty();
        }
    }

    /** Executes one tool call; the returned string is sent back to the model as the tool result. */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String toolName, JsonNode input);
    }

    /**
     * Result of a tool-use conversation turn. {@code failed} means an API call failed (text may be
     * partial or empty); tokens and cost always cover every call that completed.
     */
    public record ToolLoopResult(String text, int toolRounds, boolean hitRoundLimit, boolean failed,
                                 int inputTokens, int outputTokens, BigDecimal costUsd) {}

    /**
     * Runs the Messages API tool-use loop: the model may call tools for at most {@code maxToolRounds}
     * rounds; tool results go back as tool_result blocks. Tool names are logged, inputs and results never.
     */
    public ToolLoopResult runToolLoop(String systemPrompt, List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools, ToolExecutor executor, int maxToolRounds) {
        List<Map<String, Object>> conversation = new ArrayList<>(messages);
        int rounds = 0;
        int inTokens = 0;
        int outTokens = 0;
        BigDecimal cost = BigDecimal.ZERO;
        StringBuilder text = new StringBuilder();

        while (true) {
            Optional<RawResponse> response = sendRaw(systemPrompt, conversation, tools);
            if (response.isEmpty()) {
                return new ToolLoopResult(text.toString().trim(), rounds, false, true, inTokens, outTokens, cost);
            }
            RawResponse raw = response.get();
            inTokens += raw.inputTokens();
            outTokens += raw.outputTokens();
            cost = cost.add(raw.costUsd());

            JsonNode content = raw.root().path("content");
            List<JsonNode> toolUses = new ArrayList<>();
            text.setLength(0); // keep only the latest assistant text
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    text.append(block.path("text").asText(""));
                } else if ("tool_use".equals(type)) {
                    toolUses.add(block);
                }
            }

            boolean wantsTools = "tool_use".equals(raw.root().path("stop_reason").asText()) && !toolUses.isEmpty();
            if (!wantsTools) {
                return new ToolLoopResult(text.toString().trim(), rounds, false, false, inTokens, outTokens, cost);
            }
            if (rounds >= maxToolRounds) {
                log.warn("Tool loop stopped at the {}-round limit", maxToolRounds);
                return new ToolLoopResult(text.toString().trim(), rounds, true, false, inTokens, outTokens, cost);
            }
            rounds++;

            conversation.add(Map.of("role", "assistant", "content", objectMapper.convertValue(content, List.class)));
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode toolUse : toolUses) {
                String name = toolUse.path("name").asText();
                Map<String, Object> result = new HashMap<>();
                result.put("type", "tool_result");
                result.put("tool_use_id", toolUse.path("id").asText());
                try {
                    result.put("content", executor.execute(name, toolUse.path("input")));
                } catch (Exception e) {
                    result.put("content", "{\"error\":\"" + (e.getMessage() == null ? "Tool failed" : e.getMessage().replace("\"", "'")) + "\"}");
                    result.put("is_error", true);
                }
                log.info("Tool executed: {}", name);
                results.add(result);
            }
            conversation.add(Map.of("role", "user", "content", results));
        }
    }

    /**
     * Status plus Anthropic's error type and a short message (e.g. "HTTP 401 authentication_error:
     * invalid x-api-key"); never the request body. Message is trimmed so request text can't spill into logs.
     */
    private String failureReason(Exception ex) {
        if (ex instanceof RestClientResponseException rre) {
            String reason = "HTTP " + rre.getStatusCode().value();
            try {
                JsonNode error = objectMapper.readTree(rre.getResponseBodyAsString()).path("error");
                String type = error.path("type").asText("");
                String message = error.path("message").asText("").replaceAll("\\s+", " ");
                if (message.length() > 160) {
                    message = message.substring(0, 160) + "...";
                }
                if (!type.isEmpty()) {
                    reason += " " + type + (message.isEmpty() ? "" : ": " + message);
                }
            } catch (Exception ignored) {
                // body wasn't JSON
            }
            return reason;
        }
        // Network problems: name the root cause (e.g. SocketTimeoutException, UnknownHostException)
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root == ex ? "" : " caused by " + root.getClass().getSimpleName();
        if (root instanceof java.net.SocketTimeoutException || root instanceof java.net.http.HttpTimeoutException) {
            detail += " (no answer from api.anthropic.com in time; check the network)";
        } else if (root instanceof java.net.UnknownHostException) {
            detail += " (can't resolve api.anthropic.com; check DNS or network)";
        } else if (root instanceof java.net.ConnectException) {
            detail += " (connection refused or blocked; check firewall/VPN)";
        }
        return ex.getClass().getSimpleName() + detail;
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
