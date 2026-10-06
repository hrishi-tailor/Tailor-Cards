package com.tailorcards.api.controller;

import com.tailorcards.api.dto.CartItemRequest;
import com.tailorcards.api.dto.CartResponse;
import com.tailorcards.api.service.CartService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Shopping Cart", description = "Endpoints for managing guest shopping cart sessions and stock validation")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{cartSessionId}")
    @Operation(summary = "Get guest cart", description = "Retrieve all items, quantities, and calculated total in the guest cart session")
    public ResponseEntity<CartResponse> getCart(@PathVariable String cartSessionId) {
        return ResponseEntity.ok(cartService.getCart(cartSessionId));
    }

    @PostMapping("/{cartSessionId}")
    @Operation(summary = "Add item to cart", description = "Add a product to cart or increment quantity with stock limit verification")
    public ResponseEntity<CartResponse> addToCart(
            @PathVariable String cartSessionId,
            @Valid @RequestBody CartItemRequest request
    ) {
        CartResponse response = cartService.addToCart(cartSessionId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{cartSessionId}/items/{itemId}")
    @Operation(summary = "Remove item from cart", description = "Delete a line item from the guest cart session")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable String cartSessionId,
            @PathVariable Long itemId
    ) {
        cartService.removeFromCart(cartSessionId, itemId);
        return ResponseEntity.noContent().build();
    }
}
