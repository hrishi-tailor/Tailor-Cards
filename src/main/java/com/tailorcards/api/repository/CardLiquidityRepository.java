package com.tailorcards.api.repository;

import com.tailorcards.api.entity.CardLiquidity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardLiquidityRepository extends JpaRepository<CardLiquidity, Long> {
    Optional<CardLiquidity> findByPokemontcgId(String pokemontcgId);
}
