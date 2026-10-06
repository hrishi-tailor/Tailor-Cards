package com.tailorcards.api.dto;

import java.math.BigDecimal;

public record ProductPokemontcgCandidateDto(
        String cardId,
        String name,
        String setName,
        String cardNumber,
        String imageUrl,
        BigDecimal marketPriceUsd
) {}
