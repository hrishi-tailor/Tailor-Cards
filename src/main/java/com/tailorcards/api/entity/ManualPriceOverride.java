package com.tailorcards.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "manual_price_overrides",
    indexes = {
        @Index(name = "idx_price_override_card_condition", columnList = "card_id, condition_or_grade")
    },
    uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_override_card_condition", columnNames = {"card_id", "condition_or_grade"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ManualPriceOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, length = 100)
    private String cardId;

    @Column(name = "condition_or_grade", nullable = false, length = 50)
    private String conditionOrGrade;

    @Column(name = "override_price_cad", nullable = false, precision = 10, scale = 2)
    private BigDecimal overridePriceCad;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.conditionOrGrade != null) {
            this.conditionOrGrade = this.conditionOrGrade.trim().toUpperCase();
        }
    }
}
