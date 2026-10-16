package com.tailorcards.api.buylistchat.resolution;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.BuylistLlmBudget;
import com.tailorcards.api.buylistchat.intake.ItemInput;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.AnthropicResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A stand-in for the Claude list normaliser with production-like timing. Latency follows a least-squares fit of
 * 40 live evaluation runs on Claude Haiku 4.5 (R² 0.98): 385 ms per call + 6.02 ms per output token + 0.101 ms per
 * input token. Replies are what the model returns for the lot: the Java parser's reading of each line, written in
 * the reply format the current prompt asks for (every field, or only the non-default ones).
 */
final class StubClaude {

    static final double MS_PER_CALL = 385;
    static final double MS_PER_OUTPUT_TOKEN = 6.02;
    static final double MS_PER_INPUT_TOKEN = 0.101;
    /** JSON averages about 3.2 characters per token, prose about 3.8. */
    static final double JSON_CHARS_PER_TOKEN = 3.2;
    static final double TEXT_CHARS_PER_TOKEN = 3.8;
    private static final Pattern LINE = Pattern.compile("(?m)^(\\d+)\\. (.*)$");

    final AnthropicClient client = mock(AnthropicClient.class);
    final BuylistLlmBudget budget = mock(BuylistLlmBudget.class);
    final AtomicInteger calls = new AtomicInteger();
    final AtomicLong outputTokens = new AtomicLong();
    private final ItemNormalizer parser;

    StubClaude() {
        parser = new ItemNormalizer(mock(AnthropicClient.class), mock(BuylistLlmBudget.class), new BuylistChatProperties());
        when(client.withSettings(anyString(), anyInt(), anyInt())).thenReturn(client);
        when(client.isConfigured()).thenReturn(true);
        when(budget.hasBudget()).thenReturn(true);
        when(client.sendMessage(anyString(), anyList())).thenAnswer(inv -> reply(inv.getArgument(0), inv.getArgument(1)));
    }

    private Optional<AnthropicResponse> reply(String system, List<Map<String, String>> messages) throws InterruptedException {
        String user = messages.getLast().get("content");
        boolean compact = system.contains("Leave out");
        ArrayNode out = BuylistBench.JSON.createArrayNode();
        Matcher m = LINE.matcher(user);
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            Optional<ItemInput> item = parser.parseHeuristic(m.group(2));
            ObjectNode node = out.addObject();
            node.put("i", i);
            if (item.isEmpty()) {
                node.put("skip", true);
                continue;
            }
            ItemInput it = item.get();
            node.put("name", it.name());
            field(node, "set", it.setName(), compact);
            field(node, "number", it.cardNumber(), compact);
            if (!compact || it.quantity() != 1) node.put("quantity", it.quantity());
            if (!compact || (it.condition() != null && !"UNKNOWN".equals(it.condition()))) node.put("condition", it.condition() == null ? "UNKNOWN" : it.condition());
            field(node, "variant", it.variant(), compact);
            field(node, "grading", it.grading(), compact);
            if (!compact || "BULK".equals(it.kind())) node.put("bulk", "BULK".equals(it.kind()));
            if (!compact || "SEALED".equals(it.kind())) node.put("sealed", "SEALED".equals(it.kind()));
        }
        String text = out.toString();
        long in = Math.round((system.length() + user.length()) / TEXT_CHARS_PER_TOKEN);
        long outTokens = Math.round(text.length() / JSON_CHARS_PER_TOKEN);
        long ms = Math.round(MS_PER_CALL + MS_PER_OUTPUT_TOKEN * outTokens + MS_PER_INPUT_TOKEN * in);
        calls.incrementAndGet();
        outputTokens.addAndGet(outTokens);
        Thread.sleep(ms);
        return Optional.of(new AnthropicResponse(text, (int) in, (int) outTokens, ms, BigDecimal.ZERO));
    }

    private static void field(ObjectNode node, String key, String value, boolean compact) {
        if (value != null) {
            node.put(key, value);
        } else if (!compact) {
            node.putNull(key);
        }
    }

    ItemNormalizer normalizer(BuylistChatProperties props) {
        return new ItemNormalizer(client, budget, props);
    }
}
