package com.tailorcards.api.mapper;

import com.tailorcards.api.dto.ProductRequest;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductMapperTest {

    private ProductMapper productMapper;

    @BeforeEach
    void setUp() {
        CategoryMapper categoryMapper = new CategoryMapper();
        productMapper = new ProductMapper(categoryMapper);
    }

    @Test
    void toResponse_shouldMapAllFieldsIncludingCardMetadataAndStatus() {
        Category category = Category.builder()
                .id(1L)
                .name("Singles")
                .description("Individual trading cards")
                .build();

        Product product = Product.builder()
                .id(10L)
                .name("Charizard Holographic (Base Set)")
                .description("Classic holographic Charizard card")
                .price(BigDecimal.valueOf(349.99))
                .imageUrl("https://example.com/charizard.jpg")
                .stock(3)
                .category(category)
                .cardNumber("4/102")
                .set("Base Set")
                .condition("Near Mint")
                .grading("PSA 10")
                .status("AVAILABLE")
                .build();

        ProductResponse response = productMapper.toResponse(product);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Charizard Holographic (Base Set)", response.name());
        assertEquals("Classic holographic Charizard card", response.description());
        assertEquals(BigDecimal.valueOf(349.99), response.price());
        assertEquals("https://example.com/charizard.jpg", response.imageUrl());
        assertEquals(3, response.stock());
        assertNotNull(response.category());
        assertEquals(1L, response.category().id());
        assertEquals("Singles", response.category().name());
        assertEquals("4/102", response.cardNumber());
        assertEquals("Base Set", response.set());
        assertEquals("Near Mint", response.condition());
        assertEquals("PSA 10", response.grading());
        assertEquals("AVAILABLE", response.status());
    }

    @Test
    void toEntity_shouldMapAllFieldsAndDefaultStatusWhenOmitted() {
        Category category = Category.builder()
                .id(1L)
                .name("Singles")
                .build();

        ProductRequest request = new ProductRequest(
                "Black Lotus Art Commemorative",
                "Vintage-style commemorative art card",
                BigDecimal.valueOf(89.50),
                "https://example.com/black-lotus.jpg",
                5,
                1L,
                "PR-01",
                "Vintage Masters",
                "Near Mint",
                "Ungraded",
                null // status omitted
        );

        Product product = productMapper.toEntity(request, category);

        assertNotNull(product);
        assertEquals("Black Lotus Art Commemorative", product.getName());
        assertEquals("PR-01", product.getCardNumber());
        assertEquals("Vintage Masters", product.getSet());
        assertEquals("Near Mint", product.getCondition());
        assertEquals("Ungraded", product.getGrading());
        assertEquals("AVAILABLE", product.getStatus());
        assertEquals(category, product.getCategory());
    }

    @Test
    void toEntity_shouldMapExplicitStatusWhenProvided() {
        Category category = Category.builder()
                .id(1L)
                .name("Singles")
                .build();

        ProductRequest request = new ProductRequest(
                "1986 Basketball Legend",
                "Vintage card",
                BigDecimal.valueOf(599.00),
                "https://example.com/legend.jpg",
                0,
                1L,
                "57",
                "1986 Fleer",
                "Gem Mint",
                "BGS 9.5",
                "SOLD"
        );

        Product product = productMapper.toEntity(request, category);

        assertNotNull(product);
        assertEquals("SOLD", product.getStatus());
    }

    @Test
    void updateEntity_shouldUpdateCardMetadataAndStatus() {
        Category category = Category.builder()
                .id(1L)
                .name("Singles")
                .build();

        Product product = Product.builder()
                .id(1L)
                .name("Original Name")
                .cardNumber("1/100")
                .set("Old Set")
                .condition("Good")
                .grading("Raw")
                .status("AVAILABLE")
                .build();

        ProductRequest updateRequest = new ProductRequest(
                "Updated Name",
                "Updated Description",
                BigDecimal.valueOf(199.99),
                "https://example.com/new.jpg",
                2,
                1L,
                "2/100",
                "New Set",
                "Mint",
                "PSA 9",
                "SOLD"
        );

        productMapper.updateEntity(product, updateRequest, category);

        assertEquals("Updated Name", product.getName());
        assertEquals("2/100", product.getCardNumber());
        assertEquals("New Set", product.getSet());
        assertEquals("Mint", product.getCondition());
        assertEquals("PSA 9", product.getGrading());
        assertEquals("SOLD", product.getStatus());
    }
}
