package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuylistDraftRepository extends JpaRepository<BuylistDraft, String> {
    Optional<BuylistDraft> findTopByEmailAndStatusOrderByCreatedAtDesc(String email, String status);
}
