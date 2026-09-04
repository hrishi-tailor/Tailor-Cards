package com.tailorcards.api.controller;

import com.tailorcards.api.dto.CartItemRequest;
import com.tailorcards.api.dto.CartResponse;
import com.tailorcards.api.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{cartSessionId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable String cartSessionId) {
        return ResponseEntity.ok(cartService.getCart(cartSessionId));
    }

    @PostMapping("/{cartSessionId}")
    public ResponseEntity<CartResponse> addToCart(
            @PathVariable String cartSessionId,
            @Valid @RequestBody CartItemRequest request
    ) {
        CartResponse response = cartService.addToCart(cartSessionId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{cartSessionId}/items/{itemId}")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable String cartSessionId,
            @PathVariable Long itemId
    ) {
        cartService.removeFromCart(cartSessionId, itemId);
        return ResponseEntity.noContent().build();
    }
}
