package com.tailorcards.api.ledger.dto;

import com.tailorcards.api.ledger.Channel;
import com.tailorcards.api.ledger.EntrySource;
import com.tailorcards.api.ledger.ItemKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A lot with what has sold from it so far. Revenue and profit count realised sales only. */
public record LotView(
        Long id,
        String title,
        ItemKind kind,
        String cardRef,
        int quantity,
        int unitsSold,
        int unitsLeft,
        BigDecimal totalCostCad,
        BigDecimal unitCostCad,
        LocalDate acquiredOn,
        Channel acquiredChannel,
        LocalDate listedOn,
        BigDecimal listedPriceCad,
        String notes,
        EntrySource source,
        BigDecimal revenueCad,
        BigDecimal grossProfitCad,
        Instant createdAt,
        List<SaleView> sales) {
}
