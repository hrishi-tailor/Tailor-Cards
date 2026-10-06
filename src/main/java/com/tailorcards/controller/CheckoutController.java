package com.tailorcards.controller;

import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.service.StripeCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
@Tag(name = "Checkout & Stripe", description = "Endpoints for creating Stripe Checkout Sessions and receiving webhook events")
public class CheckoutController {

    private final StripeCheckoutService stripeCheckoutService;

    public CheckoutController(StripeCheckoutService stripeCheckoutService) {
        this.stripeCheckoutService = stripeCheckoutService;
    }

    @PostMapping("/create-session")
    @Operation(summary = "Create Stripe checkout session", description = "Validates inventory stock and creates a hosted Stripe payment session for guest cart")
    public ResponseEntity<CheckoutSessionResponse> createSession(@RequestBody(required = false) CheckoutSessionRequest request) {
        CheckoutSessionRequest effectiveRequest = request != null ? request : new CheckoutSessionRequest(null, null);
        CheckoutSessionResponse session = stripeCheckoutService.createCheckoutSession(effectiveRequest);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook endpoint", description = "Receives signed checkout.session.completed events from Stripe to decrement stock and finalize orders")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        stripeCheckoutService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
