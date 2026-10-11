package com.tailorcards.api.buylistchat.resolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

@DisplayName("Graded prices (Pokémon TCG API on RapidAPI)")
class GradedPriceServiceTest {

    private static final String BASE = "https://pokemon-tcg-api.p.rapidapi.com";

    // Shape as returned by /cards/search (trimmed)
    private static final String CHARIZARD_V = """
            {"data":[{"id":4868,"name":"Charizard V","card_number":154,"tcgid":"swsh9-154",
              "episode":{"name":"Brilliant Stars"},
              "prices":{"ebay":{"currency":"USD","graded":{
                "psa":{"10":{"median_price":549.86,"sample_size":5},"9":{"median_price":246.88,"sample_size":5}},
                "bgs":{"10":{"median_price":19698.59,"sample_size":2}}}}}}],
             "paging":{"current":1,"total":1,"per_page":20},"results":1}
            """;

    private record Setup(GradedPriceService service, MockRestServiceServer server) {}

    private static Setup setup(String key) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new GradedPriceService(builder.build(), key, 720, Clock.systemUTC()), server);
    }

    @Test
    @DisplayName("Matches by TCGdex id and reads the eBay median for the exact grade, with its sale count; cached")
    void byTcgId() {
        Setup s = setup("key");
        s.server().expect(once(), requestTo(startsWith(BASE + "/cards/search")))
                .andExpect(queryParam("tcgid", "swsh9-154"))
                .andRespond(withSuccess(CHARIZARD_V, MediaType.APPLICATION_JSON));

        var psa10 = s.service().gradedPrice("swsh9-154", "Charizard V", "Brilliant Stars", "154", "PSA 10");
        var psa9 = s.service().gradedPrice("swsh9-154", "Charizard V", "Brilliant Stars", "154", "PSA 9");

        assertThat(psa10).isPresent();
        assertThat(psa10.get().usd()).isEqualByComparingTo("549.86");
        assertThat(psa10.get().saleCount()).isEqualTo(5);
        assertThat(psa10.get().source()).isEqualTo(GradedPriceService.SOURCE);
        assertThat(psa9.get().usd()).isEqualByComparingTo("246.88");
        assertThat(s.service().gradedPrice("swsh9-154", "Charizard V", "Brilliant Stars", "154", "CGC 10")).isEmpty();
        s.server().verify(); // one request for all three
    }

    @Test
    @DisplayName("Falls back to name + number + set when the TCGdex id differs")
    void byNameNumberSet() {
        Setup s = setup("key");
        s.server().expect(requestTo(startsWith(BASE + "/cards/search"))).andExpect(queryParam("tcgid", "sv03.5-154"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        s.server().expect(requestTo(startsWith(BASE + "/cards/search"))).andExpect(queryParam("search", "Charizard%20V%20154"))
                .andRespond(withSuccess(CHARIZARD_V, MediaType.APPLICATION_JSON));

        var price = s.service().gradedPrice("sv03.5-154", "Charizard V", "Brilliant Stars", "154/172", "PSA 10");
        assertThat(price).isPresent();
        s.server().verify();
    }

    @Test
    @DisplayName("Black Label / Pristine never fall back to a plain 10; no key or HTTP errors give empty")
    void emptyCases() {
        assertThat(GradedPriceService.gradeKey("BGS 10 Black Label")).isEmpty();
        assertThat(GradedPriceService.gradeKey("CGC 10 Pristine")).isEmpty();
        assertThat(GradedPriceService.gradeKey("BGS 9.5").get()).containsExactly("bgs", "9.5");
        assertThat(GradedPriceService.gradeKey("psa  10").get()).containsExactly("psa", "10");

        Setup noKey = setup("");
        assertThat(noKey.service().isConfigured()).isFalse();
        assertThat(noKey.service().gradedPrice("swsh9-154", "Charizard V", null, "154", "PSA 10")).isEmpty();
        noKey.server().verify(); // no calls made

        Setup limited = setup("key");
        limited.server().expect(requestTo(startsWith(BASE + "/cards/search")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        assertThat(limited.service().gradedPrice("swsh9-154", null, null, null, "PSA 10")).isEmpty();
    }
}
