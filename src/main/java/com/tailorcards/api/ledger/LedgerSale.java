package com.tailorcards.api.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Units sold from a lot at one price on one day. A unit price of 0 records a giveaway. */
@Entity
@Table(name = "ledger_sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private LedgerLot lot;

    @Column(name = "sold_on", nullable = false)
    private LocalDate soldOn;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_cad", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceCad;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private EntrySource source;

    @Column(name = "client_ref", length = 80, unique = true)
    private String clientRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BigDecimal revenueCad() {
        return unitPriceCad.multiply(BigDecimal.valueOf(quantity));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
