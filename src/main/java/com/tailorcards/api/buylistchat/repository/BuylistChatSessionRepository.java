package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuylistChatSessionRepository extends JpaRepository<BuylistChatSession, Long> {
    Optional<BuylistChatSession> findByTokenHash(String tokenHash);
}
