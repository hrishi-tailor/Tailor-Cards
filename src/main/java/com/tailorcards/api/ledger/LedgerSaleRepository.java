package com.tailorcards.api.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LedgerSaleRepository extends JpaRepository<LedgerSale, Long> {

    Optional<LedgerSale> findByClientRef(String clientRef);

    List<LedgerSale> findByLotIdOrderBySoldOnAscIdAsc(Long lotId);

    boolean existsByLotId(Long lotId);

    @Query("select coalesce(sum(s.quantity), 0) from LedgerSale s where s.lot.id = :lotId")
    int unitsSold(@Param("lotId") Long lotId);

    /** Every sale with its lot, for the summary (the ledger of one store stays small). */
    @Query("select s from LedgerSale s join fetch s.lot order by s.soldOn, s.id")
    List<LedgerSale> findAllWithLot();
}
