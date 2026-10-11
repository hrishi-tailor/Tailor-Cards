package com.tailorcards.api.listing.dto;

public record CardCandidate(
        String cardId,
        String name,
        String setName,
        String cardNumber,
        String imageUrl
) {}
