package com.tailorcards.controller;

import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.service.StripeCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final boolean demoMode;

    public CheckoutController(StripeCheckoutService stripeCheckoutService) {
        this(stripeCheckoutService, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CheckoutController(
            StripeCheckoutService stripeCheckoutService,
            @Value("${DEMO_MODE:${app.demo-mode:false}}") boolean demoMode
    ) {
        this.stripeCheckoutService = stripeCheckoutService;
        this.demoMode = demoMode;
    }

    @PostMapping("/create-session")
    @Operation(summary = "Create Stripe checkout session", description = "Validates inventory stock and creates a hosted Stripe payment session for guest cart")
    public ResponseEntity<CheckoutSessionResponse> createSession(@RequestBody(required = false) CheckoutSessionRequest request) {
        CheckoutSessionRequest effectiveRequest = request != null ? request : new CheckoutSessionRequest(null, null);
        CheckoutSessionResponse session = stripeCheckoutService.createCheckoutSession(effectiveRequest);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/demo")
    @Operation(summary = "Instant Demo Checkout", description = "Simulates acquisition in DEMO_MODE without modifying stock or creating real orders")
    public ResponseEntity<Map<String, Object>> demoCheckout(@RequestBody(required = false) CheckoutSessionRequest request) {
        if (!demoMode) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Demo checkout is disabled on this server. Enable DEMO_MODE to use this feature.",
                            "demoMode", false
                    ));
        }
        CheckoutSessionRequest effectiveRequest = request != null ? request : new CheckoutSessionRequest(null, null);
        Map<String, Object> result = stripeCheckoutService.simulateDemoCheckout(effectiveRequest);
        return ResponseEntity.ok(result);
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
