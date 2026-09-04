package com.tailorcards.api.repository;

import com.tailorcards.api.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartSessionId(String cartSessionId);
    Optional<CartItem> findByCartSessionIdAndProductId(String cartSessionId, Long productId);
    Optional<CartItem> findByCartSessionIdAndId(String cartSessionId, Long id);
    void deleteByCartSessionIdAndId(String cartSessionId, Long id);
    void deleteByCartSessionId(String cartSessionId);
}
