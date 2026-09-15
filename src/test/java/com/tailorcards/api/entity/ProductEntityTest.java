package com.tailorcards.api.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductEntityTest {

    @Test
    void defaultStatus_shouldBeAvailable() {
        Product product = Product.builder()
                .name("Charizard")
                .build();

        assertEquals("AVAILABLE", product.getStatus());
    }

    @Test
    void noArgsConstructor_shouldDefaultStatusToAvailable() {
        Product product = new Product();
        assertEquals("AVAILABLE", product.getStatus());
    }

    @Test
    void validateStatus_shouldAllowAvailableAndSold() {
        Product product = new Product();
        product.setStatus("AVAILABLE");
        assertDoesNotThrow(product::validateStatus);

        product.setStatus("SOLD");
        assertDoesNotThrow(product::validateStatus);
    }

    @Test
    void setStatus_shouldThrowExceptionForInvalidStatus() {
        Product product = new Product();
        assertThrows(IllegalArgumentException.class, () -> product.setStatus("INVALID_STATUS"));
    }

    @Test
    void validateStatus_shouldDefaultNullOrBlankToAvailable() {
        Product product = Product.builder()
                .name("Charizard")
                .status(null)
                .build();

        product.validateStatus();
        assertEquals("AVAILABLE", product.getStatus());
    }
}
