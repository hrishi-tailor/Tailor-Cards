package com.tailorcards.api.buylistchat.resolution;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached, rate-limited TCGdex access shared by card resolution and the chat tools. Successful
 * lookups (including "not found") are cached for the configured minutes; outages are not cached.
 */
@Service
public class CardLookupService {

    private record Entry<T>(T value, Instant expiresAt) {}

    /**
     * Running totals since startup, for timing logs: TCGdex requests made, cache hits, time spent waiting for a
     * rate-limit slot and time spent in requests. Callers log the difference between two snapshots.
     */
    public record Stats(long requests, long cacheHits, long throttleWaitMs, long requestMs) {
        public Stats minus(Stats before) {
            return new Stats(requests - before.requests, cacheHits - before.cacheHits,
                    throttleWaitMs - before.throttleWaitMs, requestMs - before.requestMs);
        }
    }

    private final java.util.concurrent.atomic.AtomicLong requests = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong cacheHits = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong throttleWaitNanos = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong requestNanos = new java.util.concurrent.atomic.AtomicLong();

    private final TcgdexPriceProvider tcgdex;
    private final Duration ttl;
    private final Duration searchTtl;
    /** Requests in progress, so lines asking for the same thing at the same moment share one TCGdex call. */
    private final Map<String, java.util.concurrent.CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final int searchLimit;
    private final long nanosPerRequest;
    private final Clock clock;
    private final Map<String, Entry<List<CardMarketPrice>>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, Entry<Optional<CardMarketPrice>>> cardCache = new ConcurrentHashMap<>();
    private volatile Entry<List<TcgdexPriceProvider.SetInfo>> setsCache;
    private long nextSlotNanos = System.nanoTime();

    /** Subsets TCGdex lists as their own sets: Trainer Gallery (TG), Galarian Gallery (GG), Shiny Vault (SV). */
    private static final java.util.regex.Pattern SUBSET = java.util.regex.Pattern.compile(
            "(?i)(trainer gallery|galarian gallery|shiny vault)$");

    @org.springframework.beans.factory.annotation.Autowired
    public CardLookupService(TcgdexPriceProvider tcgdex, BuylistChatProperties properties) {
        this(tcgdex, properties, Clock.systemUTC());
    }

    CardLookupService(TcgdexPriceProvider tcgdex, BuylistChatProperties properties, Clock clock) {
        this.tcgdex = tcgdex;
        this.ttl = Duration.ofMinutes(properties.getResolution().getCacheMinutes());
        this.searchTtl = Duration.ofMinutes(Math.max(properties.getResolution().getCacheMinutes(),
                properties.getResolution().getSearchCacheMinutes()));
        this.searchLimit = properties.getResolution().getSearchLimit();
        this.nanosPerRequest = 1_000_000_000L / Math.max(1, properties.getResolution().getRequestsPerSecond());
        this.clock = clock;
    }

