package com.tailorcards.api.service;

import com.tailorcards.api.dto.PriceHistoryResponse;
import com.tailorcards.api.dto.PricePointResponse;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
}
