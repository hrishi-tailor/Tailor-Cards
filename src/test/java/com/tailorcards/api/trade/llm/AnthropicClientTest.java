package com.tailorcards.api.trade.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("AnthropicClient Tests")
class AnthropicClientTest {

    @Test
    @DisplayName("Defaults to claude-haiku-4-5-20251001 when model is empty or null")
    void testDefaultModelIsHaiku45() {
        AnthropicClient client = new AnthropicClient("https://api.anthropic.com/v1", "key", null, 1024, 10);
        assertThat(client.getModel()).isEqualTo("claude-haiku-4-5-20251001");

        AnthropicClient clientBlank = new AnthropicClient("https://api.anthropic.com/v1", "key", "   ", 1024, 10);
        assertThat(clientBlank.getModel()).isEqualTo("claude-haiku-4-5-20251001");
    }

    @Test
    @DisplayName("Returns empty optional when API key is not configured")
    void testUnconfiguredReturnsEmpty() {
        AnthropicClient client = new AnthropicClient("https://api.anthropic.com/v1", "", "claude-haiku-4-5-20251001", 100, 5);
        assertThat(client.isConfigured()).isFalse();

        Optional<AnthropicResponse> response = client.sendMessage("system", List.of(Map.of("role", "user", "content", "hello")));
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("Calculates cost accurately based on actual token usage for claude-haiku-4-5-20251001")
    void testCalculateCostHaiku45() {
        AnthropicClient client = new AnthropicClient(RestClient.create(), "claude-haiku-4-5-20251001", 1024);
        // 1000 input @ $1.00/1M = $0.001000
        // 500 output @ $5.00/1M = $0.002500
        // Total = $0.003500
        BigDecimal cost = client.calculateEstimatedCost(1000, 500);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.003500"));
    }

    @Test
    @DisplayName("Parses Anthropic Messages response with token usage and cost calculation")
    void testSuccessfulMessageResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String jsonResponse = """
                {
                  "id": "msg_01",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "text",
                      "text": "[{\\"name\\":\\"Charizard\\",\\"quantity\\":1}]"
                    }
                  ],
                  "model": "claude-haiku-4-5-20251001",
                  "usage": {
                    "input_tokens": 100,
                    "output_tokens": 50
                  }
                }
                """;

        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        AnthropicClient client = new AnthropicClient(restClient, "claude-haiku-4-5-20251001", 1024);

        Optional<AnthropicResponse> response = client.sendMessage(
                "System prompt",
                List.of(Map.of("role", "user", "content", "I have a Charizard"))
        );

        server.verify();

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("Charizard");
        assertThat(response.get().inputTokens()).isEqualTo(100);
        assertThat(response.get().outputTokens()).isEqualTo(50);
        // 100 in ($0.000100) + 50 out ($0.000250) = $0.000350
        assertThat(response.get().estimatedCostUsd()).isEqualByComparingTo(new BigDecimal("0.000350"));
    }

    @Test
    @DisplayName("Safely handles HTTP error from Anthropic API and returns empty optional")
    void testHttpErrorHandling() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        server.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        AnthropicClient client = new AnthropicClient(restClient, "claude-haiku-4-5-20251001", 1024);

        Optional<AnthropicResponse> response = client.sendMessage(
                "System prompt",
                List.of(Map.of("role", "user", "content", "Test"))
        );

        server.verify();
        assertThat(response).isEmpty();
    }
}
