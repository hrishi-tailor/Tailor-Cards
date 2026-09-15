package com.tailorcards.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProductRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRequest_withNullStatus_shouldPassValidation() {
        ProductRequest request = new ProductRequest(
                "Charizard",
                "Description",
                BigDecimal.valueOf(100.00),
                "http://example.com/img.png",
                5,
                1L,
                "001",
                "Base Set",
                "Near Mint",
                "Raw",
                null
        );

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Expected no violations when status is null");
    }

    @Test
    void validRequest_withAvailableStatus_shouldPassValidation() {
        ProductRequest request = new ProductRequest(
                "Charizard",
                "Description",
                BigDecimal.valueOf(100.00),
                "http://example.com/img.png",
                5,
                1L,
                "001",
                "Base Set",
                "Near Mint",
                "Raw",
                "AVAILABLE"
        );

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void validRequest_withSoldStatus_shouldPassValidation() {
        ProductRequest request = new ProductRequest(
                "Charizard",
                "Description",
                BigDecimal.valueOf(100.00),
                "http://example.com/img.png",
                0,
                1L,
                "001",
                "Base Set",
                "Near Mint",
                "PSA 10",
                "SOLD"
        );

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void invalidRequest_withInvalidStatus_shouldFailValidation() {
        ProductRequest request = new ProductRequest(
                "Charizard",
                "Description",
                BigDecimal.valueOf(100.00),
                "http://example.com/img.png",
                5,
                1L,
                "001",
                "Base Set",
                "Near Mint",
                "Raw",
                "PENDING"
        );

        Set<ConstraintViolation<ProductRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("Status must be either AVAILABLE or SOLD", violations.iterator().next().getMessage());
    }
}
