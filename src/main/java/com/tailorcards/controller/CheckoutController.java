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
    public ResponseEntity<Map<String, String>> createSession(@RequestBody(required = false) Map<String, Object> body) {
        String cartId = null;
        List<Long> productIds = null;

        if (body != null) {
            if (body.get("cartId") != null) {
                cartId = body.get("cartId").toString();
            }
            if (body.get("productIds") instanceof List<?> list) {
                productIds = list.stream()
                        .map(Object::toString)
                        .map(Long::valueOf)
                        .toList();
            }
        }

        CheckoutSessionResponse session = stripeCheckoutService.createCheckoutSession(
                new CheckoutSessionRequest(cartId, productIds)
        );

        return ResponseEntity.ok(Map.of(
                "url", session.url(),
                "sessionId", session.sessionId()
        ));
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
