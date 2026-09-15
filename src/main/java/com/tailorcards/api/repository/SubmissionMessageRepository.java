package com.tailorcards.api.repository;

import com.tailorcards.api.entity.SubmissionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionMessageRepository extends JpaRepository<SubmissionMessage, Long> {
    List<SubmissionMessage> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
}
