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

    private final TcgdexPriceProvider tcgdex;
    private final Duration ttl;
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
        this.searchLimit = properties.getResolution().getSearchLimit();
        this.nanosPerRequest = 1_000_000_000L / Math.max(1, properties.getResolution().getRequestsPerSecond());
        this.clock = clock;
    }

    /** Cards whose name matches; throws ProviderUnavailableException on an outage. */
    public List<CardMarketPrice> search(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        Entry<List<CardMarketPrice>> cached = searchCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value();
        }
        throttle();
        List<CardMarketPrice> results = List.copyOf(tcgdex.searchCardsStrict(name.trim(), searchLimit));
        searchCache.put(key, new Entry<>(results, clock.instant().plus(ttl)));
        return results;
    }

    /** Full card with prices; empty when TCGdex has no such card. */
    public Optional<CardMarketPrice> card(String cardId) {
        String key = cardId.trim();
        Entry<Optional<CardMarketPrice>> cached = cardCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value();
        }
        throttle();
        Optional<CardMarketPrice> card = tcgdex.fetchCardStrict(key);
        cardCache.put(key, new Entry<>(card, clock.instant().plus(ttl)));
        return card;
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
        String wanted = setName.trim().toLowerCase(Locale.ROOT);
        List<TcgdexPriceProvider.SetInfo> all = sets();
        List<TcgdexPriceProvider.SetInfo> exactSets = all.stream()
                .filter(s -> s.name().equalsIgnoreCase(wanted) || s.id().equalsIgnoreCase(wanted)).toList();
        if (!exactSets.isEmpty()) {
            List<String> ids = new java.util.ArrayList<>(exactSets.stream().map(TcgdexPriceProvider.SetInfo::id).toList());
            for (TcgdexPriceProvider.SetInfo base : exactSets) {
                String prefix = base.name().toLowerCase(Locale.ROOT) + " ";
                all.stream()
                        .filter(s -> s.name().toLowerCase(Locale.ROOT).startsWith(prefix) && SUBSET.matcher(s.name()).find())
                        .map(TcgdexPriceProvider.SetInfo::id)
                        .filter(id -> !ids.contains(id))
                        .forEach(ids::add);
            }
            return List.copyOf(ids);
        }
        return all.stream().filter(s -> {
            String n = s.name().toLowerCase(Locale.ROOT);
            return n.contains(wanted) || wanted.contains(n);
        }).map(TcgdexPriceProvider.SetInfo::id).toList();
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
            throttle();
            List<TcgdexPriceProvider.SetInfo> sets = List.copyOf(tcgdex.fetchSetsStrict());
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
