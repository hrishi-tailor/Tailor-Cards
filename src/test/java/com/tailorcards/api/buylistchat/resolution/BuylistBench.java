package com.tailorcards.api.buylistchat.resolution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.BuylistLlmBudget;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.intake.ItemInput;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.buylistchat.repository.BuylistDraftLineRepository;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared pieces for the buylist latency benchmarks: a realistic 60-card lot, an in-memory line repository, and a
 * TCGdex provider that records real responses or replays them with a fixed network delay.
 */
final class BuylistBench {

    static final Path DIR = Path.of("src/test/resources/bench");
    /** Real TCGdex responses for the lot, recorded by {@code BuylistResolutionLiveBench}. */
    static final Path REPLAY = DIR.resolve("tcgdex-replay.json");
    /** What resolution produced for the lot before the speed-ups; pricing must not change. */
    static final Path EXPECTED = DIR.resolve("expected-resolution.json");
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    /** A 60-card lot as a customer would paste it: modern and vintage, gallery and promo numbers, duplicates, vague lines. */
    static final List<String> LOT = List.of(
            "Charizard ex 199/165", "Pikachu 58/102", "Umbreon VMAX 215/203", "Gengar TG06/TG30", "Pikachu TG05/TG30",
            "Mew ex 151/165", "Mewtwo 150/165", "Blastoise 2/102", "Venusaur 15/102", "Charizard 4/102",
            "Lugia V 186/195", "Giratina V 186/196", "Rayquaza VMAX 218/203", "Espeon VMAX 270/264", "Sylveon VMAX 212/203",
            "Mew VMAX 269/264", "Pikachu VMAX 188/185", "Charizard VSTAR 174/172", "Arceus VSTAR 176/172", "Gardevoir ex 245/198",
            "Miraidon ex 244/198", "Koraidon ex 247/198", "Iono 269/193", "Mewtwo V 30/78", "Charizard V 154/172",
            "Snorlax 143/165", "Bulbasaur 166/165", "Charmander 168/165", "Squirtle 170/165", "Zapdos ex 192/165",
            "Alakazam ex 201/165", "Erika's Invitation 203/165", "Giratina VSTAR 212/196", "Leafeon VMAX 205/203", "Glaceon VMAX 209/203",
            "Dragonite V 192/203", "Duraludon VMAX 220/203", "Umbreon V 189/203", "Eevee 133/165", "Jigglypuff 39/165",
            "Psyduck 175/165", "Machamp 68/165", "Dragonite 149/165", "Ninetales 38/165", "Pidgeot ex 164/197",
            "Charizard ex 223/197", "Rayquaza V 194/203", "Mew ex SVP 053", "Pikachu with Grey Felt Hat SVP 085", "Radiant Charizard 11/78",
            "Umbreon ex 161/131", "Sylveon ex 156/131", "Espeon ex 155/131", "Pikachu ex 238/191", "Latias ex 239/191",
            "Bulbasaur 1/102", "Base Set Charizard", "Pikachu", "2x Umbreon VMAX 215/203", "Charizard ex 199/165");

    private BuylistBench() {
    }

    /** The first n lines of the lot parsed the way a paste without the AI is (same items the AI would return). */
    static List<ItemInput> items(int n) {
        ItemNormalizer parser = new ItemNormalizer(mock(AnthropicClient.class), mock(BuylistLlmBudget.class), new BuylistChatProperties());
        List<ItemInput> items = new ArrayList<>();
        for (String raw : LOT.subList(0, n)) {
            parser.parseHeuristic(raw).ifPresent(items::add);
        }
        return items;
    }

    static List<BuylistDraftLine> lines(String draftId, List<ItemInput> items) {
        List<BuylistDraftLine> lines = new ArrayList<>();
        long id = 1;
        for (ItemInput item : items) {
            lines.add(BuylistDraftLine.builder().id(id).draftId(draftId).lineNo((int) id).kind(item.kind())
                    .inputText(item.inputText()).name(item.name()).setName(item.setName()).cardNumber(item.cardNumber())
                    .variant(item.variant()).itemCondition(item.condition()).grading(item.grading()).quantity(item.quantity())
                    .cardId(item.cardId()).resolveState(CardResolutionService.PENDING).createdAt(Instant.now()).build());
            id++;
        }
        return lines;
    }

