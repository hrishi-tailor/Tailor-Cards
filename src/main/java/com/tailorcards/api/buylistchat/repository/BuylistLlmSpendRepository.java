package com.tailorcards.api.buylistchat.repository;

import com.tailorcards.api.buylistchat.entity.BuylistLlmSpend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuylistLlmSpendRepository extends JpaRepository<BuylistLlmSpend, java.time.LocalDate> {
}
