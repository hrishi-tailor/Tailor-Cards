package com.tailorcards.api.trade.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Free, key-less Pokémon TCG pricing provider backed by tcgdex.net.
 * Always available (the buylist chat prices against TCGdex); it is the trade assistant's
 * provider when {@code app.price-provider=tcgdex}, otherwise pokemontcg.io is {@code @Primary}.
 */
@Slf4j
@Service
public class TcgdexPriceProvider implements PriceProvider {

    public static final String PROVIDER_NAME = "TCGDEX";
    private static final String DEFAULT_BASE_URL = "https://api.tcgdex.net/v2/en";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public TcgdexPriceProvider(
            @Value("${app.tcgdex.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl
    ) {
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        log.info("Using TCGdex as the price provider (no API key required).");
    }

    // Testing constructor
    public TcgdexPriceProvider(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<CardMarketPrice> fetchPrice(String cardId) {
        try {
            return fetchCardStrict(cardId);
        } catch (ProviderUnavailableException ex) {
            log.warn("Failed to fetch price from tcgdex.net for cardId={}: {}", cardId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<CardMarketPrice> searchCards(String query, int limit) {
        try {
            return searchCardsStrict(query, limit);
        } catch (ProviderUnavailableException ex) {
            log.warn("Failed to search tcgdex.net for query='{}': {}", query, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /** Like {@link #fetchPrice} but distinguishes "no such card" (empty) from an outage (exception). */
    public Optional<CardMarketPrice> fetchCardStrict(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return Optional.empty();
        }
        try {
            log.info("Fetching card price from tcgdex.net for cardId={}", cardId);
            String responseBody = restClient.get()
                    .uri("/cards/{id}", cardId)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(mapJsonToCardMarketPrice(objectMapper.readTree(responseBody)));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (Exception ex) {
            throw new ProviderUnavailableException(ex.getMessage(), ex);
        }
    }

    /** Like {@link #searchCards} but throws on an outage instead of returning an empty list. */
    public List<CardMarketPrice> searchCardsStrict(String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int pageSize = Math.clamp(limit, 1, 500);
        try {
            log.info("Searching tcgdex.net cards: query='{}', limit={}", query, pageSize);
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards")
                            .queryParam("name", query.trim())
                            .build())
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) {
                return Collections.emptyList();
            }
            List<CardMarketPrice> results = new ArrayList<>();
            for (JsonNode itemNode : root) {
                if (results.size() >= pageSize) {
                    break;
                }
                CardMarketPrice card = mapJsonToCardMarketPrice(itemNode);
                if (card != null) {
                    results.add(card);
                }
            }
            return results;
        } catch (HttpClientErrorException.NotFound notFound) {
            return Collections.emptyList();
        } catch (Exception ex) {
            throw new ProviderUnavailableException(ex.getMessage(), ex);
        }
    }

    /** A TCGdex set: card ids in it start with "{id}-" (e.g. "me02-125" is in "me02" Phantasmal Flames). */
    public record SetInfo(String id, String name, Integer officialCount) {}

    /** All sets; throws ProviderUnavailableException on an outage. */
    public List<SetInfo> fetchSetsStrict() {
        try {
            String responseBody = restClient.get().uri("/sets").retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(responseBody == null ? "[]" : responseBody);
            List<SetInfo> sets = new ArrayList<>();
            for (JsonNode node : root) {
                String id = node.path("id").asText(null);
                String name = node.path("name").asText(null);
                if (id != null && name != null) {
                    JsonNode official = node.path("cardCount").path("official");
                    sets.add(new SetInfo(id, name, official.isInt() ? official.asInt() : null));
                }
            }
            return sets;
        } catch (Exception ex) {
            throw new ProviderUnavailableException(ex.getMessage(), ex);
        }
    }

    /** TCGdex could not be reached or returned an error (not "not found"). */
    public static class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private CardMarketPrice mapJsonToCardMarketPrice(JsonNode node) {
        String id = node.path("id").asText(null);
        if (id == null) {
            return null;
        }

        String name = node.path("name").asText(null);
        String setName = node.path("set").path("name").asText(null);
        String number = node.path("localId").asText(null);
        String image = node.path("image").asText(null);
        String smallImage = image != null ? image + "/low.webp" : null;
        String largeImage = image != null ? image + "/high.webp" : smallImage;

        JsonNode tcgplayer = node.path("pricing").path("tcgplayer");
        Map<String, BigDecimal> variantPrices = extractVariantPricesUsd(tcgplayer);
        JsonNode cardmarket = node.path("pricing").path("cardmarket");
        BigDecimal eurTrend = cardmarket.path("trend").isNumber()
                ? BigDecimal.valueOf(cardmarket.path("trend").asDouble()).setScale(2, RoundingMode.HALF_UP)
                : null;
        String updated = tcgplayer.path("updated").isTextual() ? tcgplayer.path("updated").asText()
                : cardmarket.path("updated").isTextual() ? cardmarket.path("updated").asText() : null;

        return CardMarketPrice.builder()
                .cardId(id)
                .name(name)
                .setName(setName)
                .cardNumber(number)
                .imageUrl(smallImage)
                .largeImageUrl(largeImage)
                .marketPriceUsd(preferredPrice(variantPrices))
                .source(PROVIDER_NAME)
                .rarity(node.path("rarity").asText(null))
                .category(node.path("category").asText(null))
                .variantPricesUsd(variantPrices)
                .eurTrend(eurTrend)
                .pricesUpdatedAt(updated)
                .setOfficialCount(node.path("set").path("cardCount").path("official").isInt()
                        ? node.path("set").path("cardCount").path("official").asInt() : null)
                .build();
    }

    // Preferred variant order for the headline price; TCGdex uses hyphenated keys
    private static final String[] PREFERRED_VARIANTS = {
            "holofoil", "normal", "reverse-holofoil", "1st-edition-holofoil", "1st-edition-normal",
            "unlimited-holofoil", "unlimited-normal", "reverseHolofoil", "1stEditionHolofoil", "unlimitedHolofoil"};

    /** TCGplayer market price (USD) per variant; variants without a market price are left out. */
    private Map<String, BigDecimal> extractVariantPricesUsd(JsonNode tcgplayerNode) {
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        if (tcgplayerNode.isMissingNode() || tcgplayerNode.isNull()) {
            return prices;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = tcgplayerNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode variant = entry.getValue();
            if (!variant.isObject()) {
                continue; // "unit", "updated"
            }
            JsonNode market = variant.path("marketPrice").isNumber() ? variant.path("marketPrice") : variant.path("market");
            if (market.isNumber() && market.asDouble() > 0) {
                prices.put(entry.getKey(), BigDecimal.valueOf(market.asDouble()).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return prices;
    }

    private static BigDecimal preferredPrice(Map<String, BigDecimal> variantPrices) {
        for (String key : PREFERRED_VARIANTS) {
            if (variantPrices.containsKey(key)) {
                return variantPrices.get(key);
            }
        }
        return variantPrices.values().stream().findFirst().orElse(null);
    }
}
