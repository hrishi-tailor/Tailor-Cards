package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.provider.CardMarketPrice;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Evaluation harness for the buylist chat assistant: runs golden conversations through the production
 * system prompt, tools and model, and scores what the assistant did.
 *
 * <ul>
 *   <li>Read tools (search_cards, get_card) run for real against TCGdex; sealed search and shop search
 *       use fixtures so runs repeat and spend no RapidAPI quota.</li>
 *   <li>Write tools (add item, set deal, ...) are recorded instead of touching a draft.</li>
 *   <li>Card adds are resolved to a TCGdex card id the way production does (card id, else set + number,
 *       else name + number), so "exact print" means the customer would get the right card.</li>
 *   <li>Every reply is checked against global rules: no markdown bold, no USD quotes, no promises of
 *       price or approval, no prompt leaks.</li>
 * </ul>
 */
public final class BuylistEval {

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    /** Tool results go to the model compact, as production sends them. */
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private BuylistEval() {
    }

    // ---------------------------------------------------------------- dataset

    public record ExpectedAdd(String cardId, List<String> sealedNameContains, Integer quantity, String grading,
                              String condition) {
        boolean sealed() {
            return sealedNameContains != null && !sealedNameContains.isEmpty();
        }

        String label() {
            String what = sealed() ? "sealed '" + String.join(" ", sealedNameContains) + "'" : cardId;
            List<String> extras = new ArrayList<>();
            if (quantity != null) extras.add("x" + quantity);
            if (grading != null) extras.add(grading);
            if (condition != null) extras.add(condition);
            return extras.isEmpty() ? what : what + " (" + String.join(", ", extras) + ")";
        }
    }

    public record Expect(List<ExpectedAdd> adds, boolean noAdds, boolean allowExtraAdds, List<String> toolsCalled,
                         List<String> toolsNotCalled, List<String> replyMatches, List<String> replyNotMatches,
                         String dealType) {
    }

    public record Case(String id, String category, List<String> turns, Expect expect) {
    }

    public record Dataset(int version, List<Case> cases) {
    }

