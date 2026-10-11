package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistSubmissionLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuylistSubmissionLineRepository extends JpaRepository<BuylistSubmissionLine, Long> {
    List<BuylistSubmissionLine> findBySubmissionIdOrderByLineNoAsc(Long submissionId);
}
