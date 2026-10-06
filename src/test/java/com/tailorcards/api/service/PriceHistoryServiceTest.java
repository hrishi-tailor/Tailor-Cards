package com.tailorcards.api.service;

import com.tailorcards.api.dto.PriceHistoryResponse;
import com.tailorcards.api.dto.PricePointResponse;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    private PriceHistoryService priceHistoryService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        priceHistoryService = new PriceHistoryService(productRepository);

        sampleProduct = Product.builder()
                .id(16L)
                .name("Mewtwo GX - Full Art")
                .price(BigDecimal.valueOf(85.50))
                .set("Shining Legends")
                .cardNumber("78/73")
                .condition("Near Mint")
                .grading("PSA 10 GEM MT")
                .status("AVAILABLE")
                .stock(1)
                .build();
    }

    @Test
    void getPriceHistory_defaultRange3M_generates90DataPoints() {
        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));

        PriceHistoryResponse response = priceHistoryService.getPriceHistory(16L, null);

        assertNotNull(response);
        assertEquals(16L, response.productId());
        assertEquals("Mewtwo GX - Full Art", response.productName());
        assertEquals("3M", response.range());
        assertEquals(BigDecimal.valueOf(85.50).setScale(2, RoundingMode.HALF_UP), response.currentPrice());
        assertEquals(90, response.history().size());

        // Final point must equal current catalog listing price
        PricePointResponse latest = response.history().get(response.history().size() - 1);
        assertEquals(BigDecimal.valueOf(85.50).setScale(2, RoundingMode.HALF_UP), latest.price());

        assertTrue(response.periodHigh().compareTo(response.periodLow()) >= 0);
        assertNotNull(response.changeAmount());
        assertNotNull(response.changePercentage());
    }

    @Test
    void getPriceHistory_range1M_generates30DataPoints() {
        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));

        PriceHistoryResponse response = priceHistoryService.getPriceHistory(16L, "1M");

        assertNotNull(response);
        assertEquals("1M", response.range());
        assertEquals(30, response.history().size());

        PricePointResponse latest = response.history().get(response.history().size() - 1);
        assertEquals(BigDecimal.valueOf(85.50).setScale(2, RoundingMode.HALF_UP), latest.price());
    }

    @Test
    void getPriceHistory_range1Y_generates52WeeklyDataPoints() {
        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));

        PriceHistoryResponse response = priceHistoryService.getPriceHistory(16L, "1Y");

        assertNotNull(response);
        assertEquals("1Y", response.range());
        assertEquals(52, response.history().size());

        PricePointResponse latest = response.history().get(response.history().size() - 1);
        assertEquals(BigDecimal.valueOf(85.50).setScale(2, RoundingMode.HALF_UP), latest.price());
    }

    @Test
    void getPriceHistory_invalidRange_fallsBackTo3M() {
        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));

        PriceHistoryResponse response = priceHistoryService.getPriceHistory(16L, "invalid-range");

        assertNotNull(response);
        assertEquals("3M", response.range());
        assertEquals(90, response.history().size());
    }

    @Test
    void getPriceHistory_deterministicConsistency_returnsIdenticalValues() {
        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));

        PriceHistoryResponse first = priceHistoryService.getPriceHistory(16L, "3M");
        PriceHistoryResponse second = priceHistoryService.getPriceHistory(16L, "3M");

        assertEquals(first.periodLow(), second.periodLow());
        assertEquals(first.periodHigh(), second.periodHigh());
        assertEquals(first.changeAmount(), second.changeAmount());
        assertEquals(first.changePercentage(), second.changePercentage());
        assertEquals(first.history().size(), second.history().size());

        for (int i = 0; i < first.history().size(); i++) {
            assertEquals(first.history().get(i).price(), second.history().get(i).price());
            assertEquals(first.history().get(i).date(), second.history().get(i).date());
            assertEquals(first.history().get(i).volume(), second.history().get(i).volume());
        }
    }

    @Test
    void getPriceHistory_productNotFound_throwsResourceNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> priceHistoryService.getPriceHistory(999L, "3M"));
    }

    @Test
    void getPriceHistory_realSnapshotsFewerThan7_showsTrackingStartedAndAllSnapshots() {
        PriceSnapshotRepository snapshotRepo = org.mockito.Mockito.mock(PriceSnapshotRepository.class);
        sampleProduct.setPokemontcgId("swsh4-25");

        java.time.Instant day1 = java.time.Instant.parse("2026-03-01T10:00:00Z");
        java.time.Instant day2 = java.time.Instant.parse("2026-03-02T10:00:00Z");
        java.time.Instant day3 = java.time.Instant.parse("2026-03-03T10:00:00Z");

        List<com.tailorcards.api.entity.PriceSnapshot> snapshots = List.of(
                com.tailorcards.api.entity.PriceSnapshot.builder()
                        .cardId("swsh4-25")
                        .priceCad(new BigDecimal("82.00"))
                        .source("pokemontcg.io")
                        .fetchedAt(day1)
                        .build(),
                com.tailorcards.api.entity.PriceSnapshot.builder()
                        .cardId("swsh4-25")
                        .priceCad(new BigDecimal("84.50"))
                        .source("pokemontcg.io")
                        .fetchedAt(day2)
                        .build(),
                com.tailorcards.api.entity.PriceSnapshot.builder()
                        .cardId("swsh4-25")
                        .priceCad(new BigDecimal("85.50"))
                        .source("pokemontcg.io")
                        .fetchedAt(day3)
                        .build()
        );

        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));
        when(snapshotRepo.findByCardIdOrderByFetchedAtAsc("swsh4-25")).thenReturn(snapshots);

        PriceHistoryService realService = new PriceHistoryService(productRepository, snapshotRepo, false);
        PriceHistoryResponse response = realService.getPriceHistory(16L, "3M");

        assertNotNull(response);
        assertFalse(response.isSampleData());
        assertEquals(PriceHistoryService.REAL_SOURCE_LABEL, response.sourceLabel());
        assertEquals("Tracking started 2026-03-01", response.trackingStartDate());
        assertEquals(3, response.history().size());
        assertEquals(new BigDecimal("82.00"), response.periodLow());
        assertEquals(new BigDecimal("85.50"), response.periodHigh());
    }

    @Test
    void getPriceHistory_realSnapshots7OrMore_noTrackingStartedNotice() {
        PriceSnapshotRepository snapshotRepo = org.mockito.Mockito.mock(PriceSnapshotRepository.class);
        sampleProduct.setPokemontcgId("swsh4-25");

        java.time.Instant now = java.time.Instant.now();
        List<com.tailorcards.api.entity.PriceSnapshot> snapshots = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            snapshots.add(com.tailorcards.api.entity.PriceSnapshot.builder()
                    .cardId("swsh4-25")
                    .priceCad(new BigDecimal("80.00").add(BigDecimal.valueOf(i)))
                    .source("pokemontcg.io")
                    .fetchedAt(now.minus(10 - i, java.time.temporal.ChronoUnit.DAYS))
                    .build());
        }

        when(productRepository.findById(16L)).thenReturn(Optional.of(sampleProduct));
        when(snapshotRepo.findByCardIdOrderByFetchedAtAsc("swsh4-25")).thenReturn(snapshots);

        PriceHistoryService realService = new PriceHistoryService(productRepository, snapshotRepo, false);
        PriceHistoryResponse response = realService.getPriceHistory(16L, "3M");

        assertNotNull(response);
        assertFalse(response.isSampleData());
        assertNull(response.trackingStartDate());
        assertEquals(10, response.history().size());
    }
}
