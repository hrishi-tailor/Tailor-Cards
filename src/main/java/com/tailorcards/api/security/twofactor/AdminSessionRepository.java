package com.tailorcards.api.security.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    @Modifying
    @Transactional
    @Query("DELETE FROM AdminSession s WHERE s.expiresAt < :now")
    int deleteExpired(Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminSession s WHERE s.username = :username")
    int deleteByUsername(String username);
}
