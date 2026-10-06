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
@Table(name = "buy_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class BuyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "category_code", nullable = false, unique = true, length = 100)
    private String categoryCode;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
