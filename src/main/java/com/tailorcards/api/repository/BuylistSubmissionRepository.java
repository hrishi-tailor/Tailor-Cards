package com.tailorcards.api.repository;

import com.tailorcards.api.entity.BuylistSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BuylistSubmissionRepository extends JpaRepository<BuylistSubmission, Long> {
    Optional<BuylistSubmission> findByTrackingToken(String trackingToken);
    boolean existsByTrackingToken(String trackingToken);
    Page<BuylistSubmission> findByStatusIgnoreCase(String status, Pageable pageable);
    Page<BuylistSubmission> findByTrackingTokenStartingWithIgnoreCase(String prefix, Pageable pageable);
    Page<BuylistSubmission> findByStatusIgnoreCaseAndTrackingTokenStartingWithIgnoreCase(String status, String prefix, Pageable pageable);

    // Chatbot daily limits (local_date is the America/Toronto confirmation date)
    boolean existsByCustomerEmailAndLocalDate(String customerEmail, LocalDate localDate);

    long countBySubmitterIpAndLocalDate(String submitterIp, LocalDate localDate);

    List<BuylistSubmission> findByCustomerEmailAndLocalDate(String customerEmail, LocalDate localDate);
}
