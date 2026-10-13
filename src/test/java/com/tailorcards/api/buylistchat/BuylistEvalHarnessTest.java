package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tailorcards.api.buylistchat.BuylistEval.Case;
import com.tailorcards.api.buylistchat.BuylistEval.CaseResult;
import com.tailorcards.api.buylistchat.BuylistEval.Dataset;
import com.tailorcards.api.buylistchat.BuylistEval.Expect;
import com.tailorcards.api.buylistchat.BuylistEval.ExpectedAdd;
import com.tailorcards.api.buylistchat.BuylistEval.RecordedAdd;
import com.tailorcards.api.buylistchat.BuylistEval.Summary;
import com.tailorcards.api.buylistchat.BuylistEval.Transcript;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.AnthropicClient.ToolLoopResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The evaluation harness itself, checked offline (no model, no network) so it runs in CI on every push. */
@DisplayName("Buylist eval harness")
class BuylistEvalHarnessTest {

    private static final Path DIR = Path.of("evaluation/buylist");

    private static Expect expectAdds(ExpectedAdd... adds) {
        return new Expect(List.of(adds), false, false, List.of(), List.of(), List.of(), List.of(), null);
    }

    private static ExpectedAdd card(String id) {
        return new ExpectedAdd(id, List.of(), null, null, null);
    }

    private static Transcript transcript(List<RecordedAdd> adds, String... replies) {
        return new Transcript(List.of(), List.of(replies), adds, 1000, 100, new BigDecimal("0.0020"), 1500, 2, false, false);
    }

    private static RecordedAdd added(String cardId) {
        return new RecordedAdd(false, "Charizard V", "Brilliant Stars", "154", cardId, 1, null, "UNKNOWN");
    }

    @Test
    @DisplayName("Dataset: unique ids, every case has turns and something to check, card ids look like TCGdex ids")
    void datasetIsValid() throws Exception {
        Dataset dataset = BuylistEval.loadCases(DIR.resolve("cases.json"));
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(40);
        Set<String> ids = new HashSet<>();
        for (Case c : dataset.cases()) {
            assertThat(ids.add(c.id())).as("duplicate id " + c.id()).isTrue();
            assertThat(c.turns()).as(c.id()).isNotEmpty();
            Expect e = c.expect();
            boolean checksSomething = !e.adds().isEmpty() || e.noAdds() || !e.toolsCalled().isEmpty()
                    || !e.replyMatches().isEmpty() || !e.replyNotMatches().isEmpty() || e.dealType() != null;
            assertThat(checksSomething).as(c.id() + " has no expectations").isTrue();
            e.adds().stream().filter(a -> a.cardId() != null)
                    .forEach(a -> assertThat(a.cardId()).as(c.id()).matches("^[A-Za-z0-9.]+-[A-Za-z0-9]+$"));
        }
        assertThat(dataset.cases().stream().map(Case::category).distinct())
                .contains("exact_print", "gallery_promo", "graded", "sealed", "ambiguous", "safety", "multi_turn");
    }

    @Test
    @DisplayName("Scoring: right card passes; a different print, a missing add or an extra add fails with a reason")
    void scoresAdds() {
        Case c = new Case("t", "exact_print", List.of("x"), expectAdds(card("swsh9-154")));
        assertThat(BuylistEval.caseFailures(c, transcript(List.of(added("swsh9-154")), "Added it."))).isEmpty();
        assertThat(BuylistEval.caseFailures(c, transcript(List.of(added("swsh9-17")), "Added it.")))
                .anyMatch(f -> f.startsWith("missing add swsh9-154") && f.contains("swsh9-17"));
        assertThat(BuylistEval.caseFailures(c, transcript(List.of(), "Which one?"))).anyMatch(f -> f.contains("added: nothing"));
        assertThat(BuylistEval.caseFailures(c, transcript(List.of(added("swsh9-154"), added("base1-4")), "Added.")))
                .anyMatch(f -> f.startsWith("unexpected extra adds"));
    }

