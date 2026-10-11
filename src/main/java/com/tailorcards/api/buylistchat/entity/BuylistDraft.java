package com.tailorcards.api.buylistchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Server-side buylist draft owned by one verified email. */
@Entity
@Table(name = "buylist_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuylistDraft {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** OPEN or SUBMITTED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submission_id")
    private Long submissionId;

    /** SELL, TRADE or PARTIAL (trade plus cash); null means SELL. */
    @Column(name = "deal_type", length = 10)
    private String dealType;

    /** PARTIAL deals: cash the customer wants on top of the shop cards. */
    @Column(name = "requested_cash_usd", precision = 12, scale = 2)
    private BigDecimal requestedCashUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
