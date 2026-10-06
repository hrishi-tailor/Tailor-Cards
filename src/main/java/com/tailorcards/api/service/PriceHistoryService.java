package com.tailorcards.api.service;

import com.tailorcards.api.dto.PriceHistoryResponse;
import com.tailorcards.api.dto.PricePointResponse;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PriceHistoryService {

    public static final String REAL_SOURCE_LABEL = "TCGplayer market price via pokemontcg.io, converted to CAD";
    public static final String SAMPLE_SOURCE_LABEL = "Sample data (simulated market model)";

    private final ProductRepository productRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final boolean sampleData;

    @Autowired
    public PriceHistoryService(
            ProductRepository productRepository,
            @Autowired(required = false) PriceSnapshotRepository priceSnapshotRepository,
            @Value("${app.price-history.sample-data:${SAMPLE_DATA:false}}") boolean sampleData
    ) {
        this.productRepository = productRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.sampleData = sampleData;
    }

    public PriceHistoryService(ProductRepository productRepository, PriceSnapshotRepository priceSnapshotRepository) {
        this(productRepository, priceSnapshotRepository, false);
    }

    public PriceHistoryService(ProductRepository productRepository) {
        this(productRepository, null, true);
    }

    public PriceHistoryResponse getPriceHistory(Long productId, String range) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        String normalizedRange = (range != null) ? range.trim().toUpperCase() : "3M";
        if (!List.of("1M", "3M", "1Y").contains(normalizedRange)) {
            normalizedRange = "3M";
        }

        BigDecimal currentPrice = product.getPrice() != null && product.getPrice().compareTo(BigDecimal.ZERO) > 0
                ? product.getPrice().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(25.00);

        List<PricePointResponse> history;
        boolean isSynthetic = this.sampleData;
        String sourceLabel = isSynthetic ? SAMPLE_SOURCE_LABEL : REAL_SOURCE_LABEL;
        String trackingStartDate = null;
        int totalSnapshotCount = 0;

        if (isSynthetic) {
            history = generateDeterministicTimeSeries(
                    productId,
                    product.getName(),
                    currentPrice,
                    normalizedRange
            );
            totalSnapshotCount = history.size();
        } else {
            String cardId = product.getPokemontcgId();
            List<PriceSnapshot> allSnapshots = (cardId != null && !cardId.isBlank() && priceSnapshotRepository != null)
                    ? priceSnapshotRepository.findByCardIdOrderByFetchedAtAsc(cardId)
                    : List.of();

            totalSnapshotCount = allSnapshots.size();

            if (totalSnapshotCount < 7) {
                // If fewer than 7 snapshots exist, show "Tracking started [date]" and whatever points exist
                if (totalSnapshotCount == 0) {
                    trackingStartDate = "Tracking started " + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
                    history = List.of(new PricePointResponse(
                            LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE),
                            currentPrice,
                            null
                    ));
                } else {
                    LocalDate firstDate = LocalDate.ofInstant(allSnapshots.get(0).getFetchedAt(), ZoneOffset.UTC);
                    trackingStartDate = "Tracking started " + firstDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    history = allSnapshots.stream()
                            .map(s -> new PricePointResponse(
                                    LocalDate.ofInstant(s.getFetchedAt(), ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                    s.getPriceCad() != null ? s.getPriceCad().setScale(2, RoundingMode.HALF_UP) : currentPrice,
                                    null
                            ))
                            .toList();
                }
            } else {
                // 7 or more snapshots exist: filter by selected timeframe cutoff
                long days = switch (normalizedRange) {
                    case "1M" -> 30;
                    case "1Y" -> 365;
                    default -> 90;
                };
                Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

                List<PriceSnapshot> inRange = allSnapshots.stream()
                        .filter(s -> !s.getFetchedAt().isBefore(cutoff))
                        .toList();

                List<PriceSnapshot> effective = inRange.isEmpty() ? allSnapshots : inRange;

                history = effective.stream()
                        .map(s -> new PricePointResponse(
                                LocalDate.ofInstant(s.getFetchedAt(), ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE),
                                s.getPriceCad() != null ? s.getPriceCad().setScale(2, RoundingMode.HALF_UP) : currentPrice,
                                null
                        ))
                        .toList();
            }
        }

        BigDecimal periodLow = history.stream()
                .map(PricePointResponse::price)
                .min(BigDecimal::compareTo)
                .orElse(currentPrice);

        BigDecimal periodHigh = history.stream()
                .map(PricePointResponse::price)
                .max(BigDecimal::compareTo)
                .orElse(currentPrice);

        BigDecimal startPrice = history.isEmpty() ? currentPrice : history.get(0).price();
        BigDecimal changeAmount = currentPrice.subtract(startPrice).setScale(2, RoundingMode.HALF_UP);

        double changePercentage = 0.0;
        if (startPrice.compareTo(BigDecimal.ZERO) > 0) {
            double rawPct = (changeAmount.doubleValue() / startPrice.doubleValue()) * 100.0;
            changePercentage = Math.round(rawPct * 100.0) / 100.0;
        }

        return new PriceHistoryResponse(
                product.getId(),
                product.getName(),
                product.getSet(),
                product.getCardNumber(),
                product.getCondition(),
                product.getGrading(),
                normalizedRange,
                "CAD",
                currentPrice,
                periodLow,
                periodHigh,
                changeAmount,
                changePercentage,
                sourceLabel,
                isSynthetic,
                trackingStartDate,
                totalSnapshotCount,
                history
        );
    }

    private List<PricePointResponse> generateDeterministicTimeSeries(
            Long productId,
            String productName,
            BigDecimal currentPrice,
            String range
    ) {
        int steps;
        boolean isWeekly = false;
        switch (range) {
            case "1M" -> steps = 30;
            case "1Y" -> {
                steps = 52;
                isWeekly = true;
            }
            case "3M" -> steps = 90;
            default -> steps = 90;
        }

        long seed = (productId * 7919L)
                + (productName != null ? (long) productName.hashCode() * 31L : 0L)
                + ((long) range.hashCode() * 17L);

        double hashNorm = ((seed ^ 0x5DEECE66DL) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;

        double trendPct;
        if ("1M".equals(range)) {
            trendPct = -0.06 + hashNorm * 0.16; // -6% to +10%
        } else if ("1Y".equals(range)) {
            trendPct = -0.15 + hashNorm * 0.45; // -15% to +30%
        } else {
            trendPct = -0.10 + hashNorm * 0.25; // -10% to +15%
        }

        double curPriceVal = currentPrice.doubleValue();
        double startPriceVal = Math.max(1.0, curPriceVal / (1.0 + trendPct));

        List<PricePointResponse> points = new ArrayList<>(steps);
        LocalDate today = LocalDate.now();

        for (int i = 0; i < steps; i++) {
            LocalDate pointDate;
            if (isWeekly) {
                pointDate = today.minusWeeks(steps - 1 - i);
            } else {
                pointDate = today.minusDays(steps - 1 - i);
            }

            BigDecimal price;
            if (i == steps - 1) {
                // Ensure exact match with current catalog listing price at final step
                price = currentPrice;
            } else {
                double f = (double) i / (steps - 1);
                double base = startPriceVal + (curPriceVal - startPriceVal) * f;

                // Two smooth harmonic cycles that converge to zero at the end
                double wave1 = Math.sin(2.0 * Math.PI * f * 2.2) * 0.035 * curPriceVal * (1.0 - f);
                double wave2 = Math.sin(2.0 * Math.PI * f * 4.7 + 0.5) * 0.018 * curPriceVal * (1.0 - f);

                // Deterministic step pseudo-noise
                long stepHash = (seed + (long) i * 1013904223L) ^ 0x5DEECE66DL;
                double noiseNorm = (((stepHash & 0x7FFFFFFF) / (double) 0x7FFFFFFF) - 0.5); // [-0.5, 0.5]
                double stepNoise = noiseNorm * 0.02 * curPriceVal * (1.0 - f);

                double calculated = Math.max(1.0, base + wave1 + wave2 + stepNoise);
                price = BigDecimal.valueOf(calculated).setScale(2, RoundingMode.HALF_UP);
            }

            // Deterministic volume per step
            long volHash = (seed + (long) i * 6364136223846793005L);
            int baseVol = Math.max(3, (int) (14 - (curPriceVal > 150 ? 8 : 0)));
            int volJitter = (int) (Math.abs(volHash % 13));
            int volume = baseVol + volJitter;

            points.add(new PricePointResponse(
                    pointDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    price,
                    volume
            ));
        }

        return points;
    }
}
