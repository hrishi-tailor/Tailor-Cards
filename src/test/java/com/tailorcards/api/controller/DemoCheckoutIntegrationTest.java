package com.tailorcards.api.controller;

import com.stripe.model.checkout.Session;
import com.tailorcards.controller.CheckoutController;
import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Order;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.OrderRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.service.StripeCheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.demo-mode=true",
        "DEMO_MODE=true"
})
@Transactional
@DisplayName("Instant Demo Checkout Security and State Tests")
class DemoCheckoutIntegrationTest {

    @Autowired
    private StripeCheckoutService stripeCheckoutService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.findByName("Singles")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Singles").description("Singles").build()));

        testProduct = productRepository.save(Product.builder()
                .name("Demo Test Card Holo")
                .price(new BigDecimal("150.00"))
                .stock(5)
                .status("AVAILABLE")
                .category(category)
                .build());
    }

    @Test
    @DisplayName("Demo checkout fails with 403 Forbidden when DEMO_MODE is false")
    void demoCheckoutForbiddenWhenDemoModeFalse() {
        CheckoutController nonDemoController = new CheckoutController(stripeCheckoutService, false);
        CheckoutSessionRequest request = new CheckoutSessionRequest(null, List.of(testProduct.getId()));

        ResponseEntity<Map<String, Object>> response = nonDemoController.demoCheckout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("demoMode", false);
    }

    @Test
    @DisplayName("Demo checkout succeeds when DEMO_MODE is true, without mutating stock or saving order")
    void demoCheckoutSucceedsWithoutMutatingStockOrOrders() {
        CheckoutController demoController = new CheckoutController(stripeCheckoutService, true);
        CheckoutSessionRequest request = new CheckoutSessionRequest(null, List.of(testProduct.getId()));

        long orderCountBefore = orderRepository.count();
        int stockBefore = testProduct.getStock();

        ResponseEntity<Map<String, Object>> response = demoController.demoCheckout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("sessionId", "demo_recruiter_instant_checkout");

        // Verify stock is completely unchanged
        Product reloadedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloadedProduct.getStock()).isEqualTo(stockBefore);
        assertThat(reloadedProduct.getStatus()).isEqualTo("AVAILABLE");

        // Verify zero orders were created
        long orderCountAfter = orderRepository.count();
        assertThat(orderCountAfter).isEqualTo(orderCountBefore);
    }

    @Test
    @DisplayName("processCompletedCheckout ignores demo sessions without mutating stock or creating orders")
    void processCompletedCheckoutIgnoresDemoSessions() {
        Session demoSession = new Session();
        demoSession.setId("demo_recruiter_instant_checkout");

        long orderCountBefore = orderRepository.count();
        int stockBefore = testProduct.getStock();

        stripeCheckoutService.processCompletedCheckout(demoSession);

        // Verify zero orders and no stock mutation
        assertThat(orderRepository.count()).isEqualTo(orderCountBefore);
        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(stockBefore);
    }
}
