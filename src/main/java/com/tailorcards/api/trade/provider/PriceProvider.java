package com.tailorcards.api.trade.provider;

import java.util.List;
import java.util.Optional;

public interface PriceProvider {

    /**
     * Unique identifier/name of this provider (e.g. "POKEMONTCG_IO").
     */
    String getProviderName();

    /**
     * Fetch market price and metadata for a specific Pokémon card by its provider ID (e.g. "base1-4").
     */
    Optional<CardMarketPrice> fetchPrice(String cardId);

    /**
     * Search Pokémon cards by name or query string, returning card metadata, images, and prices.
     */
    List<CardMarketPrice> searchCards(String query, int limit);
}
