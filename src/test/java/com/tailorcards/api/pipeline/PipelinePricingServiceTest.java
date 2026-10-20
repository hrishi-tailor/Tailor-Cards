package com.tailorcards.api.pipeline;

import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.ledger.LedgerLotRepository;
import com.tailorcards.api.pipeline.dto.IngestResult;
import com.tailorcards.api.pipeline.dto.PriceBatch;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@DisplayName("Pipeline pricing")
class PipelinePricingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");

    @Autowired
    private PriceSnapshotRepository snapshots;
    private final ProductRepository products = mock(ProductRepository.class);
    private final LedgerLotRepository lots = mock(LedgerLotRepository.class);
    private PipelinePricingService pricing;

    @BeforeEach
    void setUp() {
        ExchangeRateService rates = mock(ExchangeRateService.class);
        when(rates.getUsdToCadRate()).thenReturn(new BigDecimal("1.3800"));
        pricing = new PipelinePricingService(products, lots, snapshots, rates, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Product product(String cardId) {
        Product product = new Product();
        product.setPokemontcgId(cardId);
        return product;
    }

    private static PriceBatch batch(PriceBatch.Price... prices) {
        return new PriceBatch("run-2026-10-20", "TCGDEX", List.of(prices));
    }

    @Test
    @DisplayName("The watchlist joins site products and ledger stock, trimmed, each id once, sorted")
    void watchlist() {
        when(products.findByPokemontcgIdIsNotNull()).thenReturn(List.of(product(" swsh7-215 "), product("sv8pt5-161"), product("")));
        when(lots.cardRefsInStock()).thenReturn(List.of("sv8pt5-161", "sv3pt5-199"));

        assertThat(pricing.watchlist().cardIds()).containsExactly("sv3pt5-199", "sv8pt5-161", "swsh7-215");
        assertThat(pricing.watchlist().generatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Prices are saved in USD and CAD at the app's rate, under the pipeline's source")
    void recordsPrices() {
        IngestResult result = pricing.record(batch(new PriceBatch.Price("sv8pt5-161", new BigDecimal("101.505"), NOW.minusSeconds(60))));

        assertThat(result.saved()).isEqualTo(1);
        PriceSnapshot saved = snapshots.findTopByCardIdOrderByFetchedAtDesc("sv8pt5-161").orElseThrow();
        assertThat(saved.getPriceUsd()).isEqualByComparingTo("101.51");
        assertThat(saved.getPriceCad()).isEqualByComparingTo("140.08");
        assertThat(saved.getSource()).isEqualTo("TCGDEX");
        assertThat(saved.getFetchedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    @DisplayName("Sending the same run twice saves each price once")
    void idempotent() {
        Instant fetched = NOW.minusSeconds(30).plusMillis(250);
        PriceBatch run = batch(new PriceBatch.Price("sv8pt5-161", new BigDecimal("100"), fetched),
                new PriceBatch.Price("swsh7-215", new BigDecimal("640"), fetched),
                new PriceBatch.Price("swsh7-215", new BigDecimal("640"), fetched));

        assertThat(pricing.record(run)).extracting(IngestResult::saved, IngestResult::duplicates).containsExactly(2, 1);
        assertThat(pricing.record(run)).extracting(IngestResult::saved, IngestResult::duplicates).containsExactly(0, 3);
        assertThat(snapshots.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("A batch with a price dated in the future is refused whole")
    void futurePrice() {
        PriceBatch run = batch(new PriceBatch.Price("sv8pt5-161", new BigDecimal("100"), NOW),
                new PriceBatch.Price("swsh7-215", new BigDecimal("640"), NOW.plusSeconds(600)));

        assertThatThrownBy(() -> pricing.record(run)).isInstanceOf(IllegalArgumentException.class);
        assertThat(snapshots.count()).isZero();
    }
}
