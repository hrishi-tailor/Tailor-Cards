package com.tailorcards.api.security.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminTwoFactorRepository extends JpaRepository<AdminTwoFactor, String> {
}
