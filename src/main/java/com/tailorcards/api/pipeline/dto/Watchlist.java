package com.tailorcards.api.pipeline.dto;

import java.time.Instant;
import java.util.List;

/** The cards the price pipeline should price: products on the site plus ledger stock, each id once, sorted. */
public record Watchlist(List<String> cardIds, Instant generatedAt) {
}
