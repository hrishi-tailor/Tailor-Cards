package com.tailorcards.api.security.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AdminBackupCodeRepository extends JpaRepository<AdminBackupCode, Long> {
    List<AdminBackupCode> findByUsernameAndUsedAtIsNull(String username);

    @Transactional
    void deleteByUsername(String username);
}
