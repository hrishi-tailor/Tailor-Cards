package com.tailorcards.api.service;

import com.tailorcards.api.dto.CartItemRequest;
import com.tailorcards.api.dto.CartItemResponse;
import com.tailorcards.api.dto.CartResponse;
import com.tailorcards.api.entity.CartItem;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.repository.CartItemRepository;
import com.tailorcards.api.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String cartSessionId) {
        List<CartItem> items = cartItemRepository.findByCartSessionId(cartSessionId);
        return buildCartResponse(cartSessionId, items);
    }

    public CartResponse addToCart(String cartSessionId, CartItemRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));

        Optional<CartItem> existingItemOpt = cartItemRepository
                .findByCartSessionIdAndProductId(cartSessionId, request.productId());

        int currentCartQuantity = existingItemOpt.map(CartItem::getQuantity).orElse(0);
        int targetQuantity = currentCartQuantity + request.quantity();

        if (targetQuantity > product.getStock()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot add %d items. Requested total (%d) exceeds available stock (%d) for product '%s'.",
                    request.quantity(), targetQuantity, product.getStock(), product.getName()
            ));
        }

        CartItem cartItem;
        if (existingItemOpt.isPresent()) {
            cartItem = existingItemOpt.get();
            cartItem.setQuantity(targetQuantity);
        } else {
            cartItem = CartItem.builder()
                    .cartSessionId(cartSessionId)
                    .product(product)
                    .quantity(request.quantity())
                    .build();
        }
        cartItemRepository.save(cartItem);

        List<CartItem> allItems = cartItemRepository.findByCartSessionId(cartSessionId);
        return buildCartResponse(cartSessionId, allItems);
    }

    public void removeFromCart(String cartSessionId, Long itemId) {
        CartItem cartItem = cartItemRepository.findByCartSessionIdAndId(cartSessionId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart item not found with id " + itemId + " in session " + cartSessionId
                ));
        cartItemRepository.delete(cartItem);
    }

    private CartResponse buildCartResponse(String cartSessionId, List<CartItem> items) {
        List<CartItemResponse> itemResponses = items.stream()
                .map(this::toCartItemResponse)
                .toList();

        BigDecimal totalPrice = itemResponses.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = itemResponses.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();

        return new CartResponse(cartSessionId, itemResponses, totalPrice, totalItems);
    }

    private CartItemResponse toCartItemResponse(CartItem item) {
        Product p = item.getProduct();
        BigDecimal price = p.getPrice();
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemResponse(
                item.getId(),
                p.getId(),
                p.getName(),
                price,
                p.getImageUrl(),
                item.getQuantity(),
                subtotal
        );
    }
}
