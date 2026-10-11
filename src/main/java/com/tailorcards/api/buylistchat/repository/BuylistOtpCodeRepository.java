package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistOtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;

public interface BuylistOtpCodeRepository extends JpaRepository<BuylistOtpCode, Long> {
    Optional<BuylistOtpCode> findTopByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant since);
}