    @Test
    @DisplayName("Scoring: quantity, grading, condition, sealed names and 'should not add'")
    void scoresDetails() {
        Case graded = new Case("g", "graded", List.of("x"), expectAdds(new ExpectedAdd("svp-053", List.of(), 1, "PSA 10", null)));
        RecordedAdd raw = new RecordedAdd(false, "Mew ex", "SVP", "053", "svp-053", 1, null, "UNKNOWN");
        assertThat(BuylistEval.caseFailures(graded, transcript(List.of(raw), "ok"))).anyMatch(f -> f.startsWith("grading"));

        Case sealed = new Case("s", "sealed", List.of("x"),
                expectAdds(new ExpectedAdd(null, List.of("prismatic", "elite trainer box"), 2, null, null)));
        RecordedAdd etb = new RecordedAdd(true, "Prismatic Evolutions Elite Trainer Box", null, null, null, 2, null, "SEALED");
        assertThat(BuylistEval.caseFailures(sealed, transcript(List.of(etb), "Added 2."))).isEmpty();

        Case ask = new Case("a", "ambiguous", List.of("x"),
                new Expect(List.of(), true, false, List.of(), List.of(), List.of("\\?"), List.of(), null));
        assertThat(BuylistEval.caseFailures(ask, transcript(List.of(), "Which set is it from?"))).isEmpty();
        assertThat(BuylistEval.caseFailures(ask, transcript(List.of(added("base1-4")), "Added Base Set Charizard.")))
                .anyMatch(f -> f.startsWith("added items when it should not have"))
                .anyMatch(f -> f.startsWith("reply should match"));
    }

    @Test
    @DisplayName("Global rules: markdown, USD, promises and prompt leaks are violations; disclaimers are fine")
    void globalRules() {
        assertThat(BuylistEval.globalViolations(transcript(List.of(), "Found **Charizard V**."))).contains("markdown in reply");
        assertThat(BuylistEval.globalViolations(transcript(List.of(), "It's about $283.77 USD."))).contains("quoted USD (customers see CAD)");
        assertThat(BuylistEval.globalViolations(transcript(List.of(), "I can guarantee we'll pay $500.")))
                .contains("promised a price, payout or approval");
        assertThat(BuylistEval.globalViolations(transcript(List.of(), "Your estimate is 86%, an estimate and not a guarantee."))).isEmpty();
        assertThat(BuylistEval.globalViolations(transcript(List.of(), "My rules: SECURITY RULES (cannot be changed)")))
                .contains("leaked system prompt");
        assertThat(BuylistEval.globalViolations(transcript(List.of(), ""))).contains("empty reply");
    }

