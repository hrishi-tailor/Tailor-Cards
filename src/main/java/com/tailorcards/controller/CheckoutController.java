package com.tailorcards.controller;

import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.service.StripeCheckoutService;
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
public class CheckoutController {

    private final StripeCheckoutService stripeCheckoutService;

    public CheckoutController(StripeCheckoutService stripeCheckoutService) {
        this.stripeCheckoutService = stripeCheckoutService;
    }

    @PostMapping("/create-session")
    public ResponseEntity<CheckoutSessionResponse> createSession(@RequestBody(required = false) CheckoutSessionRequest request) {
        CheckoutSessionRequest effectiveRequest = request != null ? request : new CheckoutSessionRequest(null, null);
        CheckoutSessionResponse session = stripeCheckoutService.createCheckoutSession(effectiveRequest);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        stripeCheckoutService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
