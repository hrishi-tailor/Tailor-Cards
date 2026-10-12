package com.tailorcards.api.buylistchat.resolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sealed product prices (booster boxes, Elite Trainer Boxes, bundles, collections) from the Pokémon TCG
 * API on RapidAPI (/products/search). The price is the Cardmarket 30-day average in EUR; callers
 * convert it. Uses the same key and cache settings as {@link GradedPriceService}.
 */
@Slf4j
@Service
public class SealedPriceService {

    /** @param eur30dAverage Cardmarket 30-day average, EUR; null when the product has no recent sales */
    public record SealedProduct(long id, String name, String setName, String imageUrl, BigDecimal eur30dAverage,
                                double matchScore) {}

    private record Entry<T>(T value, Instant expiresAt) {}

    /** Words that don't identify a product. */
    private static final Set<String> NOISE = Set.of("pokemon", "pokémon", "tcg", "the", "a", "an", "of", "sealed", "new");

    private final RestClient restClient;
    private final String apiKey;
    private final Duration ttl;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Entry<List<SealedProduct>>> cache = new ConcurrentHashMap<>();

    @Autowired
    public SealedPriceService(
            @Value("${app.buylist-chat.graded-prices.base-url:https://pokemon-tcg-api.p.rapidapi.com}") String baseUrl,
            @Value("${app.buylist-chat.graded-prices.host:pokemon-tcg-api.p.rapidapi.com}") String host,
            @Value("${app.buylist-chat.graded-prices.api-key:}") String apiKey,
            @Value("${app.buylist-chat.graded-prices.cache-minutes:720}") int cacheMinutes) {
        this(buildClient(baseUrl, host, apiKey), apiKey, cacheMinutes, Clock.systemUTC());
    }

    SealedPriceService(RestClient restClient, String apiKey, int cacheMinutes, Clock clock) {
        this.restClient = restClient;
        this.apiKey = clean(apiKey);
        this.ttl = Duration.ofMinutes(cacheMinutes);
        this.clock = clock;
    }

    private static RestClient buildClient(String baseUrl, String host, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("x-rapidapi-host", clean(host));
        if (!clean(apiKey).isEmpty()) {
            builder.defaultHeader("x-rapidapi-key", clean(apiKey));
        }
        return builder.build();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /** Products for a search, best name match first; empty when not configured or on errors. */
    public List<SealedProduct> search(String query, int limit) {
        if (!isConfigured() || query == null || query.isBlank()) {
            return List.of();
        }
        String q = expand(query.trim());
        String key = q.toLowerCase(Locale.ROOT);
        Entry<List<SealedProduct>> cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value().stream().limit(limit).toList();
        }
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/products/search").queryParam("search", q).queryParam("per_page", 20).build())
                    .retrieve().body(String.class);
            List<SealedProduct> results = new ArrayList<>();
            for (JsonNode p : objectMapper.readTree(body == null ? "{}" : body).path("data")) {
                String name = p.path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                JsonNode avg = p.path("prices").path("cardmarket").path("30d_average");
                results.add(new SealedProduct(p.path("id").asLong(), name, p.path("episode").path("name").asText(null),
                        p.path("image").asText(null),
                        avg.isNumber() && avg.asDouble() > 0 ? BigDecimal.valueOf(avg.asDouble()).setScale(2, RoundingMode.HALF_UP) : null,
                        score(q, name)));
            }
            results.sort(Comparator.comparingDouble(SealedProduct::matchScore).reversed()
                    .thenComparingInt(r -> r.name().length()));
            cache.put(key, new Entry<>(List.copyOf(results), clock.instant().plus(ttl)));
            return results.stream().limit(limit).toList();
        } catch (RestClientResponseException e) {
            log.warn("Sealed price request failed: HTTP {}", e.getStatusCode().value());
            return List.of();
        } catch (Exception e) {
            log.warn("Sealed price request failed: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** The best match when it covers the words the customer used (score >= 0.75), else empty. */
    public Optional<SealedProduct> bestMatch(String query) {
        return search(query, 1).stream().filter(p -> p.matchScore() >= 0.75).findFirst();
    }

    /** "ETB" -> "Elite Trainer Box", "BB" stays; customers and the model use the abbreviation. */
    static String expand(String query) {
        return query.replaceAll("(?i)\\betbs?\\b", "Elite Trainer Box")
                .replaceAll("(?i)\\bupc\\b", "Ultra-Premium Collection")
                .replaceAll("\\s+", " ").trim();
    }

    /** Share of the query's words found in the product name (1.0 = every word). */
    static double score(String query, String name) {
        Set<String> nameWords = words(name);
        List<String> wanted = words(query).stream().toList();
        if (wanted.isEmpty()) {
            return 0;
        }
        long hits = wanted.stream().filter(nameWords::contains).count();
        return (double) hits / wanted.size();
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).replace('&', ' ').split("[^\\p{L}\\p{Nd}]+"))
                .filter(w -> !w.isBlank() && !NOISE.contains(w))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
