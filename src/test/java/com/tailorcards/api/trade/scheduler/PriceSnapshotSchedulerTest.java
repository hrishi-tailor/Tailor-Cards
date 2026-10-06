package com.tailorcards.api.trade.scheduler;

import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.service.CardPriceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceSnapshotScheduler Tests")
class PriceSnapshotSchedulerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CardPriceService cardPriceService;

    @InjectMocks
    private PriceSnapshotScheduler scheduler;

    @Test
    @DisplayName("Syncs distinct card IDs for listed inventory products")
    void testSyncListedCardPrices() {
        Product p1 = Product.builder().id(1L).name("Charizard").pokemontcgId("base1-4").build();
        Product p2 = Product.builder().id(2L).name("Charizard Dup").pokemontcgId("base1-4").build();
        Product p3 = Product.builder().id(3L).name("Blastoise").pokemontcgId("base1-2").build();

        when(productRepository.findByPokemontcgIdIsNotNull()).thenReturn(List.of(p1, p2, p3));
        when(cardPriceService.fetchConvertAndSnapshot("base1-4")).thenReturn(Optional.of(new BigDecimal("350.00")));
        when(cardPriceService.fetchConvertAndSnapshot("base1-2")).thenReturn(Optional.of(new BigDecimal("120.00")));

        int updated = scheduler.syncListedCardPrices();

        assertThat(updated).isEqualTo(2); // 2 distinct cards
        verify(cardPriceService).fetchConvertAndSnapshot("base1-4");
        verify(cardPriceService).fetchConvertAndSnapshot("base1-2");
    }

    @Test
    @DisplayName("Gracefully skips when no products have pokemontcgId")
    void testSyncNoProducts() {
        when(productRepository.findByPokemontcgIdIsNotNull()).thenReturn(List.of());

        int updated = scheduler.syncListedCardPrices();

        assertThat(updated).isEqualTo(0);
    }
}
