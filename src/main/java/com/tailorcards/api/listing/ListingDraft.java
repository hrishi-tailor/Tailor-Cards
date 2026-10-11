package com.tailorcards.api.listing;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Validated text draft read from card photos. Deliberately has no price field: prices never
 * come from the model.
 */
public record ListingDraft(
        String cardName,
        String setName,
        String cardNumber,
        String rarity,
        String language,
        ListingCondition condition,
        String gradingCompany,
        String grade,
        @JsonProperty("isSealed") boolean isSealed,
        String title,
        String description,
        String conditionNotes,
        ListingConfidence confidence,
        List<String> uncertainties
) {}
