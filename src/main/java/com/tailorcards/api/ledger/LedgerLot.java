package com.tailorcards.api.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/** One purchase at one total cost: a card, a slab, or a whole sealed case. Sales draw units from it. */
@Entity
@Table(name = "ledger_lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ItemKind kind;

    /** TCGdex card id (e.g. sv03.5-166) when the lot is a known card; links it to market prices. */
    @Column(name = "card_ref", length = 100)
    private String cardRef;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "total_cost_cad", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCostCad;

    @Column(name = "acquired_on")
    private LocalDate acquiredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquired_channel", length = 30)
    private Channel acquiredChannel;

    @Column(name = "listed_on")
    private LocalDate listedOn;

    /** Asking price per unit when listed. */
    @Column(name = "listed_price_cad", precision = 12, scale = 2)
    private BigDecimal listedPriceCad;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private EntrySource source;

    @Column(name = "external_ref", length = 80, unique = true)
    private String externalRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Average cost of one unit, to the cent. */
    public BigDecimal unitCostCad() {
        return totalCostCad.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
