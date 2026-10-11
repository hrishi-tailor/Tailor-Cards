package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuylistDraftLineRepository extends JpaRepository<BuylistDraftLine, Long> {
    List<BuylistDraftLine> findByDraftIdOrderByLineNoAsc(String draftId);

    List<BuylistDraftLine> findByDraftIdAndResolveState(String draftId, String resolveState);

    long countByDraftId(String draftId);

    long countByDraftIdAndResolveState(String draftId, String resolveState);
}
