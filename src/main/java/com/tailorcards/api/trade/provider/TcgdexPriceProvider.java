package com.tailorcards.api.trade.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Free, key-less Pokémon TCG pricing provider backed by tcgdex.net.
 * Selected via {@code app.price-provider=tcgdex}.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app", name = "price-provider", havingValue = "tcgdex")
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

            JsonNode root = objectMapper.readTree(responseBody);
            return Optional.ofNullable(mapJsonToCardMarketPrice(root));
        } catch (Exception ex) {
            log.warn("Failed to fetch price from tcgdex.net for cardId={}: {}", cardId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<CardMarketPrice> searchCards(String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int pageSize = Math.clamp(limit, 1, 50);

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
        } catch (Exception ex) {
            log.warn("Failed to search tcgdex.net for query='{}': {}", query, ex.getMessage());
            return Collections.emptyList();
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

        BigDecimal marketPriceUsd = extractMarketPriceUsd(node.path("pricing").path("tcgplayer"));

        return CardMarketPrice.builder()
                .cardId(id)
                .name(name)
                .setName(setName)
                .cardNumber(number)
                .imageUrl(smallImage)
                .largeImageUrl(largeImage)
                .marketPriceUsd(marketPriceUsd)
                .source(PROVIDER_NAME)
                .build();
    }

    private BigDecimal extractMarketPriceUsd(JsonNode tcgplayerNode) {
        if (tcgplayerNode.isMissingNode() || tcgplayerNode.isNull()) {
            return null;
        }

        // Ordered priority of price subcategories
        String[] preferredKeys = {"holofoil", "normal", "reverseHolofoil", "1stEditionHolofoil", "unlimitedHolofoil"};
        for (String key : preferredKeys) {
            JsonNode categoryNode = tcgplayerNode.path(key);
            if (!categoryNode.isMissingNode()) {
                JsonNode marketNode = categoryNode.path("market");
                if (marketNode.isNumber()) {
                    return BigDecimal.valueOf(marketNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        // Fallback: iterate any subcategory object
        Iterator<Map.Entry<String, JsonNode>> fields = tcgplayerNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode catNode = entry.getValue();
            if (catNode.isObject()) {
                JsonNode marketNode = catNode.path("market");
                if (marketNode.isNumber()) {
                    return BigDecimal.valueOf(marketNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        return null;
    }
}
