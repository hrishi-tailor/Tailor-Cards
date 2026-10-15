package com.tailorcards.api.buylistchat.resolution;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider.SetInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Card lookup: sets and their gallery subsets")
class CardLookupServiceTest {

    private CardLookupService lookup() {
        TcgdexPriceProvider tcgdex = mock(TcgdexPriceProvider.class);
        when(tcgdex.fetchSetsStrict()).thenReturn(List.of(
                new SetInfo("swsh11", "Lost Origin", 196),
                new SetInfo("swsh11tg", "Lost Origin Trainer Gallery", 30),
                new SetInfo("swsh12.5", "Crown Zenith", 159),
                new SetInfo("swsh12.5gg", "Crown Zenith Galarian Gallery", 70),
                new SetInfo("base1", "Base Set", 102),
                new SetInfo("base4", "Base Set 2", 130)));
        when(tcgdex.fetchCardStrict(anyString())).thenReturn(Optional.empty());
        when(tcgdex.fetchCardStrict("swsh11tg-TG05")).thenReturn(Optional.of(CardMarketPrice.builder()
                .cardId("swsh11tg-TG05").name("Pikachu").setName("Lost Origin Trainer Gallery").cardNumber("TG05").build()));
        BuylistChatProperties props = new BuylistChatProperties();
        props.getResolution().setRequestsPerSecond(1000);
        return new CardLookupService(tcgdex, props);
    }

    @Test
    @DisplayName("A set brings its Trainer Gallery / Galarian Gallery subset, but not other sets that share its name")
    void subsets() {
        CardLookupService lookup = lookup();
        assertThat(lookup.setIdsFor("Lost Origin")).containsExactly("swsh11", "swsh11tg");
        assertThat(lookup.setIdsFor("crown zenith")).containsExactly("swsh12.5", "swsh12.5gg");
        assertThat(lookup.setIdsFor("Base Set")).containsExactly("base1");
    }

    @Test
    @DisplayName("Pikachu TG05 in Lost Origin is found in the Trainer Gallery set")
    void trainerGalleryNumber() {
        CardLookupService lookup = lookup();
        Optional<CardMarketPrice> card = lookup.findBySetAndNumber(lookup.setIdsFor("Lost Origin"), "TG05");
        assertThat(card).map(CardMarketPrice::cardId).contains("swsh11tg-TG05");
    }

    @Test
    @DisplayName("Lines asking for the same card at the same moment share one TCGdex request")
    void sharesInFlightRequests() throws Exception {
        TcgdexPriceProvider tcgdex = mock(TcgdexPriceProvider.class);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(tcgdex.fetchCardStrict("base1-4")).thenAnswer(inv -> {
            calls.incrementAndGet();
            Thread.sleep(200);
            return Optional.of(CardMarketPrice.builder().cardId("base1-4").name("Charizard").build());
        });
        BuylistChatProperties props = new BuylistChatProperties();
        props.getResolution().setRequestsPerSecond(1000);
        CardLookupService lookup = new CardLookupService(tcgdex, props);

        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<Optional<CardMarketPrice>>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(pool.submit(() -> lookup.card("base1-4")));
            for (var r : results) assertThat(r.get()).map(CardMarketPrice::name).contains("Charizard");
        }
        assertThat(calls.get()).isEqualTo(1);
        assertThat(lookup.stats().requests()).isEqualTo(1);
        assertThat(lookup.stats().cacheHits()).isEqualTo(3);
    }

    @Test
    @DisplayName("A failed request reaches every waiter and isn't cached")
    void sharedFailureNotCached() {
        TcgdexPriceProvider tcgdex = mock(TcgdexPriceProvider.class);
        when(tcgdex.fetchCardStrict("x-1"))
                .thenThrow(new TcgdexPriceProvider.ProviderUnavailableException("down", null))
                .thenReturn(Optional.empty());
        BuylistChatProperties props = new BuylistChatProperties();
        props.getResolution().setRequestsPerSecond(1000);
        CardLookupService lookup = new CardLookupService(tcgdex, props);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lookup.card("x-1"))
                .isInstanceOf(TcgdexPriceProvider.ProviderUnavailableException.class);
        assertThat(lookup.card("x-1")).isEmpty(); // tried again, not served a cached failure
    }
}
