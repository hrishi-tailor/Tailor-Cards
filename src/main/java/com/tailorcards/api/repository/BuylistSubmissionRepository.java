package com.tailorcards.api.repository;

import com.tailorcards.api.entity.BuylistSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BuylistSubmissionRepository extends JpaRepository<BuylistSubmission, Long> {
    Optional<BuylistSubmission> findByTrackingToken(String trackingToken);
    Page<BuylistSubmission> findByStatusIgnoreCase(String status, Pageable pageable);
}
