package com.tailorcards.api.buylistchat.resolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Sealed product prices (Pokémon TCG API on RapidAPI)")
class SealedPriceServiceTest {

    private static final String BASE = "https://pokemon-tcg-api.p.rapidapi.com";

    private static final String PRODUCTS_JSON = """
            {"data":[
              {"id":1001,"name":"Scarlet & Violet 151 Elite Trainer Box",
               "episode":{"name":"151"},
               "image":"https://example.com/151-etb.jpg",
               "prices":{"cardmarket":{"30d_average":94.50}}},
              {"id":1002,"name":"Scarlet & Violet 151 Booster Bundle",
               "episode":{"name":"151"},
               "image":"https://example.com/151-bundle.jpg",
               "prices":{"cardmarket":{"30d_average":42.00}}},
              {"id":1003,"name":"Scarlet & Violet 151 Poster Collection",
               "episode":{"name":"151"},
               "image":"https://example.com/151-poster.jpg",
               "prices":{"cardmarket":{"30d_average":0}}}
            ],
            "paging":{"current":1,"total":1,"per_page":20},"results":3}
            """;

    private record Setup(SealedPriceService service, MockRestServiceServer server) {}

    private static Setup setup(String key) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new SealedPriceService(builder.build(), key, 720, Clock.systemUTC()), server);
    }

    @Test
    @DisplayName("Searches products, expands ETB to Elite Trainer Box, extracts Cardmarket 30d average in EUR, cached")
    void searchAndCache() {
        Setup s = setup("test-key");
        s.server().expect(once(), requestTo(startsWith(BASE + "/products/search")))
                .andExpect(queryParam("search", "151%20Elite%20Trainer%20Box"))
                .andExpect(queryParam("per_page", "20"))
                .andRespond(withSuccess(PRODUCTS_JSON, MediaType.APPLICATION_JSON));

        List<SealedPriceService.SealedProduct> results = s.service().search("151 ETB", 5);

        assertThat(results).hasSize(3);
        SealedPriceService.SealedProduct top = results.getFirst();
        assertThat(top.id()).isEqualTo(1001L);
        assertThat(top.name()).isEqualTo("Scarlet & Violet 151 Elite Trainer Box");
        assertThat(top.setName()).isEqualTo("151");
        assertThat(top.imageUrl()).isEqualTo("https://example.com/151-etb.jpg");
        assertThat(top.eur30dAverage()).isEqualByComparingTo(new BigDecimal("94.50"));
        assertThat(top.matchScore()).isGreaterThanOrEqualTo(0.75);

        // Third item has 0 price -> null
        assertThat(results.get(2).eur30dAverage()).isNull();

        // Second call with same query uses cache without hitting server
        List<SealedPriceService.SealedProduct> cached = s.service().search("151 ETB", 2);
        assertThat(cached).hasSize(2);
        assertThat(cached.getFirst().id()).isEqualTo(1001L);
        s.server().verify();
    }

    @Test
    @DisplayName("bestMatch returns the top match when match score >= 0.75, else empty")
    void bestMatch() {
        Setup s = setup("test-key");
        s.server().expect(requestTo(startsWith(BASE + "/products/search")))
                .andExpect(queryParam("search", "Scarlet%20%26%20Violet%20151%20Elite%20Trainer%20Box"))
                .andRespond(withSuccess(PRODUCTS_JSON, MediaType.APPLICATION_JSON));

        Optional<SealedPriceService.SealedProduct> match = s.service().bestMatch("Scarlet & Violet 151 Elite Trainer Box");
        assertThat(match).isPresent();
        assertThat(match.get().id()).isEqualTo(1001L);
        s.server().verify();

        // Query with poor overlap returns empty
        Setup s2 = setup("test-key");
        s2.server().expect(requestTo(startsWith(BASE + "/products/search")))
                .andExpect(queryParam("search", "Sun%20%26%20Moon%20Celestial%20Storm%20Booster%20Box"))
                .andRespond(withSuccess(PRODUCTS_JSON, MediaType.APPLICATION_JSON));

        Optional<SealedPriceService.SealedProduct> noMatch = s2.service().bestMatch("Sun & Moon Celestial Storm Booster Box");
        assertThat(noMatch).isEmpty();
        s2.server().verify();
    }

    @Test
    @DisplayName("Empty or unconfigured key returns empty without making HTTP requests; HTTP errors return empty")
    void unconfiguredAndErrors() {
        Setup unconfigured = setup("");
        assertThat(unconfigured.service().isConfigured()).isFalse();
        assertThat(unconfigured.service().search("151 ETB", 5)).isEmpty();
        unconfigured.server().verify();

        Setup errorSetup = setup("test-key");
        errorSetup.server().expect(requestTo(startsWith(BASE + "/products/search")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(errorSetup.service().search("151 ETB", 5)).isEmpty();
        errorSetup.server().verify();
    }

    @Test
    @DisplayName("expand handles abbreviations and score calculates word overlap")
    void expansionAndScoring() {
        assertThat(SealedPriceService.expand("151 ETB")).isEqualTo("151 Elite Trainer Box");
        assertThat(SealedPriceService.expand("151 etbs")).isEqualTo("151 Elite Trainer Box");
        assertThat(SealedPriceService.expand("Charizard UPC")).isEqualTo("Charizard Ultra-Premium Collection");

        double exactScore = SealedPriceService.score("151 Elite Trainer Box", "Scarlet & Violet 151 Elite Trainer Box");
        assertThat(exactScore).isEqualTo(1.0);

        double partialScore = SealedPriceService.score("151 Booster Box", "Scarlet & Violet 151 Elite Trainer Box");
        assertThat(partialScore).isLessThan(0.75);
    }
}
