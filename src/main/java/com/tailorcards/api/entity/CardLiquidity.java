package com.tailorcards.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "card_liquidity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class CardLiquidity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pokemontcg_id", nullable = false, unique = true, length = 100)
    private String pokemontcgId;

    @Column(name = "liquidity_tier", nullable = false, length = 50)
    private String liquidityTier;

    @Column(name = "haircut", nullable = false, precision = 6, scale = 4)
    private BigDecimal haircut;

    @Column(name = "notes", length = 500)
    private String notes;
}
