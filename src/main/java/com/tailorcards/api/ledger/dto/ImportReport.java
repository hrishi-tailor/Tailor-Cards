package com.tailorcards.api.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a ledger CSV import: what the file holds and whether it was saved. A dry run is never saved, and a
 * real run is saved only when no row has an error (all or nothing). Lots whose lot_ref was imported before are
 * skipped with their sales; lots, sales, revenue and cost count only what is new.
 */
public record ImportReport(
        boolean saved,
        int rows,
        int lots,
        int sales,
        int lotsAlreadyImported,
        BigDecimal revenueCad,
        BigDecimal costCad,
        List<RowError> errors) {

    /** line is the line number in the file, counting the header as line 1. */
    public record RowError(int line, String message) {
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
