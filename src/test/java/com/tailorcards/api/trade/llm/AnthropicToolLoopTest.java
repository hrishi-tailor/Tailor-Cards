package com.tailorcards.api.trade.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("AnthropicClient tool-use loop")
class AnthropicToolLoopTest {

    private static final String TOOL_CALL = """
            {"content":[{"type":"text","text":"Checking."},
                        {"type":"tool_use","id":"tu_1","name":"get_card","input":{"card_id":"base1-4"}}],
             "stop_reason":"tool_use","usage":{"input_tokens":100,"output_tokens":20}}
            """;
    private static final String FINAL = """
            {"content":[{"type":"text","text":"Charizard base1-4 is $928.32 market (TCGdex, updated 2026-10-08)."}],
             "stop_reason":"end_turn","usage":{"input_tokens":150,"output_tokens":30}}
            """;

    private static final List<Map<String, Object>> TOOLS = List.of(Map.of("name", "get_card", "description", "d",
            "input_schema", Map.of("type", "object")));

    private record Setup(AnthropicClient client, MockRestServiceServer server) {}

    private static Setup setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.anthropic.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new AnthropicClient(builder.build(), "claude-haiku-4-5-20251001", 512), server);
    }

    @Test
    @DisplayName("Executes tool calls, returns tool_result blocks, and sums usage and cost")
    void runsToolsAndReturnsFinalText() {
        Setup s = setup();
        s.server().expect(requestTo("https://api.anthropic.com/v1/messages")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"tools\"")))
                .andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        s.server().expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"tool_use_id\":\"tu_1\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("928.32")))
                .andRespond(withSuccess(FINAL, MediaType.APPLICATION_JSON));
        List<String> calls = new ArrayList<>();

        AnthropicClient.ToolLoopResult result = s.client().runToolLoop("sys",
                List.of(Map.of("role", "user", "content", "price of base1-4?")), TOOLS,
                (name, input) -> {
                    calls.add(name + ":" + input.path("card_id").asText());
                    return "{\"usd_market_by_variant\":{\"holofoil\":928.32}}";
                }, 5);

        s.server().verify();
        assertThat(calls).containsExactly("get_card:base1-4");
        assertThat(result.text()).contains("928.32");
        assertThat(result.toolRounds()).isEqualTo(1);
        assertThat(result.hitRoundLimit()).isFalse();
        assertThat(result.failed()).isFalse();
        assertThat(result.inputTokens()).isEqualTo(250);
        assertThat(result.outputTokens()).isEqualTo(50);
        assertThat(result.costUsd()).isEqualByComparingTo("0.000500");
    }

    @Test
    @DisplayName("Stops after the maximum number of tool rounds")
    void stopsAtRoundLimit() {
        Setup s = setup();
        s.server().expect(times(3), requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));

        AnthropicClient.ToolLoopResult result = s.client().runToolLoop("sys",
                List.of(Map.of("role", "user", "content", "loop")), TOOLS, (name, input) -> "{}", 2);

        s.server().verify();
        assertThat(result.toolRounds()).isEqualTo(2);
        assertThat(result.hitRoundLimit()).isTrue();
    }

    @Test
    @DisplayName("A tool exception becomes an is_error tool_result, not a crash")
    void toolErrorsAreReported() {
        Setup s = setup();
        s.server().expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        s.server().expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"is_error\":true")))
                .andRespond(withSuccess(FINAL, MediaType.APPLICATION_JSON));

        AnthropicClient.ToolLoopResult result = s.client().runToolLoop("sys",
                List.of(Map.of("role", "user", "content", "x")), TOOLS,
                (name, input) -> { throw new IllegalStateException("boom"); }, 5);

        assertThat(result.failed()).isFalse();
    }

    @Test
    @DisplayName("Upstream failure mid-loop reports failed=true with usage so far")
    void upstreamFailure() {
        Setup s = setup();
        s.server().expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withSuccess(TOOL_CALL, MediaType.APPLICATION_JSON));
        s.server().expect(requestTo("https://api.anthropic.com/v1/messages")).andRespond(withServerError());

        AnthropicClient.ToolLoopResult result = s.client().runToolLoop("sys",
                List.of(Map.of("role", "user", "content", "x")), TOOLS, (name, input) -> "{}", 5);

        assertThat(result.failed()).isTrue();
        assertThat(result.inputTokens()).isEqualTo(100);
    }
}
