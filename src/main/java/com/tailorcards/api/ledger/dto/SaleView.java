package com.tailorcards.api.ledger.dto;

import com.tailorcards.api.ledger.Channel;
import com.tailorcards.api.ledger.EntrySource;
import com.tailorcards.api.ledger.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A sale with its share of the lot's cost. daysListed is null when the lot has no listing date. */
public record SaleView(
        Long id,
        Long lotId,
        LocalDate soldOn,
        int quantity,
        BigDecimal unitPriceCad,
        BigDecimal revenueCad,
        BigDecimal costCad,
        BigDecimal grossProfitCad,
        Integer daysListed,
        Channel channel,
        PaymentMethod paymentMethod,
        String notes,
        EntrySource source) {
}
