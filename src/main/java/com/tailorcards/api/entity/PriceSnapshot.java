package com.tailorcards.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
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
    name = "price_snapshots",
    indexes = {
        @Index(name = "idx_price_snapshot_card_id", columnList = "card_id"),
        @Index(name = "idx_price_snapshot_fetched_at", columnList = "fetched_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, length = 100)
    private String cardId;

    @Column(name = "price_usd", precision = 10, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "price_cad", precision = 10, scale = 2)
    private BigDecimal priceCad;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @PrePersist
    public void prePersist() {
        if (this.fetchedAt == null) {
            this.fetchedAt = Instant.now();
        }
        if (this.source == null) {
            this.source = "POKEMONTCG_IO";
        }
    }
}
