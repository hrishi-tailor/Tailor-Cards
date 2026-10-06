package com.tailorcards.api.repository;

import com.tailorcards.api.entity.TradeParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradeParameterRepository extends JpaRepository<TradeParameter, Long> {
    Optional<TradeParameter> findByParamKey(String paramKey);
}
