package com.tailorcards.api.ledger.dto;

import com.tailorcards.api.ledger.Channel;
import com.tailorcards.api.ledger.ItemKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A purchase. totalCostCad is what was paid for all units together (a case at $1,350 is quantity 10, total 1350).
 * externalRef is optional: show mode sends an id made on the phone so a retried sync can't add the lot twice.
 */
public record LotRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull ItemKind kind,
        @Size(max = 100) String cardRef,
        @NotNull @Min(1) @Max(100_000) Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal totalCostCad,
        LocalDate acquiredOn,
        Channel acquiredChannel,
        LocalDate listedOn,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal listedPriceCad,
        @Size(max = 500) String notes,
        @Size(max = 80) String externalRef) {
}
