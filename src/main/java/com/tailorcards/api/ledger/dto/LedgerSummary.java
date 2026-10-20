package com.tailorcards.api.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Realised results for sales between from and to (inclusive; null means unbounded), plus stock still on hand.
 * Margin is gross profit over revenue in percent, null when there is no revenue. Median days listed is per sale,
 * over sales whose lot has a listing date.
 */
public record LedgerSummary(
        LocalDate from,
        LocalDate to,
        Line total,
        List<Line> byKind,
        List<Line> byChannel,
        List<Line> byMonth,
        OnHand onHand) {

    public record Line(
            String key,
            int sales,
            int units,
            BigDecimal revenueCad,
            BigDecimal costCad,
            BigDecimal grossProfitCad,
            BigDecimal marginPct,
            BigDecimal medianDaysListed) {
    }

    /** Units not yet sold, at what they cost. */
    public record OnHand(int lots, int units, BigDecimal costCad) {
    }
}
