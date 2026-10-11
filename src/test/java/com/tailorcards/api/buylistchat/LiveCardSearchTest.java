package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Calls the real TCGdex API with the ways the assistant tends to phrase a search. Skipped unless
 * run with -Dlive=true (the normal suite never touches the network):
 * ./mvnw test -Dtest=LiveCardSearchTest -Dlive=true
 */
@DisplayName("Live TCGdex: search_cards understands how customers name cards")
class LiveCardSearchTest {

    private static BuylistChatService service;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        assumeTrue(Boolean.getBoolean("live"), "live TCGdex test: run with -Dlive=true");
        BuylistChatProperties props = new BuylistChatProperties();
        CardLookupService lookup = new CardLookupService(new TcgdexPriceProvider("https://api.tcgdex.net/v2/en"), props);
        service = new BuylistChatService(mock(AnthropicClient.class), mock(BuylistLlmBudget.class), new LlmRateLimiter(20),
                mock(BuylistDraftService.class), lookup, null, props, null, true);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "{\"name\":\"Charizard\",\"set_name\":\"Brilliant Stars\",\"card_number\":\"154\"} | swsh9-154",
            "{\"name\":\"brilliant stars charizard 154\"} | swsh9-154",
            "{\"name\":\"Brilliant Stars Charizard\",\"card_number\":\"154\"} | swsh9-154",
            "{\"name\":\"charizard 154\",\"set_name\":\"brilliant stars\"} | swsh9-154",
            "{\"name\":\"Charizard V\",\"card_number\":\"154/172\"} | swsh9-154",
            "{\"set_name\":\"Brilliant Stars\",\"card_number\":\"154\"} | swsh9-154",
            "{\"name\":\"phantasmal flames mega charizard x ex 125\"} | me02-125",
            "{\"name\":\"Mega Charizard X-EX\",\"set_name\":\"Phantasmal Flames\",\"card_number\":\"125\"} | me02-125",
            "{\"name\":\"Bulbasaur\",\"set_name\":\"151\",\"card_number\":\"1\"} | sv03.5-001",
            "{\"name\":\"Charizard\",\"set_name\":\"Base Set\",\"card_number\":\"4/102\"} | base1-4",
    })
    void findsTheCard(String input, String expectedId) throws Exception {
        JsonNode result = JSON.readTree(service.executeTool("a@example.com", "d", "search_cards", JSON.readTree(input)));
        assertThat(result.path("results").size()).as(result.toString()).isGreaterThan(0);
        assertThat(result.path("results").get(0).path("id").asText()).as(result.toString()).isEqualTo(expectedId);
    }
}
