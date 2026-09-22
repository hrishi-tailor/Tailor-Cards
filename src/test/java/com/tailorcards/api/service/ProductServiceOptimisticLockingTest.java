package com.tailorcards.api.service;

import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductServiceOptimisticLockingTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductService productService;

    @Test
    void concurrentStockDecrement_throwsOptimisticLockingException() {
        Category category = categoryRepository.save(
                Category.builder().name("Test Category").description("Testing").build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("Charizard EX")
                        .price(new BigDecimal("100.00"))
                        .stock(10)
                        .category(category)
                        .status("AVAILABLE")
                        .build()
        );

        Long productId = product.getId();
        assertNotNull(product.getVersion(), "Version should be initialized by JPA");

        // Simulate two separate transactions reading the same entity version
        Product tx1Product = productRepository.findById(productId).orElseThrow();
        Product tx2Product = productRepository.findById(productId).orElseThrow();

        assertEquals(tx1Product.getVersion(), tx2Product.getVersion());

        // Transaction 1 decrements and saves
        tx1Product.decrementStock(2);
        productRepository.saveAndFlush(tx1Product);

        // Transaction 2 tries to decrement and save its stale copy
        tx2Product.decrementStock(1);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            productRepository.saveAndFlush(tx2Product);
        });
    }

    @Test
    void decrementStock_throughService_updatesStockAndVersion() {
        Category category = categoryRepository.save(
                Category.builder().name("Single Cards").description("Singles").build()
        );

        Product product = productRepository.save(
                Product.builder()
                        .name("Mewtwo GX")
                        .price(new BigDecimal("50.00"))
                        .stock(2)
                        .category(category)
                        .status("AVAILABLE")
                        .build()
        );

        Long initialVersion = product.getVersion();

        var response = productService.decrementStock(product.getId(), 2);
        assertEquals(0, response.stock());
        assertEquals("SOLD", response.status());

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, reloaded.getStock());
        assertEquals("SOLD", reloaded.getStatus());
        assertTrue(reloaded.getVersion() > initialVersion, "Version must increment after update");
    }
}
