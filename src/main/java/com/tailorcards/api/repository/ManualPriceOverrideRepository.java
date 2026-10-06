package com.tailorcards.api.repository;

import com.tailorcards.api.entity.ManualPriceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManualPriceOverrideRepository extends JpaRepository<ManualPriceOverride, Long> {

    @Query("SELECT m FROM ManualPriceOverride m WHERE m.cardId = :cardId AND UPPER(m.conditionOrGrade) = UPPER(:conditionOrGrade) ORDER BY m.updatedAt DESC")
    List<ManualPriceOverride> findByCardIdAndConditionOrGrade(
            @Param("cardId") String cardId,
            @Param("conditionOrGrade") String conditionOrGrade
    );

    default Optional<ManualPriceOverride> findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(
            String cardId,
            String conditionOrGrade
    ) {
        List<ManualPriceOverride> list = findByCardIdAndConditionOrGrade(cardId, conditionOrGrade);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    List<ManualPriceOverride> findByCardIdOrderByUpdatedAtDesc(String cardId);

    List<ManualPriceOverride> findAllByOrderByUpdatedAtDesc();
}
