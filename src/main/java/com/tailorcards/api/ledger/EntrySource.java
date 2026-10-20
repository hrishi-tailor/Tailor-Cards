package com.tailorcards.api.ledger;

/** How a ledger entry was created, so imported and phone-logged records can be told apart from typed ones. */
public enum EntrySource {
    MANUAL,
    CSV_IMPORT,
    SHOW_MODE
}
