package com.tailorcards.api.buylistchat.resolution;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.repository.BuylistDraftLineRepository;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Card resolution against TCGdex")
class CardResolutionServiceTest {

    private TcgdexPriceProvider tcgdex;
    private GradedPriceService graded;
    private CardResolutionService service;

    @BeforeEach
    void setUp() {
        tcgdex = mock(TcgdexPriceProvider.class);
        graded = mock(GradedPriceService.class);
        BuylistChatProperties props = new BuylistChatProperties();
        props.getResolution().setAsync(false);
        props.getResolution().setRequestsPerSecond(1000);
        service = new CardResolutionService(mock(BuylistDraftLineRepository.class), new CardLookupService(tcgdex, props), props, null, graded);
    }

    static CardMarketPrice brief(String id, String number) {
        return CardMarketPrice.builder().cardId(id).name("Charizard").cardNumber(number).build();
    }

    static CardMarketPrice full(String id, String set, String number, Map<String, BigDecimal> variants) {
        BigDecimal headline = variants.isEmpty() ? null : variants.values().iterator().next();
        return CardMarketPrice.builder().cardId(id).name("Charizard").setName(set).cardNumber(number)
                .rarity("Rare Holo").category("Pokemon").variantPricesUsd(variants).marketPriceUsd(headline)
                .eurTrend(new BigDecimal("500.00")).pricesUpdatedAt("2026-10-08T22:54:34Z").source("TCGDEX").build();
    }

    static BuylistDraftLine line(String name, String set, String number, String variant) {
        return BuylistDraftLine.builder().id(1L).kind("CARD").name(name).setName(set).cardNumber(number)
                .variant(variant).quantity(1).resolveState("PENDING").build();
    }

    @Test
    @DisplayName("Name + number match resolves with high confidence and the variant's USD market price")
    void exactMatch() {
        when(tcgdex.searchCardsStrict(eq("Charizard"), anyInt()))
                .thenReturn(List.of(brief("base1-4", "4"), brief("base4-4", "4"), brief("swsh3-20", "20")));
        Map<String, BigDecimal> variants = new LinkedHashMap<>();
        variants.put("holofoil", new BigDecimal("928.32"));
        variants.put("reverse-holofoil", new BigDecimal("40.00"));
        when(tcgdex.fetchCardStrict("base1-4")).thenReturn(Optional.of(full("base1-4", "Base Set", "4", variants)));
        when(tcgdex.fetchCardStrict("base4-4")).thenReturn(Optional.of(full("base4-4", "Base Set 2", "4", variants)));

        BuylistDraftLine result = service.resolve(line("Charizard", "Base Set 2", "004/102", "reverse holo"));

        assertThat(result.getResolveState()).isEqualTo("RESOLVED");
        assertThat(result.getCardId()).isEqualTo("base4-4");
        assertThat(result.getIdConfidence()).isEqualByComparingTo("0.95");
        assertThat(result.getUnitMarketUsd()).isEqualByComparingTo("40.00");
        assertThat(result.getEurTrend()).isEqualByComparingTo("500.00");
        assertThat(result.getPriceUpdatedAt()).isEqualTo("2026-10-08T22:54:34Z");
    }

    @Test
    @DisplayName("The printed set size picks the set: 4/102 is Base Set, not Base Set 2 (4/130)")
    void setTotalDisambiguates() {
        when(tcgdex.searchCardsStrict(eq("Charizard"), anyInt())).thenReturn(List.of(brief("base4-4", "4"), brief("base1-4", "4")));
        when(tcgdex.fetchCardStrict("base4-4")).thenReturn(Optional.of(full("base4-4", "Base Set 2", "4",
                Map.of("holofoil", new BigDecimal("415.48"))).toBuilder().setOfficialCount(130).build()));
        when(tcgdex.fetchCardStrict("base1-4")).thenReturn(Optional.of(full("base1-4", "Base Set", "4",
                Map.of("holofoil", new BigDecimal("928.32"))).toBuilder().setOfficialCount(102).build()));

        BuylistDraftLine result = service.resolve(line("Charizard", null, "4/102", null));

        assertThat(result.getResolveState()).isEqualTo("RESOLVED");
        assertThat(result.getCardId()).isEqualTo("base1-4");
        assertThat(result.getUnitMarketUsd()).isEqualByComparingTo("928.32");
        assertThat(result.getIdConfidence()).isEqualByComparingTo("0.98");
    }

