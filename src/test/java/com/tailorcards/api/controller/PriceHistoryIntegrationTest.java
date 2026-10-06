package com.tailorcards.api.controller;

import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.service.PriceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Price History Real Snapshot Integration Tests")
class PriceHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PriceSnapshotRepository priceSnapshotRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        priceSnapshotRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = Category.builder()
                .name("Singles")
                .description("Authentic Pokémon singles")
                .build();
        category = categoryRepository.save(category);

        Product product = Product.builder()
                .name("Mewtwo GX - Secret Rare")
                .description("Shining Legends Full Art Mewtwo GX")
                .price(BigDecimal.valueOf(125.00))
                .stock(1)
                .category(category)
                .pokemontcgId("sm35-78")
                .set("Shining Legends")
                .cardNumber("78/73")
                .condition("Near Mint")
                .grading("PSA 10 GEM MT")
                .status("AVAILABLE")
                .build();
        testProduct = productRepository.save(product);
    }

    @Test
    @DisplayName("Fewer than 7 snapshots shows Tracking started notice and available points")
    void getPriceHistory_fewerThan7Snapshots_shouldShowTrackingStarted() throws Exception {
        Instant now = Instant.now();
        priceSnapshotRepository.save(PriceSnapshot.builder()
                .cardId("sm35-78")
                .priceCad(BigDecimal.valueOf(120.00))
                .source("pokemontcg.io")
                .fetchedAt(now.minus(3, ChronoUnit.DAYS))
                .build());
        priceSnapshotRepository.save(PriceSnapshot.builder()
                .cardId("sm35-78")
                .priceCad(BigDecimal.valueOf(122.50))
                .source("pokemontcg.io")
                .fetchedAt(now.minus(2, ChronoUnit.DAYS))
                .build());
        priceSnapshotRepository.save(PriceSnapshot.builder()
                .cardId("sm35-78")
                .priceCad(BigDecimal.valueOf(125.00))
                .source("pokemontcg.io")
                .fetchedAt(now.minus(1, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/price-history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId", is(testProduct.getId().intValue())))
                .andExpect(jsonPath("$.productName", is("Mewtwo GX - Secret Rare")))
                .andExpect(jsonPath("$.range", is("3M")))
                .andExpect(jsonPath("$.currency", is("CAD")))
                .andExpect(jsonPath("$.currentPrice", is(125.00)))
                .andExpect(jsonPath("$.sourceLabel", is(PriceHistoryService.REAL_SOURCE_LABEL)))
                .andExpect(jsonPath("$.isSampleData", is(false)))
                .andExpect(jsonPath("$.trackingStartDate", startsWith("Tracking started ")))
                .andExpect(jsonPath("$.history", hasSize(3)))
                .andExpect(jsonPath("$.history[2].price", is(125.00)));
    }

    @Test
    @DisplayName("7 or more snapshots returns full history without tracking started notice")
    void getPriceHistory_7OrMoreSnapshots_noTrackingStartedNotice() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 8; i++) {
            priceSnapshotRepository.save(PriceSnapshot.builder()
                    .cardId("sm35-78")
                    .priceCad(BigDecimal.valueOf(110.00 + i * 2.0))
                    .source("pokemontcg.io")
                    .fetchedAt(now.minus(10 - i, ChronoUnit.DAYS))
                    .build());
        }

        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/price-history?range=1M")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range", is("1M")))
                .andExpect(jsonPath("$.isSampleData", is(false)))
                .andExpect(jsonPath("$.trackingStartDate").doesNotExist())
                .andExpect(jsonPath("$.history", hasSize(8)));
    }

    @Test
    @DisplayName("Returns 404 when product does not exist")
    void getPriceHistory_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/products/999999/price-history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
