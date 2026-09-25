package com.tailorcards.service;

import com.stripe.model.checkout.Session;
import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.api.entity.CartItem;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Order;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.StockConflictException;
import com.tailorcards.api.repository.CartItemRepository;
import com.tailorcards.api.repository.OrderRepository;
import com.tailorcards.api.repository.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    private StripeCheckoutService stripeCheckoutService;

    @BeforeEach
    void setUp() {
        stripeCheckoutService = new StripeCheckoutService(productRepository, cartItemRepository, orderRepository);
        ReflectionTestUtils.setField(stripeCheckoutService, "secretKey", "");
        ReflectionTestUtils.setField(stripeCheckoutService, "webhookSecret", "");
        ReflectionTestUtils.setField(stripeCheckoutService, "frontendUrl", "https://tailorcards.com");
        ReflectionTestUtils.setField(stripeCheckoutService, "successUrl", "https://tailorcards.com/checkout/success?session_id={CHECKOUT_SESSION_ID}");
        ReflectionTestUtils.setField(stripeCheckoutService, "cancelUrl", "https://tailorcards.com/cart");
    }

    @Test
    void createCheckoutSession_emptyCart_throwsIllegalArgumentException() {
        when(cartItemRepository.findByCartSessionId("empty-cart")).thenReturn(List.of());

        CheckoutSessionRequest request = new CheckoutSessionRequest("empty-cart", null);
        assertThrows(IllegalArgumentException.class, () -> stripeCheckoutService.createCheckoutSession(request));
    }

    @Test
    void createCheckoutSession_soldProduct_throwsIllegalStateException() {
        Product soldProduct = Product.builder()
                .id(1L)
                .name("Charizard Base Set 1st Edition")
                .price(new BigDecimal("12500.00"))
                .stock(1)
                .status("SOLD")
                .build();

        CartItem cartItem = CartItem.builder()
                .id(10L)
                .cartSessionId("session-1")
                .product(soldProduct)
                .quantity(1)
                .build();

        when(cartItemRepository.findByCartSessionId("session-1")).thenReturn(List.of(cartItem));
        when(productRepository.findById(1L)).thenReturn(Optional.of(soldProduct));

        CheckoutSessionRequest request = new CheckoutSessionRequest("session-1", null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> stripeCheckoutService.createCheckoutSession(request));
        assertTrue(ex.getMessage().contains("already sold"));
    }

    @Test
    void createCheckoutSession_outOfStockProduct_throwsIllegalStateException() {
        Product oosProduct = Product.builder()
                .id(2L)
                .name("Gengar VMAX Alt Art")
                .price(new BigDecimal("450.00"))
                .stock(0)
                .status("AVAILABLE")
                .build();

        CartItem cartItem = CartItem.builder()
                .id(11L)
                .cartSessionId("session-2")
                .product(oosProduct)
                .quantity(1)
                .build();

        when(cartItemRepository.findByCartSessionId("session-2")).thenReturn(List.of(cartItem));
        when(productRepository.findById(2L)).thenReturn(Optional.of(oosProduct));

        CheckoutSessionRequest request = new CheckoutSessionRequest("session-2", null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> stripeCheckoutService.createCheckoutSession(request));
        assertTrue(ex.getMessage().contains("out of stock"));
    }

    @Test
    void createCheckoutSession_validCart_returnsCheckoutSessionResponse() {
        Category category = Category.builder().id(1L).name("Singles").build();
        Product product = Product.builder()
                .id(3L)
                .name("Pikachu Illustrator Promo")
                .price(new BigDecimal("5000.00"))
                .stock(1)
                .status("AVAILABLE")
                .category(category)
                .imageUrl("https://images.pokemontcg.io/test.png")
                .build();

        CartItem cartItem = CartItem.builder()
                .id(12L)
                .cartSessionId("valid-cart")
                .product(product)
                .quantity(1)
                .build();

        when(cartItemRepository.findByCartSessionId("valid-cart")).thenReturn(List.of(cartItem));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));

        Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_real_123");
        when(mockSession.getId()).thenReturn("cs_test_real_123");

        try (var mockedStatic = mockStatic(Session.class)) {
            mockedStatic.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)))
                    .thenAnswer(invocation -> {
                        com.stripe.param.checkout.SessionCreateParams params = invocation.getArgument(0);
                        assertEquals(com.stripe.param.checkout.SessionCreateParams.BillingAddressCollection.REQUIRED, params.getBillingAddressCollection());
                        assertNotNull(params.getShippingAddressCollection());
                        assertTrue(params.getShippingAddressCollection().getAllowedCountries().contains(
                                com.stripe.param.checkout.SessionCreateParams.ShippingAddressCollection.AllowedCountry.CA
                        ));
                        assertTrue(params.getShippingAddressCollection().getAllowedCountries().contains(
                                com.stripe.param.checkout.SessionCreateParams.ShippingAddressCollection.AllowedCountry.US
                        ));
                        return mockSession;
                    });

            CheckoutSessionRequest request = new CheckoutSessionRequest("valid-cart", null);
            CheckoutSessionResponse response = stripeCheckoutService.createCheckoutSession(request);

            assertNotNull(response);
            assertEquals("cs_test_real_123", response.sessionId());
            assertEquals("https://checkout.stripe.com/pay/cs_test_real_123", response.url());
            mockedStatic.verify(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)));
        }
    }

    @Test
    void createCheckoutSession_stripeException_throwsRuntimeException() {
        Category category = Category.builder().id(1L).name("Singles").build();
        Product product = Product.builder()
                .id(4L)
                .name("Charizard Base Set")
                .price(new BigDecimal("100.00"))
                .stock(1)
                .status("AVAILABLE")
                .category(category)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(13L)
                .cartSessionId("fail-cart")
                .product(product)
                .quantity(1)
                .build();

        when(cartItemRepository.findByCartSessionId("fail-cart")).thenReturn(List.of(cartItem));
        when(productRepository.findById(4L)).thenReturn(Optional.of(product));

        try (var mockedStatic = mockStatic(Session.class)) {
            mockedStatic.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)))
                    .thenThrow(new com.stripe.exception.AuthenticationException("Invalid API Key", null, null, 401));

            CheckoutSessionRequest request = new CheckoutSessionRequest("fail-cart", null);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> stripeCheckoutService.createCheckoutSession(request));
            assertTrue(ex.getMessage().contains("Failed to initiate Stripe Checkout"));
        }
    }

    @Test
    void processCompletedCheckout_marksProductSold_clearsCart_andSavesOrder() {
        Product product = Product.builder()
                .id(5L)
                .name("Lugia 1st Edition Neo Genesis")
                .price(new BigDecimal("1800.00"))
                .stock(1)
                .status("AVAILABLE")
                .build();

        when(orderRepository.existsByStripeSessionId("cs_test_completed_123")).thenReturn(false);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_completed_123");
        when(mockSession.getMetadata()).thenReturn(Map.of("cartId", "cart-buyer-77", "productIds", "5"));
        when(mockSession.getAmountTotal()).thenReturn(180000L);
        when(mockSession.getCurrency()).thenReturn("cad");

        stripeCheckoutService.processCompletedCheckout(mockSession);

        assertEquals("SOLD", product.getStatus());
        assertEquals(0, product.getStock());
        verify(productRepository).save(product);
        verify(cartItemRepository).deleteByCartSessionId("cart-buyer-77");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void processCompletedCheckout_idempotentWhenOrderAlreadyExists() {
        when(orderRepository.existsByStripeSessionId("cs_test_already_processed")).thenReturn(true);

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_already_processed");

        stripeCheckoutService.processCompletedCheckout(mockSession);

        verify(productRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByCartSessionId(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void resolveUrls_usesFrontendUrlWhenConfigured() {
        ReflectionTestUtils.setField(stripeCheckoutService, "frontendUrl", "https://tailorcards.com");
        ReflectionTestUtils.setField(stripeCheckoutService, "successUrl", "");
        ReflectionTestUtils.setField(stripeCheckoutService, "cancelUrl", "");

        assertEquals("https://tailorcards.com/checkout/success?session_id={CHECKOUT_SESSION_ID}", stripeCheckoutService.resolveSuccessUrl());
        assertEquals("https://tailorcards.com/cart", stripeCheckoutService.resolveCancelUrl());
    }

    @Test
    void resolveUrls_stripsTrailingSlashFromFrontendUrl() {
        ReflectionTestUtils.setField(stripeCheckoutService, "frontendUrl", "https://tailorcards.com/");
        ReflectionTestUtils.setField(stripeCheckoutService, "successUrl", "");
        ReflectionTestUtils.setField(stripeCheckoutService, "cancelUrl", "");

        assertEquals("https://tailorcards.com/checkout/success?session_id={CHECKOUT_SESSION_ID}", stripeCheckoutService.resolveSuccessUrl());
        assertEquals("https://tailorcards.com/cart", stripeCheckoutService.resolveCancelUrl());
    }

    @Test
    void processCompletedCheckout_optimisticLockException_throwsStockConflictException() {
        Product product = Product.builder()
                .id(5L)
                .name("Lugia 1st Edition Neo Genesis")
                .price(new BigDecimal("1800.00"))
                .stock(1)
                .status("AVAILABLE")
                .build();

        when(orderRepository.existsByStripeSessionId("cs_test_conflict_123")).thenReturn(false);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 5L));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_conflict_123");
        when(mockSession.getMetadata()).thenReturn(Map.of("cartId", "cart-buyer-88", "productIds", "5"));

        StockConflictException ex = assertThrows(
                StockConflictException.class,
                () -> stripeCheckoutService.processCompletedCheckout(mockSession)
        );

        assertEquals("item no longer available at requested quantity", ex.getMessage());
    }

    @Test
    void decrementStock_optimisticLockException_throwsStockConflictException() {
        Product product = Product.builder()
                .id(99L)
                .name("Charizard")
                .stock(5)
                .build();

        when(productRepository.findById(99L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 99L));

        StockConflictException ex = assertThrows(
                StockConflictException.class,
                () -> stripeCheckoutService.decrementStock(99L, 1)
        );

        assertEquals("item no longer available at requested quantity", ex.getMessage());
    }

    @Test
    void decrementStock_jakartaOptimisticLockException_throwsStockConflictException() {
        Product product = Product.builder()
                .id(101L)
                .name("Blastoise")
                .stock(3)
                .build();

        when(productRepository.findById(101L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenThrow(new OptimisticLockException("Row updated by another transaction"));

        StockConflictException ex = assertThrows(
                StockConflictException.class,
                () -> stripeCheckoutService.decrementStock(101L, 1)
        );

        assertEquals("item no longer available at requested quantity", ex.getMessage());
    }

    @Test
    void createCheckoutSession_finalStockRevalidationFails_abortsStripeAndThrowsStockConflictException() {
        Category category = Category.builder().id(1L).name("Singles").build();
        Product productAvailable = Product.builder()
                .id(3L)
                .name("Pikachu Illustrator Promo")
                .price(new BigDecimal("5000.00"))
                .stock(1)
                .status("AVAILABLE")
                .category(category)
                .build();

        Product productOutOfStock = Product.builder()
                .id(3L)
                .name("Pikachu Illustrator Promo")
                .price(new BigDecimal("5000.00"))
                .stock(0)
                .status("AVAILABLE")
                .category(category)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(12L)
                .cartSessionId("valid-cart")
                .product(productAvailable)
                .quantity(1)
                .build();

        when(cartItemRepository.findByCartSessionId("valid-cart")).thenReturn(List.of(cartItem));
        when(productRepository.findById(3L)).thenReturn(Optional.of(productAvailable), Optional.of(productOutOfStock));

        try (var mockedStatic = mockStatic(Session.class)) {
            CheckoutSessionRequest request = new CheckoutSessionRequest("valid-cart", null);

            StockConflictException ex = assertThrows(
                    StockConflictException.class,
                    () -> stripeCheckoutService.createCheckoutSession(request)
            );

            assertEquals("item no longer available at requested quantity", ex.getMessage());
            mockedStatic.verifyNoInteractions();
        }
    }

    @Test
    void createCheckoutSession_productSoldRightBeforeStripeCall_abortsStripeAndThrowsStockConflictException() {
        Category category = Category.builder().id(1L).name("Singles").build();
        Product productAvailable = Product.builder()
                .id(3L)
                .name("Pikachu Illustrator Promo")
                .price(new BigDecimal("5000.00"))
                .stock(1)
                .status("AVAILABLE")
                .category(category)
                .build();

        Product productSold = Product.builder()
                .id(3L)
                .name("Pikachu Illustrator Promo")
                .price(new BigDecimal("5000.00"))
                .stock(0)
                .status("SOLD")
                .category(category)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(12L)
                .cartSessionId("valid-cart")
                .product(productAvailable)
                .quantity(1)
                .build();

        when(cartItemRepository.findByCartSessionId("valid-cart")).thenReturn(List.of(cartItem));
        when(productRepository.findById(3L)).thenReturn(Optional.of(productAvailable), Optional.of(productSold));

        try (var mockedStatic = mockStatic(Session.class)) {
            CheckoutSessionRequest request = new CheckoutSessionRequest("valid-cart", null);

            StockConflictException ex = assertThrows(
                    StockConflictException.class,
                    () -> stripeCheckoutService.createCheckoutSession(request)
            );

            assertEquals("item no longer available at requested quantity", ex.getMessage());
            mockedStatic.verifyNoInteractions();
        }
    }

    @Test
    void validateStock_productNotFound_throwsStockConflictException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        StockConflictException ex = assertThrows(
                StockConflictException.class,
                () -> stripeCheckoutService.validateStock(999L, 1)
        );

        assertEquals("item no longer available at requested quantity", ex.getMessage());
    }

    @Test
    void validateStock_insufficientStock_throwsStockConflictException() {
        Product product = Product.builder()
                .id(10L)
                .name("Venusaur")
                .stock(1)
                .status("AVAILABLE")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        StockConflictException ex = assertThrows(
                StockConflictException.class,
                () -> stripeCheckoutService.validateStock(10L, 2)
        );

        assertEquals("item no longer available at requested quantity", ex.getMessage());
    }

    @Test
    void validateStock_productSold_throwsStockConflictException() {
        Product product = Product.builder()
                .id(10L)
                .name("Venusaur")
                .stock(5)
                .status("SOLD")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        StockConflictException ex = assertThrows(
                StockConflictException.class,
                () -> stripeCheckoutService.validateStock(10L, 1)
        );

        assertEquals("item no longer available at requested quantity", ex.getMessage());
    }

    @Test
    void validateStock_sufficientStock_succeeds() {
        Product product = Product.builder()
                .id(10L)
                .name("Venusaur")
                .stock(5)
                .status("AVAILABLE")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertDoesNotThrow(() -> stripeCheckoutService.validateStock(10L, 2));
    }
}

