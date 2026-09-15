package com.tailorcards.service;

import com.stripe.model.checkout.Session;
import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.api.entity.CartItem;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Order;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.CartItemRepository;
import com.tailorcards.api.repository.OrderRepository;
import com.tailorcards.api.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        ReflectionTestUtils.setField(stripeCheckoutService, "successUrl", "http://localhost:5173/checkout/success?session_id={CHECKOUT_SESSION_ID}");
        ReflectionTestUtils.setField(stripeCheckoutService, "cancelUrl", "http://localhost:5173/cart");
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

        CheckoutSessionRequest request = new CheckoutSessionRequest("valid-cart", null);
        CheckoutSessionResponse response = stripeCheckoutService.createCheckoutSession(request);

        assertNotNull(response);
        assertNotNull(response.sessionId());
        assertNotNull(response.url());
        assertTrue(response.url().contains(response.sessionId()));
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
}
