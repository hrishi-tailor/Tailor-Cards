package com.tailorcards.api.repository;

import com.tailorcards.api.entity.PriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findTopByCardIdOrderByFetchedAtDesc(String cardId);

    List<PriceSnapshot> findByCardIdOrderByFetchedAtDesc(String cardId);

    List<PriceSnapshot> findByCardIdOrderByFetchedAtAsc(String cardId);

    List<PriceSnapshot> findByCardIdAndFetchedAtGreaterThanEqualOrderByFetchedAtAsc(String cardId, java.time.Instant fetchedAt);

    List<PriceSnapshot> findTop100ByOrderByFetchedAtDesc();
}