    @Test
    @DisplayName("Set name typed into the card name: 'Phantasmal Flames Mega Charizard X ex' 125 resolves to me02-125")
    void setNameInsideCardName() {
        when(tcgdex.fetchSetsStrict()).thenReturn(List.of(
                new TcgdexPriceProvider.SetInfo("me02", "Phantasmal Flames", 94),
                new TcgdexPriceProvider.SetInfo("mep", "Mega Evolution Black Star Promos", null)));
        when(tcgdex.searchCardsStrict(eq("Mega Charizard X ex"), anyInt())).thenReturn(List.of(
                brief("me02-013", "013"), brief("mep-023", "023"), brief("me02-125", "125"), brief("me02-130", "130")));
        when(tcgdex.fetchCardStrict("me02-125")).thenReturn(Optional.of(full("me02-125", "Phantasmal Flames", "125",
                Map.of("holofoil", new BigDecimal("650.00")))));

        BuylistDraftLine result = service.resolve(line("Phantasmal Flames Mega Charizard X ex", null, "125", null));

        assertThat(result.getResolveState()).isEqualTo("RESOLVED");
        assertThat(result.getCardId()).isEqualTo("me02-125");
        verify(tcgdex, times(0)).searchCardsStrict(eq("Phantasmal Flames Mega Charizard X ex"), anyInt());
    }

    @Test
    @DisplayName("Extra leading words are dropped until the name search matches")
    void flexibleNameSearch() {
        when(tcgdex.searchCardsStrict(anyString(), anyInt())).thenReturn(List.of());
        when(tcgdex.searchCardsStrict(eq("Charizard V"), anyInt())).thenReturn(List.of(brief("swsh9-154", "154")));
        when(tcgdex.fetchCardStrict("swsh9-154")).thenReturn(Optional.of(full("swsh9-154", "Brilliant Stars", "154", Map.of())));

        BuylistDraftLine result = service.resolve(line("Shiny Alt Charizard V", null, "154", null));

        assertThat(result.getCardId()).isEqualTo("swsh9-154");
    }

    @Test
    @DisplayName("Several matches without a set become AMBIGUOUS with candidates")
    void ambiguous() {
        when(tcgdex.searchCardsStrict(eq("Charizard"), anyInt())).thenReturn(List.of(brief("base1-4", "4"), brief("base4-4", "4")));
        when(tcgdex.fetchCardStrict("base1-4")).thenReturn(Optional.of(full("base1-4", "Base Set", "4", Map.of())));
        when(tcgdex.fetchCardStrict("base4-4")).thenReturn(Optional.of(full("base4-4", "Base Set 2", "4", Map.of())));

        BuylistDraftLine result = service.resolve(line("Charizard", null, "4", null));

        assertThat(result.getResolveState()).isEqualTo("AMBIGUOUS");
        assertThat(result.getCandidatesJson()).contains("base1-4").contains("base4-4");
        assertThat(result.getUnitMarketUsd()).as("no price stays null, not 0").isNull();
    }

