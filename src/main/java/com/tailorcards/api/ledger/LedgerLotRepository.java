package com.tailorcards.api.ledger;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LedgerLotRepository extends JpaRepository<LedgerLot, Long> {

    Optional<LedgerLot> findByExternalRef(String externalRef);

    List<LedgerLot> findAllByOrderByIdDesc();

    /** Locks the lot row so two sales recorded at once can't both take its last units. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LedgerLot l where l.id = :id")
    Optional<LedgerLot> findForUpdate(@Param("id") Long id);
}
