package com.tailorcards.controller;

import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.service.StripeCheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock
    private StripeCheckoutService stripeCheckoutService;

    private CheckoutController checkoutController;

    @BeforeEach
    void setUp() {
        checkoutController = new CheckoutController(stripeCheckoutService);
    }

    @Test
    void createSession_callsServiceAndReturnsUrl() {
        Map<String, Object> body = Map.of("cartId", "cart-123", "productIds", List.of(1, 2));
        CheckoutSessionResponse mockResponse = new CheckoutSessionResponse("https://checkout.stripe.com/pay/cs_test_abc", "cs_test_abc");

        when(stripeCheckoutService.createCheckoutSession(any(CheckoutSessionRequest.class))).thenReturn(mockResponse);

        ResponseEntity<Map<String, String>> response = checkoutController.createSession(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("https://checkout.stripe.com/pay/cs_test_abc", response.getBody().get("url"));
        assertEquals("cs_test_abc", response.getBody().get("sessionId"));
        verify(stripeCheckoutService).createCheckoutSession(any(CheckoutSessionRequest.class));
    }

    @Test
    void handleWebhook_callsServiceAndReturnsReceivedTrue() {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        String sigHeader = "t=123,v1=abc";

        doNothing().when(stripeCheckoutService).handleWebhook(payload, sigHeader);

        ResponseEntity<Map<String, Object>> response = checkoutController.handleWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("received"));
        verify(stripeCheckoutService).handleWebhook(payload, sigHeader);
    }
}
