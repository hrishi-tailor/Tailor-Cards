package com.tailorcards.api.listing.dto;

public enum MatchStatus {
    /** Exactly one catalog card matched; market reference included when available. */
    MATCHED,
    /** Several possible cards; the admin picks one to load its market reference. */
    AMBIGUOUS,
    /** No catalog card found (or sealed product). */
    NONE
}
