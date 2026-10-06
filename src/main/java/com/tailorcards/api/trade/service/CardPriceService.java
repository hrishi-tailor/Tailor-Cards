package com.tailorcards.api.trade.service;

import com.tailorcards.api.entity.ManualPriceOverride;
import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.repository.ManualPriceOverrideRepository;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardPriceService {

    private final PriceProvider priceProvider;
    private final ExchangeRateService exchangeRateService;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final ManualPriceOverrideRepository manualPriceOverrideRepository;

    /**
     * Resolves the market price in CAD for a customer or store card.
     * Graded (PSA 10, BGS BL) and sealed items must come from manual_price_overrides.
     * If no price exists, returns Optional.empty() so the pricing engine flags NEEDS_REVIEW.
     */
    @Transactional
    public Optional<BigDecimal> resolvePriceCad(String cardId, String condition, String grading, Boolean sealed) {
        if (cardId == null || cardId.isBlank()) {
            return Optional.empty();
        }

        // 1. Sealed product: must have a manual price override
        if (Boolean.TRUE.equals(sealed)) {
            Optional<ManualPriceOverride> sealedOverride = manualPriceOverrideRepository
                    .findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(cardId, "SEALED");
            if (sealedOverride.isPresent()) {
                return Optional.of(sealedOverride.get().getOverridePriceCad());
            }
            log.info("No manual price override found for sealed cardId={}. Yielding empty (NEEDS_REVIEW).", cardId);
            return Optional.empty();
        }

        // 2. Graded cards (PSA 10, BGS BL, etc.): must have a manual price override
        if (grading != null && !grading.isBlank() && !"RAW".equalsIgnoreCase(grading.trim())) {
            String cleanGrade = grading.trim().toUpperCase();

            // Direct check on exact grade string
            Optional<ManualPriceOverride> gradedOverride = manualPriceOverrideRepository
                    .findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(cardId, cleanGrade);
            if (gradedOverride.isPresent()) {
                return Optional.of(gradedOverride.get().getOverridePriceCad());
            }

            // Normalization checks
            if (cleanGrade.contains("PSA") && (cleanGrade.contains("10") || cleanGrade.contains("TEN"))) {
                Optional<ManualPriceOverride> psa10Override = manualPriceOverrideRepository
                        .findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(cardId, "PSA 10");
                if (psa10Override.isPresent()) {
                    return Optional.of(psa10Override.get().getOverridePriceCad());
                }
            } else if (cleanGrade.contains("BGS") && (cleanGrade.contains("BLACK") || cleanGrade.contains("BL"))) {
                Optional<ManualPriceOverride> bgsOverride = manualPriceOverrideRepository
                        .findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(cardId, "BGS BL");
                if (bgsOverride.isPresent()) {
                    return Optional.of(bgsOverride.get().getOverridePriceCad());
                }
            }

            log.info("No manual price override found for graded cardId={}, grading={}. Yielding empty (NEEDS_REVIEW).",
                    cardId, grading);
            return Optional.empty();
        }

        // 3. Raw card: check manual_price_overrides first (admin override takes precedence)
        if (condition != null && !condition.isBlank()) {
            Optional<ManualPriceOverride> condOverride = manualPriceOverrideRepository
                    .findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(cardId, condition.trim().toUpperCase());
            if (condOverride.isPresent()) {
                return Optional.of(condOverride.get().getOverridePriceCad());
            }
        }

        // 4. Raw card: check cached database price snapshot (avoids hitting external API on page loads)
        Optional<PriceSnapshot> cachedSnapshot = priceSnapshotRepository
                .findTopByCardIdOrderByFetchedAtDesc(cardId);
        if (cachedSnapshot.isPresent() && cachedSnapshot.get().getPriceCad() != null) {
            return Optional.of(cachedSnapshot.get().getPriceCad());
        }

        // 5. If missing from cache, fetch from provider, convert to CAD, and save cache snapshot
        return fetchConvertAndSnapshot(cardId);
    }

    /**
     * Fetches raw market price from provider in USD, converts to CAD, and caches in price_snapshots.
     */
    @Transactional
    public Optional<BigDecimal> fetchConvertAndSnapshot(String cardId) {
        Optional<CardMarketPrice> providerCard = priceProvider.fetchPrice(cardId);
        if (providerCard.isEmpty() || providerCard.get().marketPriceUsd() == null) {
            log.info("No market price available from provider for cardId={}", cardId);
            return Optional.empty();
        }

        BigDecimal priceUsd = providerCard.get().marketPriceUsd();
        BigDecimal priceCad = exchangeRateService.convertUsdToCad(priceUsd);

        PriceSnapshot snapshot = PriceSnapshot.builder()
                .cardId(cardId)
                .priceUsd(priceUsd)
                .priceCad(priceCad)
                .source(priceProvider.getProviderName())
                .fetchedAt(Instant.now())
                .build();

        priceSnapshotRepository.save(snapshot);
        log.info("Saved price snapshot for cardId={}: USD={}, CAD={}", cardId, priceUsd, priceCad);
        return Optional.ofNullable(priceCad);
    }

    @Transactional(readOnly = true)
    public Optional<PriceSnapshot> getLatestSnapshot(String cardId) {
        return priceSnapshotRepository.findTopByCardIdOrderByFetchedAtDesc(cardId);
    }

    @Transactional(readOnly = true)
    public List<PriceSnapshot> getSnapshotHistory(String cardId) {
        return priceSnapshotRepository.findByCardIdOrderByFetchedAtDesc(cardId);
    }
}
