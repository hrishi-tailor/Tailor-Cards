package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tailorcards.api.buylistchat.BuylistEval.Case;
import com.tailorcards.api.buylistchat.BuylistEval.CaseResult;
import com.tailorcards.api.buylistchat.BuylistEval.Dataset;
import com.tailorcards.api.buylistchat.BuylistEval.Summary;
import com.tailorcards.api.buylistchat.BuylistEval.Transcript;
import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Runs the buylist assistant evaluation. Skipped in the normal test suite (it calls the Anthropic API):
 *
 * <pre>
 * ANTHROPIC_API_KEY=... ./mvnw test -Dtest=BuylistAssistantEvalTest -Deval.live=true
 *   -Deval.repeats=3            run each case 3 times, pass by majority (measures flakiness)
 *   -Deval.cases=id1,id2        only these cases;  -Deval.category=sealed  only one category
 *   -Deval.model=...            another model (default ANTHROPIC_MODEL or Haiku 4.5)
 *   -Deval.updateBaseline=true  save this run as the baseline
 *   -Deval.allowRegressions=true  report regressions without failing
 *
 * ./mvnw test -Dtest=BuylistAssistantEvalTest -Deval.replay=true
 *   re-scores the last results.json against the current cases and scorer, without calling the model
 * </pre>
 *
 * Writes evaluation/buylist/report.md and results.json. Fails when a case that passed in baseline.json fails now.
 */
@DisplayName("Buylist assistant evaluation")
class BuylistAssistantEvalTest {

    static final Path DIR = Path.of("evaluation/buylist");
    private static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";

    @Test
    @DisplayName("Live: golden conversations through the production prompt, tools and model")
    void live() throws Exception {
        assumeTrue(Boolean.getBoolean("eval.live"), "live eval: run with -Deval.live=true");
        String key = System.getenv("ANTHROPIC_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "live eval needs ANTHROPIC_API_KEY");
        String model = System.getProperty("eval.model",
                Optional.ofNullable(System.getenv("ANTHROPIC_MODEL")).filter(m -> !m.isBlank()).orElse(DEFAULT_MODEL));
        int repeats = Math.max(1, Integer.getInteger("eval.repeats", 1));

        Dataset dataset = BuylistEval.loadCases(DIR.resolve("cases.json"));
        JsonNode fixtures = BuylistEval.JSON.readTree(DIR.resolve("fixtures.json").toFile());
        List<Case> cases = selected(dataset.cases());

        BuylistChatProperties props = new BuylistChatProperties();
        CardLookupService lookup = new CardLookupService(new TcgdexPriceProvider("https://api.tcgdex.net/v2/en"), props);
        BuylistChatService service = new BuylistChatService(mock(AnthropicClient.class), mock(BuylistLlmBudget.class),
                new LlmRateLimiter(1000), mock(BuylistDraftService.class), lookup, null, props, null, true);
        AnthropicClient client = new AnthropicClient("https://api.anthropic.com/v1", key, model, 1024, 60);

        List<CaseResult> results = new ArrayList<>();
        for (Case c : cases) {
            List<Transcript> runs = new ArrayList<>();
            for (int i = 0; i < repeats; i++) {
                runs.add(BuylistEval.run(c, client, service, fixtures, BuylistEval.lookupResolver(lookup),
                        props.getLimits().getMaxToolRounds()));
            }
            CaseResult r = BuylistEval.score(c, runs);
            results.add(r);
            System.out.printf("EVAL %-4s %-40s %s%n", r.pass() ? "PASS" : "FAIL", c.id(),
                    r.pass() ? "" : String.join("; ", concat(r.failures(), r.violations())));
        }
        finish(model, repeats, dataset, cases, results, !Boolean.getBoolean("eval.allowRegressions"));
    }

    @Test
    @DisplayName("Replay: re-score the last results without calling the model")
    void replay() throws Exception {
        assumeTrue(Boolean.getBoolean("eval.replay"), "replay: run with -Deval.replay=true");
        Path file = DIR.resolve("results.json");
        assumeTrue(Files.exists(file), "replay needs a results.json from a live run");
        JsonNode last = BuylistEval.JSON.readTree(file.toFile());
        Dataset dataset = BuylistEval.loadCases(DIR.resolve("cases.json"));
        List<Case> cases = new ArrayList<>();
        List<CaseResult> results = new ArrayList<>();
        for (JsonNode recorded : last.path("cases")) {
            Optional<Case> c = dataset.cases().stream().filter(x -> x.id().equals(recorded.path("id").asText())).findFirst();
            if (c.isEmpty()) continue;
            Transcript t = BuylistEval.JSON.treeToValue(recorded.path("transcript"), Transcript.class);
            cases.add(c.get());
            results.add(BuylistEval.score(c.get(), List.of(t)));
        }
        finish(last.path("model").asText() + " (replay)", 1, dataset, cases, results, false);
    }

    private static void finish(String model, int repeats, Dataset dataset, List<Case> cases, List<CaseResult> results,
                               boolean failOnRegression) throws Exception {
        Summary summary = BuylistEval.summarize(cases, results);
        Path baselineFile = DIR.resolve("baseline.json");
        JsonNode baseline = Files.exists(baselineFile) ? BuylistEval.JSON.readTree(baselineFile.toFile()) : null;
        List<String> regressions = BuylistEval.regressions(baseline, results);
        String hash = BuylistEval.promptHash();

        ObjectNode json = BuylistEval.resultsJson(model, hash, dataset.version(), repeats, summary, results);
        boolean replay = model.endsWith("(replay)");
        BuylistEval.write(DIR.resolve(replay ? "results-replay.json" : "results.json"), BuylistEval.JSON.writeValueAsString(json));
        BuylistEval.write(DIR.resolve(replay ? "report-replay.md" : "report.md"),
                BuylistEval.markdown(model, hash, repeats, summary, results, regressions, baseline != null));
        if (Boolean.getBoolean("eval.updateBaseline") && !replay) {
            BuylistEval.write(baselineFile, BuylistEval.JSON.writeValueAsString(json));
        }
        System.out.printf("EVAL pass rate %.0f%% (%d/%d), exact-print %.0f%%, cost $%s, regressions %s%n",
                summary.passRate() * 100, summary.passed(), summary.cases(), summary.printAccuracy() * 100,
                summary.totalCostUsd(), regressions);
        if (failOnRegression) {
            assertThat(regressions).as("cases that passed in baseline.json and fail now (see evaluation/buylist/report.md)").isEmpty();
        }
    }

    private static List<Case> selected(List<Case> all) {
        String ids = System.getProperty("eval.cases", "");
        String category = System.getProperty("eval.category", "");
        Set<String> wanted = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        return all.stream().filter(c -> wanted.isEmpty() || wanted.contains(c.id()))
                .filter(c -> category.isBlank() || category.equals(c.category())).toList();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
