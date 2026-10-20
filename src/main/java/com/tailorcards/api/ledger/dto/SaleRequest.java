package com.tailorcards.api.ledger.dto;

import com.tailorcards.api.ledger.Channel;
import com.tailorcards.api.ledger.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Units sold from a lot at one price. A unit price of 0 records a giveaway (its cost still counts).
 * clientRef is optional: show mode sends an id made on the phone so a retried sync can't record the sale twice.
 */
public record SaleRequest(
        @NotNull LocalDate soldOn,
        @NotNull @Min(1) @Max(100_000) Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPriceCad,
        @NotNull Channel channel,
        PaymentMethod paymentMethod,
        @Size(max = 500) String notes,
        @Size(max = 80) String clientRef) {
}
