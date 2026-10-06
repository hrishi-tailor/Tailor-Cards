package com.tailorcards.api.controller;

import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PriceHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
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
                .set("Shining Legends")
                .cardNumber("78/73")
                .condition("Near Mint")
                .grading("PSA 10 GEM MT")
                .status("AVAILABLE")
                .build();
        testProduct = productRepository.save(product);
    }

    @Test
    void getPriceHistory_default3M_shouldReturn200And90Points() throws Exception {
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/price-history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId", is(testProduct.getId().intValue())))
                .andExpect(jsonPath("$.productName", is("Mewtwo GX - Secret Rare")))
                .andExpect(jsonPath("$.cardSet", is("Shining Legends")))
                .andExpect(jsonPath("$.cardNumber", is("78/73")))
                .andExpect(jsonPath("$.condition", is("Near Mint")))
                .andExpect(jsonPath("$.grading", is("PSA 10 GEM MT")))
                .andExpect(jsonPath("$.range", is("3M")))
                .andExpect(jsonPath("$.currency", is("CAD")))
                .andExpect(jsonPath("$.currentPrice", is(125.00)))
                .andExpect(jsonPath("$.periodHigh", notNullValue()))
                .andExpect(jsonPath("$.periodLow", notNullValue()))
                .andExpect(jsonPath("$.changeAmount", notNullValue()))
                .andExpect(jsonPath("$.changePercentage", notNullValue()))
                .andExpect(jsonPath("$.history", hasSize(90)))
                .andExpect(jsonPath("$.history[89].price", is(125.00)))
                .andExpect(jsonPath("$.history[89].volume", greaterThanOrEqualTo(3)));
    }

    @Test
    void getPriceHistory_range1M_shouldReturn30Points() throws Exception {
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/price-history?range=1M")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range", is("1M")))
                .andExpect(jsonPath("$.history", hasSize(30)))
                .andExpect(jsonPath("$.history[29].price", is(125.00)));
    }

    @Test
    void getPriceHistory_range1Y_shouldReturn52Points() throws Exception {
        mockMvc.perform(get("/api/products/" + testProduct.getId() + "/price-history?range=1Y")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range", is("1Y")))
                .andExpect(jsonPath("$.history", hasSize(52)))
                .andExpect(jsonPath("$.history[51].price", is(125.00)));
    }

    @Test
    void getPriceHistory_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/products/999999/price-history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