    /** Cards whose name matches; throws ProviderUnavailableException on an outage. */
    public List<CardMarketPrice> search(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        Entry<List<CardMarketPrice>> cached = searchCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            cacheHits.incrementAndGet();
            return cached.value();
        }
        return once("search:" + key, () -> {
            List<CardMarketPrice> results = timed(() -> List.copyOf(tcgdex.searchCardsStrict(name.trim(), searchLimit)));
            searchCache.put(key, new Entry<>(results, clock.instant().plus(searchTtl)));
            return results;
        });
    }

    /** Full card with prices; empty when TCGdex has no such card. */
    public Optional<CardMarketPrice> card(String cardId) {
        String key = cardId.trim();
        Entry<Optional<CardMarketPrice>> cached = cardCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            cacheHits.incrementAndGet();
            return cached.value();
        }
        return once("card:" + key, () -> {
            Optional<CardMarketPrice> card = timed(() -> tcgdex.fetchCardStrict(key));
            cardCache.put(key, new Entry<>(card, clock.instant().plus(ttl)));
            return card;
        });
    }

    /**
     * Name search that copes with extra words: tries the full name, then drops leading words
     * ("Phantasmal Flames Mega Charizard X ex" -> ... -> "Mega Charizard X ex") until something matches,
     * then the same with hyphens replaced by spaces.
     */
    public List<CardMarketPrice> searchFlexible(String name) {
        List<CardMarketPrice> results = searchDroppingWords(name);
        // TCGdex name search fails on hyphens ("Mega Charizard X-EX", "Charizard-EX"); retry spaced out
        if (results.isEmpty() && name.contains("-")) {
            results = searchDroppingWords(name.replace('-', ' '));
        }
        return results;
    }

    private List<CardMarketPrice> searchDroppingWords(String name) {
        String[] words = name.trim().split("\\s+");
        for (int start = 0; start < words.length; start++) {
            String candidate = String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length));
            if (candidate.length() < 3) {
                break;
            }
            List<CardMarketPrice> results = search(candidate);
            if (!results.isEmpty()) {
                return results;
            }
        }
        return List.of();
    }

    /**
     * TCGdex set ids whose name matches (exact name first, then partial), e.g. "phantasmal flames" -> [me02].
     * An exact match also brings its gallery and vault subsets, which TCGdex keeps as separate sets:
     * "Lost Origin" -> [swsh11, swsh11tg] so Trainer Gallery cards like TG05 are found.
     */
    public List<String> setIdsFor(String setName) {
        if (setName == null || setName.isBlank()) {
            return List.of();
        }
        String wanted = fold(setName);
        List<TcgdexPriceProvider.SetInfo> all = sets();
        List<TcgdexPriceProvider.SetInfo> matched = all.stream()
                .filter(s -> fold(s.name()).equals(wanted) || s.id().equalsIgnoreCase(setName.trim())).toList();
        if (matched.isEmpty()) {
            matched = all.stream().filter(s -> {
                String n = fold(s.name());
                return n.contains(wanted) || wanted.contains(n);
            }).toList();
        }
        List<String> ids = new java.util.ArrayList<>(matched.stream().map(TcgdexPriceProvider.SetInfo::id).toList());
        for (TcgdexPriceProvider.SetInfo base : matched) {
            String prefix = fold(base.name()) + " ";
            all.stream()
                    .filter(s -> fold(s.name()).startsWith(prefix) && SUBSET.matcher(s.name()).find())
                    .map(TcgdexPriceProvider.SetInfo::id)
                    .filter(id -> !ids.contains(id))
                    .forEach(ids::add);
        }
        return List.copyOf(ids);
    }

    /** Lower case without accents or extra spaces: "Pokémon GO" and "pokemon  go" compare equal. */
    static String fold(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Set ids whose name appears inside a free-text name ("Phantasmal Flames Charizard" -> [me02]). */
    public List<String> setIdsMentionedIn(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = " " + text.toLowerCase(Locale.ROOT) + " ";
        return sets().stream().filter(s -> s.name().length() >= 4 && lower.contains(" " + s.name().toLowerCase(Locale.ROOT) + " "))
                .map(TcgdexPriceProvider.SetInfo::id).toList();
    }

    /** Removes any set names from free text: "Phantasmal Flames Mega Charizard X ex" -> "Mega Charizard X ex". */
    public String stripSetNames(String text) {
        String result = text;
        for (TcgdexPriceProvider.SetInfo set : sets()) {
            if (set.name().length() >= 4) {
                result = result.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(set.name()) + "\\b", " ");
            }
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    /** Cached set list; empty (never an exception) when TCGdex is down, so callers just skip set filtering. */
    public List<TcgdexPriceProvider.SetInfo> sets() {
        Entry<List<TcgdexPriceProvider.SetInfo>> cached = setsCache;
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value();
        }
        try {
            List<TcgdexPriceProvider.SetInfo> sets = timed(() -> List.copyOf(tcgdex.fetchSetsStrict()));
            // Set lists change rarely: keep them for 12 hours
            setsCache = new Entry<>(sets, clock.instant().plus(Duration.ofHours(12)));
            return sets;
        } catch (TcgdexPriceProvider.ProviderUnavailableException e) {
            return cached != null ? cached.value() : List.of();
        }
    }

    /**
     * Direct lookup by set and printed number: TCGdex ids are "{setId}-{localId}" (swsh9-154, me02-125,
     * sv03.5-001); padding varies by set, so both the plain and 3-digit forms are tried.
     */
    public Optional<CardMarketPrice> findBySetAndNumber(List<String> setIds, String number) {
        if (setIds == null || setIds.isEmpty() || number == null || number.isBlank()) {
            return Optional.empty();
        }
        String head = number.split("/")[0].replaceAll("[\\s#]+", "");
        if (head.isEmpty()) {
            return Optional.empty();
        }
        java.util.LinkedHashSet<String> localIds = new java.util.LinkedHashSet<>();
        localIds.add(head);
        String stripped = head.replaceFirst("^0+(?=.)", "");
        localIds.add(stripped);
        if (stripped.matches("\\d{1,2}")) {
            localIds.add(String.format("%03d", Integer.parseInt(stripped)));
        }
        for (String setId : setIds.stream().limit(3).toList()) {
            for (String localId : localIds) {
                Optional<CardMarketPrice> card = card(setId + "-" + localId);
                if (card.isPresent()) {
                    return card;
                }
            }
        }
        return Optional.empty();
    }

    /** True when the card id belongs to one of the set ids ("me02-125" is in "me02"). */
    public static boolean inSets(String cardId, List<String> setIds) {
        return cardId != null && setIds.stream().anyMatch(id -> cardId.startsWith(id + "-"));
    }

    public Stats stats() {
        return new Stats(requests.get(), cacheHits.get(), throttleWaitNanos.get() / 1_000_000, requestNanos.get() / 1_000_000);
    }

    /**
     * Runs load once for concurrent callers with the same key: the first caller makes the request, the others wait
     * for its result (counted as cache hits). Failures reach every waiter and are not cached.
     */
    @SuppressWarnings("unchecked")
    private <T> T once(String key, java.util.function.Supplier<T> load) {
        java.util.concurrent.CompletableFuture<Object> mine = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Object> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            cacheHits.incrementAndGet();
            try {
                return (T) existing.join();
            } catch (java.util.concurrent.CompletionException e) {
                throw e.getCause() instanceof RuntimeException re ? re : e;
            }
        }
        try {
            T value = load.get();
            mine.complete(value);
            return value;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /** One TCGdex request: waits for a rate-limit slot, then runs it, counting both times. */
    private <T> T timed(java.util.function.Supplier<T> call) {
        long waitStart = System.nanoTime();
        throttle();
        long start = System.nanoTime();
        throttleWaitNanos.addAndGet(start - waitStart);
        requests.incrementAndGet();
        try {
            return call.get();
        } finally {
            requestNanos.addAndGet(System.nanoTime() - start);
        }
    }

    /** Spaces outgoing TCGdex calls to the configured requests per second across all threads. */
    private void throttle() {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlotNanos);
            nextSlotNanos = slot + nanosPerRequest;
            waitNanos = slot - now;
        }
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