    /** In-memory stand-in for the line table. */
    static BuylistDraftLineRepository repository(Map<Long, BuylistDraftLine> store) {
        BuylistDraftLineRepository repo = mock(BuylistDraftLineRepository.class);
        when(repo.findByDraftIdAndResolveState(anyString(), anyString())).thenAnswer(inv -> store.values().stream()
                .filter(l -> l.getDraftId().equals(inv.getArgument(0)) && inv.getArgument(1).equals(l.getResolveState())).toList());
        when(repo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get((Long) inv.getArgument(0))));
        when(repo.save(any(BuylistDraftLine.class))).thenAnswer(inv -> {
            BuylistDraftLine l = inv.getArgument(0);
            store.put(l.getId(), l);
            return l;
        });
        return repo;
    }

    record Run(long ms, CardLookupService.Stats stats, List<BuylistDraftLine> lines) {}

    /** Resolves the first n lines of the lot from a cold cache, the way a new draft is resolved, and times it. */
    static Run resolve(TcgdexPriceProvider provider, int n) throws InterruptedException {
        BuylistChatProperties props = new BuylistChatProperties();
        Map<Long, BuylistDraftLine> store = new ConcurrentHashMap<>();
        lines("bench", items(n)).forEach(l -> store.put(l.getId(), l));
        CardLookupService lookup = new CardLookupService(provider, props);
        CardResolutionService service = new CardResolutionService(repository(store), lookup, props, null, null, null, null);
        try {
            long start = System.nanoTime();
            service.schedule("bench");
            long deadline = start + 600_000_000_000L;
            while (store.values().stream().anyMatch(l -> CardResolutionService.PENDING.equals(l.getResolveState()))) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("resolution did not finish");
                Thread.sleep(5);
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            List<BuylistDraftLine> done = new ArrayList<>(store.values());
            done.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            return new Run(ms, lookup.stats(), done);
        } finally {
            service.shutdown();
        }
    }

    record PipelineRun(long ms, long normaliseMs, int claudeCalls, long claudeOutputTokens,
                       CardLookupService.Stats stats, List<BuylistDraftLine> lines) {}

    /**
     * A pasted list end to end, as the paste endpoint runs it: the Claude normaliser, lines added to the draft,
     * then resolution until every line is priced. Cold TCGdex cache.
     */
    static PipelineRun paste(TcgdexPriceProvider provider, StubClaude claude, int n) throws InterruptedException {
        BuylistChatProperties props = new BuylistChatProperties();
        Map<Long, BuylistDraftLine> store = new ConcurrentHashMap<>();
        CardLookupService lookup = new CardLookupService(provider, props);
        CardResolutionService service = new CardResolutionService(repository(store), lookup, props, null, null, null, null);
        ItemNormalizer normalizer = claude.normalizer(props);
        AtomicLong nextId = new AtomicLong(1);
        try {
            long start = System.nanoTime();
            // As the paste endpoint does: each chunk is added (and resolution scheduled) as soon as it's read
            List<ItemInput> items = normalizer.normalize(LOT.subList(0, n), chunk -> {
                add(store, nextId, chunk);
                service.schedule("bench");
            });
            long normaliseMs = (System.nanoTime() - start) / 1_000_000;
            waitResolved(store, items.size(), start);
            long ms = (System.nanoTime() - start) / 1_000_000;
            List<BuylistDraftLine> done = new ArrayList<>(store.values());
            done.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            return new PipelineRun(ms, normaliseMs, claude.calls.get(), claude.outputTokens.get(), lookup.stats(), done);
        } finally {
            service.shutdown();
        }
    }

    /** Adds items as draft lines, numbered after the ones already there (like BuylistDraftService.addItems). */
    static void add(Map<Long, BuylistDraftLine> store, AtomicLong nextId, List<ItemInput> items) {
        for (BuylistDraftLine l : lines("bench", items)) {
            long id = nextId.getAndIncrement();
            l.setId(id);
            l.setLineNo((int) id);
            store.put(id, l);
        }
    }

    static void waitResolved(Map<Long, BuylistDraftLine> store, int expected, long start) throws InterruptedException {
        long deadline = start + 600_000_000_000L;
        while (store.size() < expected
                || store.values().stream().anyMatch(l -> CardResolutionService.PENDING.equals(l.getResolveState()))) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("resolution did not finish");
            Thread.sleep(5);
        }
    }

    /** What pricing depends on, per line: the matched card, price, state, confidence and candidates. */
    static List<Map<String, Object>> snapshot(List<BuylistDraftLine> lines) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BuylistDraftLine l : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("input", l.getInputText());
            row.put("state", l.getResolveState());
            row.put("cardId", l.getCardId());
            row.put("matched", l.getMatchedName() + " | " + l.getMatchedSet() + " | " + l.getMatchedNumber());
            row.put("unitMarketUsd", l.getUnitMarketUsd() == null ? null : l.getUnitMarketUsd().toPlainString());
            row.put("eurTrend", l.getEurTrend() == null ? null : l.getEurTrend().toPlainString());
            row.put("priceSource", l.getPriceSource());
            row.put("priceBasis", l.getPriceBasis());
            row.put("confidence", l.getIdConfidence() == null ? null : l.getIdConfidence().toPlainString());
            row.put("candidates", l.getCandidatesJson());
            row.put("cardNumber", l.getCardNumber());
            out.add(row);
        }
        return out;
    }

    /**
     * Rows comparable across JVMs: candidate JSON is parsed, because its key order comes from Map.of, which
     * iterates in a different order on every JVM start (same content).
     */
    static List<Map<String, Object>> comparable(List<Map<String, Object>> rows) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new TreeMap<>(row);
            Object candidates = copy.get("candidates");
            if (candidates instanceof String json) {
                copy.put("candidates", JSON.readValue(json, new TypeReference<List<TreeMap<String, Object>>>() {}));
            }
            out.add(copy);
        }
        return out;
    }

    static List<Map<String, Object>> expected() throws IOException {
        return comparable(JSON.readValue(EXPECTED.toFile(), new TypeReference<List<Map<String, Object>>>() {}));
    }

    // ---------------------------------------------------------------- recording and replay

    /** Calls the real TCGdex and keeps every response, keyed by request. */
    static final class RecordingProvider extends TcgdexPriceProvider {
        private final TcgdexPriceProvider real;
        final Map<String, Object> recorded;

        RecordingProvider(TcgdexPriceProvider real, Map<String, Object> recorded) {
            super(null, null);
            this.real = real;
            this.recorded = recorded;
        }

        @Override
        public Optional<CardMarketPrice> fetchCardStrict(String cardId) {
            Optional<CardMarketPrice> card = real.fetchCardStrict(cardId);
            recorded.put("card:" + cardId, card.orElse(null));
            return card;
        }

        @Override
        public List<CardMarketPrice> searchCardsStrict(String query, int limit) {
            List<CardMarketPrice> found = real.searchCardsStrict(query, limit);
            recorded.put("search:" + query + ":" + limit, found);
            return found;
        }

        @Override
        public List<SetInfo> fetchSetsStrict() {
            List<SetInfo> sets = real.fetchSetsStrict();
            recorded.put("sets", sets);
            return sets;
        }
    }

    /** Answers from recorded responses after a fixed delay per request, like a network call. */
    static class ReplayProvider extends TcgdexPriceProvider {
        private final Map<String, CardMarketPrice> cards = new TreeMap<>();
        private final Map<String, List<CardMarketPrice>> searches = new TreeMap<>();
        private final List<SetInfo> sets;
        private final long latencyMs;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicLong unknown = new AtomicLong();

        ReplayProvider(long latencyMs) throws IOException {
            super(null, null);
            this.latencyMs = latencyMs;
            Map<String, Object> raw = JSON.readValue(REPLAY.toFile(), new TypeReference<>() {});
            List<SetInfo> setList = List.of();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                String key = e.getKey();
                if (key.equals("sets")) {
                    setList = JSON.convertValue(e.getValue(), new TypeReference<List<SetInfo>>() {});
                } else if (key.startsWith("card:")) {
                    cards.put(key.substring(5), e.getValue() == null ? null : JSON.convertValue(e.getValue(), CardMarketPrice.class));
                } else if (key.startsWith("search:")) {
                    searches.put(key.substring(7), JSON.convertValue(e.getValue(), new TypeReference<List<CardMarketPrice>>() {}));
                }
            }
            this.sets = setList;
        }

        private void network() {
            calls.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public Optional<CardMarketPrice> fetchCardStrict(String cardId) {
            network();
            if (!cards.containsKey(cardId)) unknown.incrementAndGet();
            return Optional.ofNullable(cards.get(cardId));
        }

        @Override
        public List<CardMarketPrice> searchCardsStrict(String query, int limit) {
            network();
            List<CardMarketPrice> found = searches.get(query + ":" + limit);
            if (found == null) unknown.incrementAndGet();
            return found == null ? List.of() : found;
        }

        @Override
        public List<SetInfo> fetchSetsStrict() {
            network();
            return sets;
        }
    }

    static void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
    }
}
