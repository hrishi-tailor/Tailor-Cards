package com.tailorcards.api.trade.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TcgdexPriceProvider Tests")
class TcgdexPriceProviderTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Successfully fetches card price, metadata, and images from tcgdex.net")
    void testFetchPriceSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tcgdex.net/v2/en");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String cardJson = """
                {
                  "id": "base1-4",
                  "localId": "4",
                  "name": "Charizard",
                  "image": "https://assets.tcgdex.net/en/base/base1/4",
                  "set": {
                    "id": "base1",
                    "name": "Base Set"
                  },
                  "pricing": {
                    "tcgplayer": {
                      "unit": "USD",
                      "updated": "2026-10-08T22:54:34.137Z",
                      "holofoil": {
                        "lowPrice": 199.99,
                        "marketPrice": 285.50
                      },
                      "reverse-holofoil": {
                        "marketPrice": 12.25
                      }
                    },
                    "cardmarket": {
                      "unit": "EUR",
                      "trend": 240.10
                    }
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.tcgdex.net/v2/en/cards/base1-4"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(cardJson, MediaType.APPLICATION_JSON));

        TcgdexPriceProvider provider = new TcgdexPriceProvider(restClient, objectMapper);
        Optional<CardMarketPrice> result = provider.fetchPrice("base1-4");

        assertThat(result).isPresent();
        CardMarketPrice card = result.get();
        assertThat(card.cardId()).isEqualTo("base1-4");
        assertThat(card.name()).isEqualTo("Charizard");
        assertThat(card.setName()).isEqualTo("Base Set");
        assertThat(card.cardNumber()).isEqualTo("4");
        assertThat(card.imageUrl()).isEqualTo("https://assets.tcgdex.net/en/base/base1/4/low.webp");
        assertThat(card.largeImageUrl()).isEqualTo("https://assets.tcgdex.net/en/base/base1/4/high.webp");
        assertThat(card.marketPriceUsd()).isEqualByComparingTo(new BigDecimal("285.50"));
        assertThat(card.source()).isEqualTo("TCGDEX");
        // Real TCGdex shape: hyphenated variant keys with "marketPrice"
        assertThat(card.variantPricesUsd())
                .containsEntry("holofoil", new BigDecimal("285.50"))
                .containsEntry("reverse-holofoil", new BigDecimal("12.25"))
                .doesNotContainKeys("unit", "updated");
        assertThat(card.eurTrend()).isEqualByComparingTo("240.10");
        assertThat(card.pricesUpdatedAt()).isEqualTo("2026-10-08T22:54:34.137Z");
        mockServer.verify();
    }

    @Test
    @DisplayName("Returns empty optional when card is not found or API 404s")
    void testFetchPriceNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tcgdex.net/v2/en");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(requestTo("https://api.tcgdex.net/v2/en/cards/nonexistent-999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        TcgdexPriceProvider provider = new TcgdexPriceProvider(restClient, objectMapper);
        Optional<CardMarketPrice> result = provider.fetchPrice("nonexistent-999");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Returns empty optional on server error / rate-limit style failures")
    void testFetchPriceServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tcgdex.net/v2/en");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(requestTo("https://api.tcgdex.net/v2/en/cards/base1-4"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        TcgdexPriceProvider provider = new TcgdexPriceProvider(restClient, objectMapper);
        Optional<CardMarketPrice> result = provider.fetchPrice("base1-4");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Searches cards and parses brief card items correctly")
    void testSearchCardsSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tcgdex.net/v2/en");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String searchJson = """
                [
                  {
                    "id": "base1-4",
                    "localId": "4",
                    "name": "Charizard",
                    "image": "https://assets.tcgdex.net/en/base/base1/4"
                  }
                ]
                """;

        mockServer.expect(requestTo("https://api.tcgdex.net/v2/en/cards?name=Charizard"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));

        TcgdexPriceProvider provider = new TcgdexPriceProvider(restClient, objectMapper);
        List<CardMarketPrice> cards = provider.searchCards("Charizard", 10);

        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().cardId()).isEqualTo("base1-4");
        assertThat(cards.getFirst().name()).isEqualTo("Charizard");
        assertThat(cards.getFirst().source()).isEqualTo("TCGDEX");
        mockServer.verify();
    }

    @Test
    @DisplayName("Provider name is TCGDEX")
    void testProviderName() {
        TcgdexPriceProvider provider = new TcgdexPriceProvider(
                RestClient.builder().baseUrl("https://api.tcgdex.net/v2/en").build(), objectMapper);
        assertThat(provider.getProviderName()).isEqualTo("TCGDEX");
    }
}
