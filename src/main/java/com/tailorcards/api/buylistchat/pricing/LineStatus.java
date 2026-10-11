package com.tailorcards.api.buylistchat.pricing;

/** Customer-visible status of a buylist line. PENDING only while card resolution is running. */
public enum LineStatus {
    PENDING,
    ELIGIBLE,
    BELOW_MINIMUM,
    /** No longer assigned (photos are optional); kept so older submissions still read. */
    NEEDS_PHOTO,
    NEEDS_REVIEW,
    UNIDENTIFIED
}
