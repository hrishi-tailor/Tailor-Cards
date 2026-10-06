package com.tailorcards.api.repository;

import com.tailorcards.api.entity.BuyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuyRuleRepository extends JpaRepository<BuyRule, Long> {
    List<BuyRule> findByActiveTrueOrderByPriorityAsc();
    List<BuyRule> findAllByOrderByPriorityAsc();
    Optional<BuyRule> findByCategoryCode(String categoryCode);
}
