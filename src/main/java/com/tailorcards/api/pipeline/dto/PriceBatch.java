package com.tailorcards.api.pipeline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Market prices from one pipeline run. {@code source} names the price provider (e.g. TCGDEX) and is stored on each
 * snapshot as the app's own job would store it; {@code runId} ties the snapshots to the run log in S3.
 */
public record PriceBatch(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9:._-]+") String runId,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String source,
        @NotEmpty @Size(max = 5_000) List<@Valid @NotNull Price> prices) {

    public record Price(
            @NotBlank @Size(max = 100) String cardId,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "1000000") BigDecimal priceUsd,
            @NotNull Instant fetchedAt) {
    }
}
