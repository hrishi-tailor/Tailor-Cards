package com.tailorcards.api.repository;

import com.tailorcards.api.entity.TradeAssistantRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradeAssistantRequestRepository extends JpaRepository<TradeAssistantRequest, Long> {

    Optional<TradeAssistantRequest> findByReferenceCode(String referenceCode);

    Page<TradeAssistantRequest> findByStatus(String status, Pageable pageable);
}