    @Test
    @DisplayName("No match is NOT_FOUND; an outage is ERROR (needs review), never a failure")
    void notFoundAndOutage() {
        when(tcgdex.searchCardsStrict(eq("Notacard"), anyInt())).thenReturn(List.of());
        assertThat(service.resolve(line("Notacard", null, null, null)).getResolveState()).isEqualTo("NOT_FOUND");

        when(tcgdex.searchCardsStrict(eq("Pikachu"), anyInt()))
                .thenThrow(new TcgdexPriceProvider.ProviderUnavailableException("503", null));
        assertThat(service.resolve(line("Pikachu", null, null, null)).getResolveState()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Lookups are cached: the same name is searched once")
    void caching() {
        when(tcgdex.searchCardsStrict(eq("Charizard"), anyInt())).thenReturn(List.of(brief("base1-4", "4")));
        when(tcgdex.fetchCardStrict("base1-4")).thenReturn(Optional.of(full("base1-4", "Base Set", "4", Map.of("holofoil", BigDecimal.TEN))));

        for (int i = 0; i < 5; i++) {
            service.resolve(line("Charizard", null, "4", null));
        }
        verify(tcgdex, times(1)).searchCardsStrict(anyString(), anyInt());
        verify(tcgdex, times(1)).fetchCardStrict("base1-4");
    }

    @Test
    @DisplayName("Bulk lots skip card lookup")
    void bulk() {
        BuylistDraftLine bulk = BuylistDraftLine.builder().kind("BULK").name("Bulk commons").quantity(500).resolveState("PENDING").build();
        assertThat(service.resolve(bulk).getResolveState()).isEqualTo("RESOLVED");
        verify(tcgdex, times(0)).searchCardsStrict(anyString(), anyInt());
    }

    @Test
    @DisplayName("Graded line: the exact grade's price replaces the ungraded one (basis GRADED); none -> RAW")
    void gradedPrice() {
        when(tcgdex.searchCardsStrict(eq("Charizard"), anyInt())).thenReturn(List.of(brief("base1-4", "4")));
        when(tcgdex.fetchCardStrict("base1-4")).thenReturn(Optional.of(full("base1-4", "Base Set", "4",
                Map.of("holofoil", new BigDecimal("928.32")))));
        when(graded.gradedPrice("base1-4", "Charizard", "Base Set", "4", "PSA 10")).thenReturn(Optional.of(
                new GradedPriceService.GradedPrice(new BigDecimal("18500.00"), "PSA 10", "EBAY_GRADED", 4)));
        when(graded.gradedPrice("base1-4", "Charizard", "Base Set", "4", "PSA 9")).thenReturn(Optional.of(
                new GradedPriceService.GradedPrice(new BigDecimal("376.41"), "PSA 9", "EBAY_GRADED", 5)));

        BuylistDraftLine slab = line("Charizard", null, "4", null);
        slab.setGrading("PSA 10");
        BuylistDraftLine result = service.resolve(slab);
        assertThat(result.getUnitMarketUsd()).isEqualByComparingTo("18500.00");
        assertThat(result.getPriceBasis()).isEqualTo("GRADED");
        assertThat(result.getPriceSource()).isEqualTo("EBAY_GRADED");
        assertThat(result.getPriceSampleSize()).isEqualTo(4);

        // A 9 selling below an ungraded copy is mixed-up sales data: keep the raw price, priced by hand
        BuylistDraftLine suspicious = line("Charizard", null, "4", null);
        suspicious.setGrading("PSA 9");
        BuylistDraftLine kept = service.resolve(suspicious);
        assertThat(kept.getPriceBasis()).isEqualTo("RAW");
        assertThat(kept.getUnitMarketUsd()).isEqualByComparingTo("928.32");
        assertThat(kept.getPriceSampleSize()).isNull();

        BuylistDraftLine bgs = line("Charizard", null, "4", null);
        bgs.setGrading("BGS 9.5");
        BuylistDraftLine raw = service.resolve(bgs);
        assertThat(raw.getUnitMarketUsd()).isEqualByComparingTo("928.32");
        assertThat(raw.getPriceBasis()).isEqualTo("RAW");
    }

    @Test
    @DisplayName("Hyphenated names ('Mega Charizard X-EX') are retried with spaces, which TCGdex matches")
    void hyphenatedName() {
        when(tcgdex.searchCardsStrict(anyString(), anyInt())).thenReturn(List.of());
        when(tcgdex.searchCardsStrict(eq("Mega Charizard X EX"), anyInt())).thenReturn(List.of(brief("me02-125", "125")));
        when(tcgdex.fetchCardStrict("me02-125")).thenReturn(Optional.of(full("me02-125", "Phantasmal Flames", "125",
                Map.of("holofoil", new BigDecimal("661.14")))));

        BuylistDraftLine result = service.resolve(line("Mega Charizard X-EX", null, "125", null));

        assertThat(result.getCardId()).isEqualTo("me02-125");
        assertThat(result.getUnitMarketUsd()).isEqualByComparingTo("661.14");
    }
}
