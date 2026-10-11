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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Graded-card market prices (PSA, BGS, CGC, SGC, TAG, ACE) from the Pokémon TCG API on RapidAPI
 * (tcggo; host pokemon-tcg-api.p.rapidapi.com, headers x-rapidapi-key / x-rapidapi-host).
 * TCGdex has no graded prices.
 *
 * <p>Values are USD medians of recent eBay sales per grade, with the number of sales behind each.
 * Cards are matched by TCGdex id ({@code tcgid}), falling back to name + number + set. The free
 * plan allows about 100 requests a day, so card responses are cached for hours.
 */
@Slf4j
@Service
public class GradedPriceService {

    public static final String SOURCE = "EBAY_GRADED";

    /** @param saleCount number of recent sales behind the median (the API reports up to 5) */
    public record GradedPrice(BigDecimal usd, String gradeKey, String source, Integer saleCount) {}

    private record Entry<T>(T value, Instant expiresAt) {}

    private static final Pattern GRADE = Pattern.compile(
            "^(PSA|BGS|CGC|SGC|TAG|ACE) (10|[1-9](?:\\.5)?)( BLACK LABEL| PRISTINE)?$");

    private final RestClient restClient;
    private final String apiKey;
    private final Duration ttl;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Entry<Optional<JsonNode>>> cardCache = new ConcurrentHashMap<>();

    @Autowired
    public GradedPriceService(
            @Value("${app.buylist-chat.graded-prices.base-url:https://pokemon-tcg-api.p.rapidapi.com}") String baseUrl,
            @Value("${app.buylist-chat.graded-prices.host:pokemon-tcg-api.p.rapidapi.com}") String host,
            @Value("${app.buylist-chat.graded-prices.api-key:}") String apiKey,
            @Value("${app.buylist-chat.graded-prices.cache-minutes:720}") int cacheMinutes) {
        this(buildClient(baseUrl, host, apiKey), apiKey, cacheMinutes, Clock.systemUTC());
    }

    GradedPriceService(RestClient restClient, String apiKey, int cacheMinutes, Clock clock) {
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

    /**
     * Market price for the exact grade of a card. Empty when not configured, not found, the grade
     * has no sales (Black Label / Pristine never fall back to a plain 10), or on errors.
     */
    public Optional<GradedPrice> gradedPrice(String cardId, String cardName, String setName, String cardNumber,
                                             String grading) {
        if (!isConfigured() || grading == null || (cardId == null && cardName == null)) {
            return Optional.empty();
        }
        Optional<String[]> grade = gradeKey(grading);
        if (grade.isEmpty()) {
            return Optional.empty();
        }
        try {
            return card(cardId, cardName, setName, cardNumber).flatMap(c -> readGradedPrice(c, grade.get()[0], grade.get()[1]));
        } catch (RestClientResponseException e) {
            log.warn("Graded price request failed: HTTP {}", e.getStatusCode().value());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Graded price request failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** "PSA 10" -> [psa, 10]; "BGS 9.5" -> [bgs, 9.5]; Black Label / Pristine -> empty (no separate eBay tier). */
    static Optional<String[]> gradeKey(String grading) {
        Matcher m = GRADE.matcher(grading.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " "));
        if (!m.matches() || m.group(3) != null) {
            return Optional.empty();
        }
        return Optional.of(new String[] {m.group(1).toLowerCase(Locale.ROOT), m.group(2)});
    }

    private Optional<JsonNode> card(String cardId, String cardName, String setName, String cardNumber) throws Exception {
        String key = (cardId + "|" + cardName + "|" + setName + "|" + cardNumber).toLowerCase(Locale.ROOT);
        Entry<Optional<JsonNode>> cached = cardCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value();
        }
        Optional<JsonNode> found = Optional.empty();
        if (cardId != null && !cardId.isBlank()) {
            found = search("tcgid", cardId.trim()).stream().findFirst();
        }
        if (found.isEmpty() && cardName != null) {
            // TCGdex and tcggo ids differ for some sets (e.g. sv03.5 vs sv3pt5): match by number and set
            String wantedNumber = numberHead(cardNumber);
            String wantedSet = letters(setName);
            String query = wantedNumber == null ? cardName : cardName + " " + wantedNumber;
            found = search("search", query).stream()
                    .filter(c -> wantedNumber == null || wantedNumber.equals(numberHead(c.path("card_number").asText(null))))
                    .filter(c -> sameSet(wantedSet, letters(c.path("episode").path("name").asText(""))))
                    .findFirst();
        }
        cardCache.put(key, new Entry<>(found, clock.instant().plus(ttl)));
        return found;
    }

    private List<JsonNode> search(String param, String value) throws Exception {
        String body = restClient.get()
                .uri(uri -> uri.path("/cards/search").queryParam(param, value).queryParam("per_page", 20).build())
                .retrieve().body(String.class);
        List<JsonNode> results = new ArrayList<>();
        objectMapper.readTree(body == null ? "{}" : body).path("data").forEach(results::add);
        return results;
    }

    /** prices.ebay.graded.{company}.{grade}: {median_price, sample_size}. */
    static Optional<GradedPrice> readGradedPrice(JsonNode card, String company, String grade) {
        JsonNode tier = card.path("prices").path("ebay").path("graded").path(company).path(grade);
        JsonNode median = tier.path("median_price");
        if (!median.isNumber() || median.asDouble() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new GradedPrice(BigDecimal.valueOf(median.asDouble()).setScale(2, RoundingMode.HALF_UP),
                company.toUpperCase(Locale.ROOT) + " " + grade, SOURCE,
                tier.path("sample_size").isInt() ? tier.path("sample_size").asInt() : null));
    }

    static String numberHead(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String head = number.split("/")[0].replaceAll("[\\s#]+", "").toUpperCase(Locale.ROOT);
        String stripped = head.replaceFirst("^0+(?=.)", "");
        return stripped.isEmpty() ? null : stripped;
    }

    private static boolean sameSet(String wanted, String episode) {
        return wanted.isEmpty() || (!episode.isEmpty() && (episode.contains(wanted) || wanted.contains(episode)));
    }

    private static String letters(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