    static Dataset loadCases(Path file) throws IOException {
        JsonNode root = JSON.readTree(file.toFile());
        List<Case> cases = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            JsonNode e = c.path("expect");
            List<ExpectedAdd> adds = new ArrayList<>();
            for (JsonNode a : e.path("adds")) {
                adds.add(new ExpectedAdd(text(a, "cardId"), strings(a.path("sealedNameContains")),
                        a.has("quantity") ? a.get("quantity").asInt() : null, text(a, "grading"), text(a, "condition")));
            }
            cases.add(new Case(c.path("id").asText(), c.path("category").asText(), strings(c.path("turns")),
                    new Expect(adds, e.path("noAdds").asBoolean(false), e.path("allowExtraAdds").asBoolean(false),
                            strings(e.path("toolsCalled")), strings(e.path("toolsNotCalled")),
                            strings(e.path("replyMatches")), strings(e.path("replyNotMatches")), text(e, "dealType"))));
        }
        return new Dataset(root.path("version").asInt(1), cases);
    }

    // ---------------------------------------------------------------- transcripts

    public record ToolCall(String name, JsonNode input) {
    }

    /** One add the assistant made, with the card it resolves to (null for sealed or unresolved). */
    public record RecordedAdd(boolean sealed, String name, String setName, String cardNumber, String resolvedCardId,
                              int quantity, String grading, String condition) {
        String label() {
            String what = sealed ? "sealed '" + name + "'" : (resolvedCardId != null ? resolvedCardId
                    : "unresolved '" + name + " " + Optional.ofNullable(setName).orElse("") + " "
                    + Optional.ofNullable(cardNumber).orElse("") + "'");
            List<String> extras = new ArrayList<>();
            if (quantity != 1) extras.add("x" + quantity);
            if (grading != null) extras.add(grading);
            if (condition != null && !"UNKNOWN".equals(condition)) extras.add(condition);
            return extras.isEmpty() ? what : what + " (" + String.join(", ", extras) + ")";
        }
    }

    public record Transcript(List<ToolCall> calls, List<String> replies, List<RecordedAdd> adds, int inputTokens,
                             int outputTokens, BigDecimal costUsd, long latencyMs, int toolRounds, boolean failed,
                             boolean hitRoundLimit) {
        String lastReply() {
            return replies.isEmpty() ? "" : replies.getLast();
        }
    }

    // ---------------------------------------------------------------- tool harness

    /**
     * Tool executor for one evaluation conversation: read tools go to the real service, sealed and
     * shop searches return fixtures, and write tools are recorded and answered like production.
     */
    static final class ToolHarness implements AnthropicClient.ToolExecutor {
        private final BuylistChatService service;
        private final JsonNode fixtures;
        final List<ToolCall> calls = new ArrayList<>();
        private final List<JsonNode> adds = new ArrayList<>();
        private String dealType = "SELL";

        ToolHarness(BuylistChatService service, JsonNode fixtures) {
            this.service = service;
            this.fixtures = fixtures;
        }

        @Override
        public String execute(String name, JsonNode input) {
            calls.add(new ToolCall(name, input == null ? JSON.createObjectNode() : input.deepCopy()));
            try {
                return switch (name) {
                    case "search_cards", "get_card" -> service == null ? "{\"results\":[]}"
                            : service.executeTool("eval@example.com", "eval-draft", name, input);
                    case "search_sealed_products" -> sealedSearch(input);
                    case "search_store_cards" -> COMPACT.writeValueAsString(Map.of("results", fixtures.path("storeCards")));
                    case "add_item_to_draft", "add_sealed_product_to_draft" -> {
                        ObjectNode recorded = input != null && input.isObject() ? (ObjectNode) input.deepCopy() : JSON.createObjectNode();
                        if ("add_sealed_product_to_draft".equals(name)) {
                            recorded.put("__sealedTool", true);
                        }
                        adds.add(recorded);
                        yield COMPACT.writeValueAsString(Map.of("added", true, "line_id", adds.size(), "status",
                                "Checking item data", "note", "Price and status appear in the summary once the item is checked."));
                    }
                    case "remove_item_from_draft" -> COMPACT.writeValueAsString(Map.of("removed", true,
                            "line_id", input.path("line_id").asLong()));
                    case "add_store_card_to_trade" -> COMPACT.writeValueAsString(Map.of("added", true,
                            "product_id", input.path("product_id").asLong()));
                    case "set_deal_type" -> {
                        dealType = input.path("deal_type").asText("SELL");
                        yield COMPACT.writeValueAsString(Map.of("deal_type", dealType));
                    }
                    case "get_draft_summary" -> summary();
                    default -> COMPACT.writeValueAsString(Map.of("error", "Unknown tool: " + name));
                };
            } catch (Exception e) {
                return "{\"error\":\"" + e.getClass().getSimpleName() + "\"}";
            }
        }

        private String sealedSearch(JsonNode input) throws IOException {
            String query = Optional.ofNullable(text(input, "query")).orElse(Optional.ofNullable(text(input, "name")).orElse(""));
            List<String> words = words(query);
            List<JsonNode> hits = new ArrayList<>();
            for (JsonNode p : fixtures.path("sealedProducts")) {
                String n = p.path("name").asText().toLowerCase(Locale.ROOT);
                long found = words.stream().filter(n::contains).count();
                if (!words.isEmpty() && found * 2 >= words.size()) {
                    hits.add(p);
                }
            }
            return COMPACT.writeValueAsString(Map.of("results", hits, "total_matches", hits.size()));
        }

        private String summary() throws IOException {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (int i = 0; i < adds.size(); i++) {
                JsonNode a = adds.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("line_id", i + 1);
                row.put("name", a.path("name").asText());
                row.put("quantity", a.path("quantity").asInt(1));
                row.put("status", "PENDING");
                row.put("status_reason", "Checking card data");
                lines.add(row);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("line_count", adds.size());
            out.put("still_checking", adds.size());
            out.put("currency", "CAD");
            out.put("estimated_chance_pct", adds.isEmpty() ? 5 : 80);
            out.put("estimated_chance_label", "Estimated approval rating, not a guarantee");
            out.put("estimated_chance_disclaimer", "This percentage is an estimated guess, not a guaranteed price or acceptance.");
            out.put("lines", lines);
            out.put("deal", Map.of("deal_type", dealType, "rates", fixtures.path("ratesText").asText()));
            out.put("note", "Figures come from the store's system. Relay them as given; they are not an offer.");
            return COMPACT.writeValueAsString(out);
        }

        List<JsonNode> recordedAddInputs() {
            return adds;
        }
    }

    // ---------------------------------------------------------------- running

    /** Resolves a card add's arguments to a TCGdex card id the way production resolves a new line. */
    static Function<JsonNode, String> lookupResolver(CardLookupService lookup) {
        return input -> {
            String cardId = text(input, "card_id");
            if (cardId != null) {
                return lookup.card(cardId).map(CardMarketPrice::cardId).orElse(cardId);
            }
            String set = text(input, "set_name");
            String number = text(input, "card_number");
            String name = text(input, "name");
            if (set != null && number != null) {
                Optional<CardMarketPrice> card = lookup.findBySetAndNumber(lookup.setIdsFor(set), number);
                if (card.isPresent()) {
                    return card.get().cardId();
                }
            }
            if (name != null && number != null) {
                String wanted = numberHead(number);
                List<String> setIds = set == null ? List.of() : lookup.setIdsFor(set);
                return lookup.searchFlexible(name).stream()
                        .filter(c -> wanted.equals(numberHead(c.cardNumber())))
                        .filter(c -> setIds.isEmpty() || CardLookupService.inSets(c.cardId(), setIds))
                        .map(CardMarketPrice::cardId).findFirst().orElse(null);
            }
            return null;
        };
    }

    /** Runs one case: each turn goes through the production prompt and tools; replies carry over as history. */
    static Transcript run(Case c, AnthropicClient client, BuylistChatService service, JsonNode fixtures,
                          Function<JsonNode, String> resolver, int maxToolRounds) {
        ToolHarness tools = new ToolHarness(service, fixtures);
        List<Map<String, Object>> history = new ArrayList<>();
        List<String> replies = new ArrayList<>();
        int in = 0;
        int out = 0;
        int rounds = 0;
        BigDecimal cost = BigDecimal.ZERO;
        boolean failed = false;
        boolean hitLimit = false;
        long started = System.nanoTime();
        for (String turn : c.turns()) {
            history.add(Map.of("role", "user", "content", BuylistChatService.wrapCustomer(turn)));
            AnthropicClient.ToolLoopResult r = client.runToolLoop(BuylistChatService.SYSTEM_PROMPT, List.copyOf(history),
                    BuylistChatService.toolDefinitions(), tools, maxToolRounds);
            in += r.inputTokens();
            out += r.outputTokens();
            rounds += r.toolRounds();
            cost = cost.add(r.costUsd() == null ? BigDecimal.ZERO : r.costUsd());
            failed |= r.failed();
            hitLimit |= r.hitRoundLimit();
            String reply = r.text() == null ? "" : r.text();
            replies.add(reply);
            history.add(Map.of("role", "assistant", "content", reply.isBlank() ? "(no reply)" : reply));
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        List<RecordedAdd> adds = tools.recordedAddInputs().stream().map(a -> toRecordedAdd(a, resolver)).toList();
        return new Transcript(List.copyOf(tools.calls), replies, adds, in, out, cost, latencyMs, rounds, failed, hitLimit);
    }

    static RecordedAdd toRecordedAdd(JsonNode input, Function<JsonNode, String> resolver) {
        String name = Optional.ofNullable(text(input, "name")).orElse("");
        boolean bulk = input.path("bulk").asBoolean(false);
        // Same rule as production's add_item_to_draft, plus the dedicated sealed tool
        boolean sealed = !bulk && (input.path("__sealedTool").asBoolean(false) || input.path("sealed").asBoolean(false)
                || ItemNormalizer.isSealed(name));
        int quantity = Math.max(1, input.path("quantity").asInt(1));
        String grading = ItemNormalizer.normalizeGrading(text(input, "grading"));
        String condition = ItemNormalizer.normalizeCondition(text(input, "condition"));
        String cardId = sealed || bulk ? null : resolver.apply(input);
        return new RecordedAdd(sealed, name, text(input, "set_name"), text(input, "card_number"), cardId, quantity,
                grading, condition);
    }

    // ---------------------------------------------------------------- scoring

    public record CaseResult(String id, String category, boolean pass, int passCount, int runs, List<String> failures,
                             List<String> violations, Transcript transcript) {
    }

    private static final Pattern MARKDOWN = Pattern.compile("\\*\\*|(?m)^#{1,6}\\s|\\|\\s*---");
    private static final Pattern USD = Pattern.compile("(?i)\\b(USD|US\\$|US dollars?)\\b");
    private static final Pattern NEGATED_PROMISE = Pattern.compile(
            "(?i)(not|n't|never|no|without)\\s+(a\\s+|any\\s+)?(be\\s+)?guarantee[ds]?|guarantee[ds]?\\s+(of\\s+)?nothing");
    private static final Pattern PROMISE = Pattern.compile(
            "(?i)\\b(i|we)\\s+(can\\s+)?guarantee|\\bguaranteed\\s+(price|payout|offer|approval)|\\bwe('ll| will)\\s+(definitely\\s+)?(pay|buy it)\\b"
                    + "|\\byour\\s+(list|submission|offer)\\s+(is|has been)\\s+approved\\b");
    private static final Pattern PROMPT_LEAK = Pattern.compile(
            "SECURITY RULES|BE HELPFUL AND DECISIVE|CARD NUMBERS AND TERMS|FACTS AND PRICES|WORKING WITH THE LIST");

    /** Rules every reply must follow, whatever the case. */
    static List<String> globalViolations(Transcript t) {
        List<String> v = new ArrayList<>();
        for (String reply : t.replies()) {
            if (MARKDOWN.matcher(reply).find()) v.add("markdown in reply");
            if (USD.matcher(reply).find()) v.add("quoted USD (customers see CAD)");
            String withoutNegations = NEGATED_PROMISE.matcher(reply).replaceAll(" ");
            if (PROMISE.matcher(withoutNegations).find()) v.add("promised a price, payout or approval");
            if (PROMPT_LEAK.matcher(reply).find()) v.add("leaked system prompt");
        }
        if (t.failed()) v.add("model call failed");
        if (t.hitRoundLimit()) v.add("hit the tool round limit");
        if (!t.failed() && t.lastReply().isBlank()) v.add("empty reply");
        return v.stream().distinct().toList();
    }

    private static final Map<String, List<String>> TOOL_ALIASES = Map.of(
            "search_cards", List.of("search_cards", "get_card"),
            "add_item_to_draft", List.of("add_item_to_draft", "add_sealed_product_to_draft"));

    /** Case-specific expectations: the right adds, tools and reply content. */
    static List<String> caseFailures(Case c, Transcript t) {
        List<String> f = new ArrayList<>();
        Expect e = c.expect();
        List<RecordedAdd> remaining = new ArrayList<>(t.adds());
        for (ExpectedAdd want : e.adds()) {
            Optional<RecordedAdd> match = remaining.stream().filter(got -> sameItem(want, got)).findFirst();
            if (match.isEmpty()) {
                f.add("missing add " + want.label() + " (added: " + describe(t.adds()) + ")");
                continue;
            }
            RecordedAdd got = match.get();
            remaining.remove(got);
            if (want.quantity() != null && want.quantity() != got.quantity()) {
                f.add("quantity for " + want.label() + ": got " + got.quantity());
            }
            if (want.grading() != null && !want.grading().equalsIgnoreCase(Optional.ofNullable(got.grading()).orElse(""))) {
                f.add("grading for " + want.label() + ": got " + Optional.ofNullable(got.grading()).orElse("none"));
            }
            if (want.condition() != null && !want.condition().equalsIgnoreCase(Optional.ofNullable(got.condition()).orElse(""))) {
                f.add("condition for " + want.label() + ": got " + Optional.ofNullable(got.condition()).orElse("none"));
            }
        }
        if (e.noAdds() && !t.adds().isEmpty()) {
            f.add("added items when it should not have: " + describe(t.adds()));
        } else if (!e.allowExtraAdds() && !e.noAdds() && !remaining.isEmpty()) {
            f.add("unexpected extra adds: " + describe(remaining));
        }
        List<String> called = t.calls().stream().map(ToolCall::name).toList();
        for (String tool : e.toolsCalled()) {
            List<String> ok = TOOL_ALIASES.getOrDefault(tool, List.of(tool));
            if (called.stream().noneMatch(ok::contains)) f.add("did not call " + tool);
        }
        for (String tool : e.toolsNotCalled()) {
            if (called.contains(tool)) f.add("called " + tool + " but should not");
        }
        if (e.dealType() != null && t.calls().stream().noneMatch(call -> "set_deal_type".equals(call.name())
                && e.dealType().equalsIgnoreCase(call.input().path("deal_type").asText()))) {
            f.add("did not set the deal to " + e.dealType());
        }
        String reply = t.lastReply();
        for (String rx : e.replyMatches()) {
            if (!Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(reply).find()) f.add("reply should match /" + rx + "/");
        }
        for (String rx : e.replyNotMatches()) {
            if (Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(String.join("\n", t.replies())).find()) {
                f.add("reply should not match /" + rx + "/");
            }
        }
        return f;
    }

    static boolean sameItem(ExpectedAdd want, RecordedAdd got) {
        if (want.sealed()) {
            String name = got.name().toLowerCase(Locale.ROOT);
            return got.sealed() && want.sealedNameContains().stream().allMatch(w -> name.contains(w.toLowerCase(Locale.ROOT)));
        }
        return !got.sealed() && want.cardId() != null && want.cardId().equalsIgnoreCase(got.resolvedCardId());
    }

    /** Scores one or more runs of a case: it passes when most runs pass (LLM output varies run to run). */
    static CaseResult score(Case c, List<Transcript> runs) {
        int passes = 0;
        CaseResult representative = null;
        for (Transcript t : runs) {
            List<String> failures = caseFailures(c, t);
            List<String> violations = globalViolations(t);
            boolean pass = failures.isEmpty() && violations.isEmpty();
            if (pass) passes++;
            if (representative == null || (!pass && representative.pass())) {
                representative = new CaseResult(c.id(), c.category(), pass, 0, 0, failures, violations, t);
            }
        }
        boolean pass = passes * 2 > runs.size();
        return new CaseResult(c.id(), c.category(), pass, passes, runs.size(), representative.failures(),
                representative.violations(), representative.transcript());
    }

    // ---------------------------------------------------------------- report

    public record Summary(int cases, int passed, double passRate, double printAccuracy, double falseAddRate,
                          double violationRate, BigDecimal totalCostUsd, BigDecimal avgCostUsd, long p50LatencyMs,
                          long p95LatencyMs, double avgToolRounds, int flakyCases, Map<String, double[]> byCategory) {
    }

    static Summary summarize(List<Case> cases, List<CaseResult> results) {
        Map<String, Case> byId = cases.stream().collect(Collectors.toMap(Case::id, c -> c));
        int passed = (int) results.stream().filter(CaseResult::pass).count();
        int printTotal = 0;
        int printOk = 0;
        int noAddCases = 0;
        int falseAdds = 0;
        for (CaseResult r : results) {
            Case c = byId.get(r.id());
            for (ExpectedAdd want : c.expect().adds()) {
                if (want.cardId() != null) {
                    printTotal++;
                    if (r.transcript().adds().stream().anyMatch(got -> sameItem(want, got))) printOk++;
                }
            }
            if (c.expect().noAdds()) {
                noAddCases++;
                if (!r.transcript().adds().isEmpty()) falseAdds++;
            }
        }
        BigDecimal total = results.stream().map(r -> r.transcript().costUsd()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> latencies = results.stream().map(r -> r.transcript().latencyMs()).sorted().toList();
        Map<String, double[]> byCategory = new LinkedHashMap<>();
        for (CaseResult r : results) {
            double[] counts = byCategory.computeIfAbsent(r.category(), k -> new double[2]);
            counts[0]++;
            if (r.pass()) counts[1]++;
        }
        int n = results.size();
        return new Summary(n, passed, ratio(passed, n), ratio(printOk, printTotal), ratio(falseAdds, noAddCases),
                ratio((int) results.stream().filter(r -> !r.violations().isEmpty()).count(), n),
                total.setScale(4, RoundingMode.HALF_UP),
                n == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(n), 5, RoundingMode.HALF_UP),
                percentile(latencies, 50), percentile(latencies, 95),
                n == 0 ? 0 : results.stream().mapToInt(r -> r.transcript().toolRounds()).average().orElse(0),
                (int) results.stream().filter(r -> r.passCount() > 0 && r.passCount() < r.runs()).count(), byCategory);
    }

    /** Cases that passed in the baseline and fail now. */
    static List<String> regressions(JsonNode baseline, List<CaseResult> results) {
        if (baseline == null || baseline.isMissingNode()) {
            return List.of();
        }
        Map<String, Boolean> before = new HashMap<>();
        baseline.path("cases").forEach(c -> before.put(c.path("id").asText(), c.path("pass").asBoolean()));
        return results.stream().filter(r -> !r.pass() && Boolean.TRUE.equals(before.get(r.id()))).map(CaseResult::id).toList();
    }

    static ObjectNode resultsJson(String model, String promptHash, int datasetVersion, int repeats, Summary s,
                                  List<CaseResult> results) {
        ObjectNode root = JSON.createObjectNode();
        root.put("runAt", Instant.now().toString());
        root.put("model", model);
        root.put("promptHash", promptHash);
        root.put("datasetVersion", datasetVersion);
        root.put("repeats", repeats);
        root.set("summary", JSON.valueToTree(s));
        ArrayNode cases = root.putArray("cases");
        for (CaseResult r : results) {
            ObjectNode c = cases.addObject();
            c.put("id", r.id());
            c.put("category", r.category());
            c.put("pass", r.pass());
            c.put("passCount", r.passCount());
            c.put("runs", r.runs());
            c.set("failures", JSON.valueToTree(r.failures()));
            c.set("violations", JSON.valueToTree(r.violations()));
            c.set("transcript", JSON.valueToTree(r.transcript()));
        }
        return root;
    }

    static String markdown(String model, String promptHash, int repeats, Summary s, List<CaseResult> results,
                           List<String> regressions, boolean hasBaseline) {
        StringBuilder md = new StringBuilder();
        md.append("# Buylist assistant evaluation\n\n");
        md.append("Model `").append(model).append("` · prompt `").append(promptHash).append("` · ")
                .append(s.cases()).append(" cases").append(repeats > 1 ? " × " + repeats + " runs (majority vote)" : "")
                .append(" · ").append(Instant.now().toString(), 0, 16).append(" UTC\n\n");
        md.append("| Metric | Value |\n| --- | --- |\n");
        md.append("| Pass rate | **").append(pct(s.passRate())).append("** (").append(s.passed()).append("/").append(s.cases()).append(") |\n");
        md.append("| Exact-print accuracy (right card added) | ").append(pct(s.printAccuracy())).append(" |\n");
        md.append("| False adds (added when it should ask or refuse) | ").append(pct(s.falseAddRate())).append(" |\n");
        md.append("| Cases with a rule violation | ").append(pct(s.violationRate())).append(" |\n");
        md.append("| Cost | $").append(s.totalCostUsd()).append(" total, $").append(s.avgCostUsd()).append(" per case |\n");
        md.append("| Latency per case | p50 ").append(s.p50LatencyMs()).append(" ms, p95 ").append(s.p95LatencyMs()).append(" ms |\n");
        md.append("| Tool rounds per case | ").append(String.format(Locale.ROOT, "%.1f", s.avgToolRounds())).append(" |\n");
        if (repeats > 1) md.append("| Flaky cases (mixed results across runs) | ").append(s.flakyCases()).append(" |\n");
        md.append("\n## By category\n\n| Category | Passed |\n| --- | --- |\n");
        s.byCategory().forEach((cat, v) -> md.append("| ").append(cat).append(" | ").append((int) v[1]).append("/")
                .append((int) v[0]).append(" (").append(pct(v[1] / v[0])).append(") |\n"));
        md.append("\n## Regressions vs baseline\n\n");
        if (!hasBaseline) md.append("No baseline yet. Run with `-Deval.updateBaseline=true` to save one.\n");
        else if (regressions.isEmpty()) md.append("None.\n");
        else regressions.forEach(id -> md.append("- `").append(id).append("`\n"));
        List<CaseResult> failed = results.stream().filter(r -> !r.pass()).toList();
        md.append("\n## Failures (").append(failed.size()).append(")\n\n");
        for (CaseResult r : failed) {
            md.append("### `").append(r.id()).append("` (").append(r.category()).append(")\n\n");
            r.failures().forEach(f -> md.append("- ").append(f).append("\n"));
            r.violations().forEach(v -> md.append("- rule: ").append(v).append("\n"));
            md.append("- tools: ").append(r.transcript().calls().stream().map(ToolCall::name).collect(Collectors.joining(" → ")))
                    .append("\n- reply: > ").append(excerpt(r.transcript().lastReply())).append("\n\n");
        }
        return md.toString();
    }

    // ---------------------------------------------------------------- helpers

    static String promptHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((BuylistChatService.SYSTEM_PROMPT
                    + JSON.writeValueAsString(BuylistChatService.toolDefinitions())).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            return "unknown";
        }
    }

    static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String describe(List<RecordedAdd> adds) {
        return adds.isEmpty() ? "nothing" : adds.stream().map(RecordedAdd::label).collect(Collectors.joining(", "));
    }

    private static String excerpt(String reply) {
        String flat = reply.replaceAll("\\s+", " ").trim();
        return flat.length() > 220 ? flat.substring(0, 220) + "…" : flat;
    }

    private static double ratio(int a, int b) {
        return b == 0 ? 1.0 : (double) a / b;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100);
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }

    private static String numberHead(String number) {
        if (number == null) return "";
        return number.split("/")[0].replaceAll("[\\s#]+", "").replaceFirst("^0+(?=.)", "").toUpperCase(Locale.ROOT);
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase(Locale.ROOT).replace("etb", "elite trainer box").split("[^a-z0-9]+")) {
            if (w.length() >= 3 && !List.of("the", "sealed", "box", "pokemon").contains(w)) out.add(w);
        }
        return out;
    }

    static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText().trim();
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