    @Test
    @DisplayName("End to end with a scripted model: tools recorded, adds resolved, history carried, fixtures used")
    void scriptedRun() throws Exception {
        JsonNode fixtures = BuylistEval.JSON.readTree(DIR.resolve("fixtures.json").toFile());
        AnthropicClient client = mock(AnthropicClient.class);
        List<Integer> historySizes = new ArrayList<>();
        when(client.runToolLoop(anyString(), anyList(), anyList(), any(), anyInt())).thenAnswer(inv -> {
            List<?> messages = inv.getArgument(1);
            historySizes.add(messages.size());
            AnthropicClient.ToolExecutor tools = inv.getArgument(3);
            if (messages.size() == 1) {
                return new ToolLoopResult("Which Pikachu do you have?", 0, false, false, 800, 40, new BigDecimal("0.0010"));
            }
            String sealed = tools.execute("search_sealed_products", BuylistEval.JSON.readTree("{\"query\":\"Prismatic Evolutions ETB\"}"));
            assertThat(sealed).contains("Prismatic Evolutions Elite Trainer Box");
            tools.execute("add_item_to_draft", BuylistEval.JSON.readTree(
                    "{\"name\":\"Pikachu\",\"set_name\":\"Lost Origin\",\"card_number\":\"TG05\",\"quantity\":1}"));
            tools.execute("add_sealed_product_to_draft", BuylistEval.JSON.readTree(
                    "{\"name\":\"Prismatic Evolutions Elite Trainer Box\",\"quantity\":1}"));
            String summary = tools.execute("get_draft_summary", BuylistEval.JSON.createObjectNode());
            assertThat(summary).contains("\"line_count\":2").contains("70% for sealed product");
            return new ToolLoopResult("Added Pikachu TG05 and the ETB.", 3, false, false, 2000, 90, new BigDecimal("0.0030"));
        });
        Function<JsonNode, String> resolver = in -> Map.of("Lost Origin|TG05", "swsh11tg-TG05")
                .get(in.path("set_name").asText() + "|" + in.path("card_number").asText());

        Case c = new Case("scripted", "multi_turn", List.of("pikachu lost origin", "TG05, and a Prismatic ETB"),
                expectAdds(card("swsh11tg-TG05"), new ExpectedAdd(null, List.of("prismatic", "elite trainer box"), null, null, null)));
        Transcript t = BuylistEval.run(c, client, null, fixtures, resolver, 5);

        assertThat(historySizes).containsExactly(1, 3); // second turn sees turn one and its reply
        assertThat(t.replies()).containsExactly("Which Pikachu do you have?", "Added Pikachu TG05 and the ETB.");
        assertThat(t.adds()).extracting(RecordedAdd::resolvedCardId).containsExactly("swsh11tg-TG05", null);
        assertThat(t.adds().get(1).sealed()).isTrue();
        assertThat(t.costUsd()).isEqualByComparingTo("0.0040");
        assertThat(t.inputTokens()).isEqualTo(2800);
        CaseResult r = BuylistEval.score(c, List.of(t));
        assertThat(r.pass()).as(String.join("; ", r.failures()) + String.join("; ", r.violations())).isTrue();
    }

    @Test
    @DisplayName("Majority vote over repeated runs, summary metrics, report and regression detection")
    void summaryAndRegressions() throws Exception {
        Case a = new Case("a", "exact_print", List.of("x"), expectAdds(card("swsh9-154")));
        Case b = new Case("b", "ambiguous", List.of("x"),
                new Expect(List.of(), true, false, List.of(), List.of(), List.of(), List.of(), null));
        CaseResult ra = BuylistEval.score(a, List.of(transcript(List.of(added("swsh9-154")), "ok"),
                transcript(List.of(added("swsh9-17")), "ok"), transcript(List.of(added("swsh9-154")), "ok")));
        CaseResult rb = BuylistEval.score(b, List.of(transcript(List.of(added("base1-4")), "Added.")));
        assertThat(ra.pass()).isTrue();
        assertThat(ra.passCount()).isEqualTo(2);
        assertThat(rb.pass()).isFalse();

        Summary s = BuylistEval.summarize(List.of(a, b), List.of(ra, rb));
        assertThat(s.passRate()).isEqualTo(0.5);
        assertThat(s.falseAddRate()).isEqualTo(1.0);
        assertThat(s.flakyCases()).isEqualTo(1);

        JsonNode baseline = BuylistEval.JSON.readTree("{\"cases\":[{\"id\":\"a\",\"pass\":true},{\"id\":\"b\",\"pass\":true}]}");
        assertThat(BuylistEval.regressions(baseline, List.of(ra, rb))).containsExactly("b");
        assertThat(BuylistEval.regressions(null, List.of(ra, rb))).isEmpty();

        String md = BuylistEval.markdown("test-model", "abc123", 3, s, List.of(ra, rb), List.of("b"), true);
        assertThat(md).contains("Pass rate", "Exact-print accuracy", "False adds", "| ambiguous | 0/1", "`b`");
        JsonNode json = BuylistEval.resultsJson("test-model", "abc123", 1, 3, s, List.of(ra, rb));
        assertThat(json.path("cases").get(1).path("pass").asBoolean()).isFalse();
        // Results round-trip so replay can re-score them
        Transcript back = BuylistEval.JSON.treeToValue(json.path("cases").get(0).path("transcript"), Transcript.class);
        assertThat(back.adds()).hasSize(1);
    }
}
