package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistDraftTradeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuylistDraftTradeItemRepository extends JpaRepository<BuylistDraftTradeItem, Long> {

    List<BuylistDraftTradeItem> findByDraftIdOrderByCreatedAtAsc(String draftId);

    Optional<BuylistDraftTradeItem> findByDraftIdAndProductId(String draftId, Long productId);

    long countByDraftId(String draftId);
}
