package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuylistChatMessageRepository extends JpaRepository<BuylistChatMessage, Long> {
    List<BuylistChatMessage> findByDraftIdOrderByCreatedAtAscIdAsc(String draftId);
}
