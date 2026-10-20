package com.tailorcards.api.pipeline;

import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.ledger.LedgerLotRepository;
import com.tailorcards.api.pipeline.dto.IngestResult;
import com.tailorcards.api.pipeline.dto.PriceBatch;
import com.tailorcards.api.pipeline.dto.Watchlist;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.service.ExchangeRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The app's side of the AWS price pipeline: tells it which cards to price and records the prices it sends back as
 * {@link PriceSnapshot}s, converted to CAD at the app's exchange rate, exactly as the app's own snapshot job does.
 */
@Service
public class PipelinePricingService {

    /** A price stamped further ahead than this is refused: the pipeline's clock or payload is wrong. */
    static final Duration MAX_FUTURE = Duration.ofMinutes(5);

    private final ProductRepository products;
    private final LedgerLotRepository lots;
    private final PriceSnapshotRepository snapshots;
    private final ExchangeRateService exchangeRates;
    private final Clock clock;

    @Autowired
    public PipelinePricingService(ProductRepository products, LedgerLotRepository lots,
                                  PriceSnapshotRepository snapshots, ExchangeRateService exchangeRates) {
        this(products, lots, snapshots, exchangeRates, Clock.systemUTC());
    }

    PipelinePricingService(ProductRepository products, LedgerLotRepository lots, PriceSnapshotRepository snapshots,
                           ExchangeRateService exchangeRates, Clock clock) {
        this.products = products;
        this.lots = lots;
        this.snapshots = snapshots;
        this.exchangeRates = exchangeRates;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Watchlist watchlist() {
        TreeSet<String> ids = new TreeSet<>();
        Stream.concat(products.findByPokemontcgIdIsNotNull().stream().map(Product::getPokemontcgId),
                        lots.cardRefsInStock().stream())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .forEach(ids::add);
        return new Watchlist(List.copyOf(ids), clock.instant());
    }

    /**
     * Saves each price once. Fetch times are kept to the second, so a retried run with the same payload finds its
     * earlier rows and reports them as duplicates instead of saving them again.
     */
    @Transactional
    public IngestResult record(PriceBatch batch) {
        Instant latestAllowed = clock.instant().plus(MAX_FUTURE);
        BigDecimal rate = exchangeRates.getUsdToCadRate();
        List<PriceSnapshot> toSave = new ArrayList<>();
        int duplicates = 0;
        for (PriceBatch.Price price : batch.prices()) {
            Instant fetchedAt = price.fetchedAt().truncatedTo(ChronoUnit.SECONDS);
            if (fetchedAt.isAfter(latestAllowed)) {
                throw new IllegalArgumentException("Price for " + price.cardId().trim() + " is dated in the future");
            }
            String cardId = price.cardId().trim();
            boolean seen = toSave.stream().anyMatch(s -> s.getCardId().equals(cardId) && s.getFetchedAt().equals(fetchedAt))
                    || snapshots.existsByCardIdAndSourceAndFetchedAt(cardId, batch.source(), fetchedAt);
            if (seen) {
                duplicates++;
                continue;
            }
            BigDecimal usd = price.priceUsd().setScale(2, RoundingMode.HALF_UP);
            toSave.add(PriceSnapshot.builder()
                    .cardId(cardId)
                    .priceUsd(usd)
                    .priceCad(usd.multiply(rate).setScale(2, RoundingMode.HALF_UP))
                    .source(batch.source())
                    .fetchedAt(fetchedAt)
                    .build());
        }
        snapshots.saveAll(toSave);
        return new IngestResult(batch.runId(), toSave.size(), duplicates);
    }
}
