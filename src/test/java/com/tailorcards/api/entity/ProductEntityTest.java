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

    @Test
    void decrementStock_success_updatesStockAndStatusWhenZero() {
        Product product = Product.builder()
                .name("Charizard")
                .stock(3)
                .status("AVAILABLE")
                .build();

        product.decrementStock(2);
        assertEquals(1, product.getStock());
        assertEquals("AVAILABLE", product.getStatus());

        product.decrementStock(1);
        assertEquals(0, product.getStock());
        assertEquals("SOLD", product.getStatus());
    }

    @Test
    void decrementStock_invalidQuantity_throwsException() {
        Product product = Product.builder()
                .name("Charizard")
                .stock(5)
                .build();

        assertThrows(IllegalArgumentException.class, () -> product.decrementStock(0));
        assertThrows(IllegalArgumentException.class, () -> product.decrementStock(-1));
    }

    @Test
    void decrementStock_exceedingStock_throwsException() {
        Product product = Product.builder()
                .name("Charizard")
                .stock(2)
                .build();

        assertThrows(IllegalArgumentException.class, () -> product.decrementStock(3));
    }
}
