package com.tailorcards.api.trade.service;

import com.tailorcards.api.entity.ManualPriceOverride;
import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.repository.ManualPriceOverrideRepository;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardPriceService Tests")
class CardPriceServiceTest {

    @Mock
    private PriceProvider priceProvider;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private PriceSnapshotRepository priceSnapshotRepository;

    @Mock
    private ManualPriceOverrideRepository manualPriceOverrideRepository;

    private CardPriceService cardPriceService;

    @BeforeEach
    void setUp() {
        cardPriceService = new CardPriceService(
                priceProvider,
                exchangeRateService,
                priceSnapshotRepository,
                manualPriceOverrideRepository
        );
    }

    @Test
    @DisplayName("Graded PSA 10 uses manual price override when present")
    void testGradedCardWithOverride() {
        ManualPriceOverride override = ManualPriceOverride.builder()
                .cardId("base1-4")
                .conditionOrGrade("PSA 10")
                .overridePriceCad(new BigDecimal("4500.00"))
                .build();

        when(manualPriceOverrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
                eq("base1-4"), eq("PSA 10")))
                .thenReturn(Optional.of(override));

        Optional<BigDecimal> price = cardPriceService.resolvePriceCad("base1-4", "NM", "PSA 10", false);

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo(new BigDecimal("4500.00"));
        verify(priceProvider, never()).fetchPrice(any());
        verify(priceSnapshotRepository, never()).findTopByCardIdOrderByFetchedAtDesc(any());
    }

    @Test
    @DisplayName("Graded PSA 10 returns empty (NEEDS_REVIEW) when no manual override exists")
    void testGradedCardWithoutOverrideReturnsEmpty() {
        when(manualPriceOverrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
                eq("base1-4"), any()))
                .thenReturn(Optional.empty());

        Optional<BigDecimal> price = cardPriceService.resolvePriceCad("base1-4", "NM", "PSA 10", false);

        assertThat(price).isEmpty();
        verify(priceProvider, never()).fetchPrice(any());
    }

    @Test
    @DisplayName("Sealed product uses manual price override when present")
    void testSealedProductWithOverride() {
        ManualPriceOverride override = ManualPriceOverride.builder()
                .cardId("sv3pt5-151")
                .conditionOrGrade("SEALED")
                .overridePriceCad(new BigDecimal("180.00"))
                .build();

        when(manualPriceOverrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
                eq("sv3pt5-151"), eq("SEALED")))
                .thenReturn(Optional.of(override));

        Optional<BigDecimal> price = cardPriceService.resolvePriceCad("sv3pt5-151", null, null, true);

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo(new BigDecimal("180.00"));
        verify(priceProvider, never()).fetchPrice(any());
    }

    @Test
    @DisplayName("Sealed product returns empty (NEEDS_REVIEW) when no manual override exists")
    void testSealedProductWithoutOverrideReturnsEmpty() {
        when(manualPriceOverrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
                eq("sv3pt5-151"), eq("SEALED")))
                .thenReturn(Optional.empty());

        Optional<BigDecimal> price = cardPriceService.resolvePriceCad("sv3pt5-151", null, null, true);

        assertThat(price).isEmpty();
        verify(priceProvider, never()).fetchPrice(any());
    }

    @Test
    @DisplayName("Raw card uses cached database price snapshot without hitting external API")
    void testRawCardUsesCachedSnapshot() {
        PriceSnapshot cachedSnapshot = PriceSnapshot.builder()
                .cardId("base1-4")
                .priceUsd(new BigDecimal("280.00"))
                .priceCad(new BigDecimal("386.40"))
                .source("POKEMONTCG_IO")
                .fetchedAt(Instant.now())
                .build();

        when(manualPriceOverrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
                eq("base1-4"), eq("NEAR_MINT")))
                .thenReturn(Optional.empty());

        when(priceSnapshotRepository.findTopByCardIdOrderByFetchedAtDesc("base1-4"))
                .thenReturn(Optional.of(cachedSnapshot));

        Optional<BigDecimal> price = cardPriceService.resolvePriceCad("base1-4", "NEAR_MINT", "RAW", false);

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo(new BigDecimal("386.40"));
        verify(priceProvider, never()).fetchPrice(any());
    }

    @Test
    @DisplayName("Raw card without cached snapshot fetches from provider, converts to CAD, and saves snapshot")
    void testRawCardFetchesAndSnapshotsWhenCacheMiss() {
        when(manualPriceOverrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
                eq("base1-2"), eq("NEAR_MINT")))
                .thenReturn(Optional.empty());

        when(priceSnapshotRepository.findTopByCardIdOrderByFetchedAtDesc("base1-2"))
                .thenReturn(Optional.empty());

        CardMarketPrice providerCard = CardMarketPrice.builder()
                .cardId("base1-2")
                .name("Blastoise")
                .marketPriceUsd(new BigDecimal("100.00"))
                .source("POKEMONTCG_IO")
                .build();

        when(priceProvider.fetchPrice("base1-2")).thenReturn(Optional.of(providerCard));
        when(priceProvider.getProviderName()).thenReturn("POKEMONTCG_IO");
        when(exchangeRateService.convertUsdToCad(new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("138.00"));

        Optional<BigDecimal> price = cardPriceService.resolvePriceCad("base1-2", "NEAR_MINT", "RAW", false);

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo(new BigDecimal("138.00"));

        ArgumentCaptor<PriceSnapshot> snapshotCaptor = ArgumentCaptor.forClass(PriceSnapshot.class);
        verify(priceSnapshotRepository).save(snapshotCaptor.capture());
        PriceSnapshot savedSnapshot = snapshotCaptor.getValue();
        assertThat(savedSnapshot.getCardId()).isEqualTo("base1-2");
        assertThat(savedSnapshot.getPriceUsd()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(savedSnapshot.getPriceCad()).isEqualByComparingTo(new BigDecimal("138.00"));
    }
}
