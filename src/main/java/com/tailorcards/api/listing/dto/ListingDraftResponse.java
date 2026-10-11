package com.tailorcards.api.listing.dto;

import com.tailorcards.api.listing.ListingDraft;

import java.util.List;

public record ListingDraftResponse(
        ListingDraft draft,
        MatchStatus matchStatus,
        MarketReferenceResponse marketReference,
        List<CardCandidate> candidates
) {}
