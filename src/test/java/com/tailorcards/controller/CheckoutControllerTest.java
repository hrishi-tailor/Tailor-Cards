package com.tailorcards.controller;

import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.service.StripeCheckoutService;
import com.tailorcards.api.exception.GlobalExceptionHandler;
import com.tailorcards.api.exception.StockConflictException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock
    private StripeCheckoutService stripeCheckoutService;

    private CheckoutController checkoutController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkoutController = new CheckoutController(stripeCheckoutService);
        mockMvc = MockMvcBuilders.standaloneSetup(checkoutController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createSession_callsServiceAndReturnsUrl() {
        CheckoutSessionRequest request = new CheckoutSessionRequest("cart-123", List.of(1L, 2L));
        CheckoutSessionResponse mockResponse = new CheckoutSessionResponse("https://checkout.stripe.com/pay/cs_test_abc", "cs_test_abc");

        when(stripeCheckoutService.createCheckoutSession(any(CheckoutSessionRequest.class))).thenReturn(mockResponse);

        ResponseEntity<CheckoutSessionResponse> response = checkoutController.createSession(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("https://checkout.stripe.com/pay/cs_test_abc", response.getBody().url());
        assertEquals("cs_test_abc", response.getBody().sessionId());
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

    @Test
    void createSession_stockConflictException_returns409WithCleanMessage() throws Exception {
        when(stripeCheckoutService.createCheckoutSession(any(CheckoutSessionRequest.class)))
                .thenThrow(new StockConflictException("item no longer available at requested quantity"));

        mockMvc.perform(post("/api/checkout/create-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"cart-123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/checkout/create-session"));
    }

    @Test
    void createSession_optimisticLockException_returns409WithCleanMessage() throws Exception {
        when(stripeCheckoutService.createCheckoutSession(any(CheckoutSessionRequest.class)))
                .thenThrow(new OptimisticLockException("Stale row version"));

        mockMvc.perform(post("/api/checkout/create-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"cart-123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/checkout/create-session"));
    }

    @Test
    void handleWebhook_stockConflictException_returns409WithCleanMessage() throws Exception {
        doThrow(new StockConflictException("item no longer available at requested quantity"))
                .when(stripeCheckoutService).handleWebhook(anyString(), any());

        mockMvc.perform(post("/api/checkout/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/checkout/webhook"));
    }

    @Test
    void handleWebhook_springOptimisticLockingFailureException_returns409WithCleanMessage() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException("Product", 42L))
                .when(stripeCheckoutService).handleWebhook(anyString(), any());

        mockMvc.perform(post("/api/checkout/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/checkout/webhook"));
    }
}
