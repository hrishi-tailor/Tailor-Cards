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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PokemonTcgIoPriceProvider Tests")
class PokemonTcgIoPriceProviderTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Successfully fetches card price, metadata, and images from pokemontcg.io")
    void testFetchPriceSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.pokemontcg.io/v2");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String cardJson = """
                {
                  "data": {
                    "id": "base1-4",
                    "name": "Charizard",
                    "number": "4",
                    "set": {
                      "id": "base1",
                      "name": "Base Set"
                    },
                    "images": {
                      "small": "https://images.pokemontcg.io/base1/4.png",
                      "large": "https://images.pokemontcg.io/base1/4_hires.png"
                    },
                    "tcgplayer": {
                      "prices": {
                        "holofoil": {
                          "market": 285.50
                        }
                      }
                    }
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.pokemontcg.io/v2/cards/base1-4"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(cardJson, MediaType.APPLICATION_JSON));

        PokemonTcgIoPriceProvider provider = new PokemonTcgIoPriceProvider(restClient, objectMapper);
        Optional<CardMarketPrice> result = provider.fetchPrice("base1-4");

        assertThat(result).isPresent();
        CardMarketPrice card = result.get();
        assertThat(card.cardId()).isEqualTo("base1-4");
        assertThat(card.name()).isEqualTo("Charizard");
        assertThat(card.setName()).isEqualTo("Base Set");
        assertThat(card.cardNumber()).isEqualTo("4");
        assertThat(card.imageUrl()).isEqualTo("https://images.pokemontcg.io/base1/4.png");
        assertThat(card.largeImageUrl()).isEqualTo("https://images.pokemontcg.io/base1/4_hires.png");
        assertThat(card.marketPriceUsd()).isEqualByComparingTo(new BigDecimal("285.50"));
        assertThat(card.source()).isEqualTo("POKEMONTCG_IO");
        mockServer.verify();
    }

    @Test
    @DisplayName("Returns empty optional when card is not found or API 404s")
    void testFetchPriceNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.pokemontcg.io/v2");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(requestTo("https://api.pokemontcg.io/v2/cards/nonexistent-999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        PokemonTcgIoPriceProvider provider = new PokemonTcgIoPriceProvider(restClient, objectMapper);
        Optional<CardMarketPrice> result = provider.fetchPrice("nonexistent-999");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Searches cards and parses card items correctly")
    void testSearchCardsSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.pokemontcg.io/v2");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String searchJson = """
                {
                  "data": [
                    {
                      "id": "base1-4",
                      "name": "Charizard",
                      "number": "4",
                      "set": { "name": "Base Set" },
                      "images": { "small": "https://img.png", "large": "https://img-lg.png" },
                      "tcgplayer": {
                        "prices": {
                          "holofoil": { "market": 300.00 }
                        }
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.pokemontcg.io/v2/cards?q=name:%22*Charizard*%22&pageSize=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(searchJson, MediaType.APPLICATION_JSON));

        PokemonTcgIoPriceProvider provider = new PokemonTcgIoPriceProvider(restClient, objectMapper);
        List<CardMarketPrice> cards = provider.searchCards("Charizard", 10);

        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().cardId()).isEqualTo("base1-4");
        assertThat(cards.getFirst().name()).isEqualTo("Charizard");
        mockServer.verify();
    }
}
