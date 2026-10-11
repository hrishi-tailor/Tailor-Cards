package com.tailorcards.api.listing;

/**
 * Raw-card conditions used across the store (trade extraction, storefront condition stamps),
 * plus UNKNOWN for when the photos do not show enough to judge.
 */
public enum ListingCondition {
    NEAR_MINT,
    LIGHTLY_PLAYED,
    MODERATELY_PLAYED,
    HEAVILY_PLAYED,
    DAMAGED,
    UNKNOWN
}
