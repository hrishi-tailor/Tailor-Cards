package com.tailorcards.api.trade.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

@Slf4j
@Service
public class PokemonTcgIoPriceProvider implements PriceProvider {

    public static final String PROVIDER_NAME = "POKEMONTCG_IO";
    private static final String DEFAULT_BASE_URL = "https://api.pokemontcg.io/v2";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    public PokemonTcgIoPriceProvider(
            @Value("${app.pokemontcg.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
            @Value("${POKEMONTCG_API_KEY:${app.pokemontcg.api-key:}}") String apiKey
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (!this.apiKey.isEmpty()) {
            builder.defaultHeader("X-Api-Key", this.apiKey);
        }

        this.restClient = builder.build();
    }

    // Testing constructor
    public PokemonTcgIoPriceProvider(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.apiKey = "";
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
            log.info("Fetching card price from pokemontcg.io for cardId={}", cardId);
            String responseBody = restClient.get()
                    .uri("/cards/{id}", cardId)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                return Optional.empty();
            }

            return Optional.ofNullable(mapJsonToCardMarketPrice(dataNode));
        } catch (Exception ex) {
            log.warn("Failed to fetch price from pokemontcg.io for cardId={}: {}", cardId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<CardMarketPrice> searchCards(String query, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int pageSize = Math.clamp(limit, 1, 50);
        String formattedQuery = formatQuery(query.trim());

        try {
            log.info("Searching pokemontcg.io cards: query='{}', pageSize={}", formattedQuery, pageSize);
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cards")
                            .queryParam("q", formattedQuery)
                            .queryParam("pageSize", pageSize)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) {
                return Collections.emptyList();
            }

            List<CardMarketPrice> results = new ArrayList<>();
            for (JsonNode itemNode : dataArray) {
                CardMarketPrice card = mapJsonToCardMarketPrice(itemNode);
                if (card != null) {
                    results.add(card);
                }
            }
            return results;
        } catch (Exception ex) {
            log.warn("Failed to search pokemontcg.io for query='{}': {}", query, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String formatQuery(String query) {
        if (query.startsWith("name:") || query.contains(":") || query.contains("*")) {
            return query;
        }
        // If user enters "Charizard Base Set", format as name:"*Charizard*"
        String sanitized = query.replaceAll("[\"\\\\]", "");
        return "name:\"*" + sanitized + "*\"";
    }

    private CardMarketPrice mapJsonToCardMarketPrice(JsonNode node) {
        String id = node.path("id").asText(null);
        if (id == null) {
            return null;
        }

        String name = node.path("name").asText(null);
        String setName = node.path("set").path("name").asText(null);
        String number = node.path("number").asText(null);
        String smallImage = node.path("images").path("small").asText(null);
        String largeImage = node.path("images").path("large").asText(null);

        BigDecimal marketPriceUsd = extractMarketPriceUsd(node.path("tcgplayer").path("prices"));

        return CardMarketPrice.builder()
                .cardId(id)
                .name(name)
                .setName(setName)
                .cardNumber(number)
                .imageUrl(smallImage)
                .largeImageUrl(largeImage != null ? largeImage : smallImage)
                .marketPriceUsd(marketPriceUsd)
                .source(PROVIDER_NAME)
                .build();
    }

    private BigDecimal extractMarketPriceUsd(JsonNode pricesNode) {
        if (pricesNode.isMissingNode() || pricesNode.isNull()) {
            return null;
        }

        // Ordered priority of price subcategories
        String[] preferredKeys = {"holofoil", "normal", "reverseHolofoil", "1stEditionHolofoil", "unlimitedHolofoil"};
        for (String key : preferredKeys) {
            JsonNode categoryNode = pricesNode.path(key);
            if (!categoryNode.isMissingNode()) {
                JsonNode marketNode = categoryNode.path("market");
                if (marketNode.isNumber()) {
                    return BigDecimal.valueOf(marketNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        // Fallback: iterate any subcategory object
        Iterator<Map.Entry<String, JsonNode>> fields = pricesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode catNode = entry.getValue();
            if (catNode.isObject()) {
                JsonNode marketNode = catNode.path("market");
                if (marketNode.isNumber()) {
                    return BigDecimal.valueOf(marketNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
                }
                JsonNode midNode = catNode.path("mid");
                if (midNode.isNumber()) {
                    return BigDecimal.valueOf(midNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        return null;
    }
}
