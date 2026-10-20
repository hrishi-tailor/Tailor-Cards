package com.tailorcards.api.ledger;

/** What a lot holds. A sealed case is kept apart from single sealed items because it's bought and sold differently. */
public enum ItemKind {
    SINGLE,
    GRADED,
    SEALED,
    SEALED_CASE,
    LOT
}
