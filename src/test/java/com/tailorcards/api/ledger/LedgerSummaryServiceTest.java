package com.tailorcards.api.ledger;

import com.tailorcards.api.ledger.dto.LedgerSummary;
import com.tailorcards.api.ledger.dto.LotRequest;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("Ledger summary")
class LedgerSummaryServiceTest {

    @Autowired
    private LedgerLotRepository lots;
    @Autowired
    private LedgerSaleRepository sales;
    private LedgerService ledger;
    private LedgerSummaryService summary;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("America/Toronto");
        ledger = new LedgerService(lots, sales, Clock.fixed(LocalDate.of(2026, 10, 20).atStartOfDay(zone).toInstant(), zone));
        summary = new LedgerSummaryService(ledger, sales);

        // A case of 10 at $1,350: 6 sold in September, 2 in October, 2 left
        LotView aCase = lot("Ascended Heroes ETB case", ItemKind.SEALED_CASE, 10, "1350", "2026-09-01");
        sell(aCase, "2026-09-03", 6, "250", Channel.FACEBOOK_MARKETPLACE);
        sell(aCase, "2026-10-02", 2, "250", Channel.CARD_SHOW);
        // A slab bought at 80% of its price
        LotView slab = lot("PSA 10 Lillie's Clefairy", ItemKind.GRADED, 1, "328", "2026-09-18");
        sell(slab, "2026-09-22", 1, "410", Channel.FACEBOOK_MARKETPLACE);
        // An unsold single
        lot("Gengar TG06", ItemKind.SINGLE, 1, "42", "2026-10-10");
    }

    private LotView lot(String title, ItemKind kind, int quantity, String cost, String listedOn) {
        LocalDate listed = LocalDate.parse(listedOn);
        return ledger.createLot(new LotRequest(title, kind, null, quantity, new BigDecimal(cost), listed, null, listed,
                null, null, null), EntrySource.MANUAL);
    }

    private void sell(LotView lot, String soldOn, int quantity, String price, Channel channel) {
        ledger.recordSale(lot.id(), new SaleRequest(LocalDate.parse(soldOn), quantity, new BigDecimal(price), channel,
                null, null, null), EntrySource.MANUAL);
    }

    @Test
    @DisplayName("Totals count realised sales only and cost is shared out by units")
    void totals() {
        LedgerSummary result = summary.summarize(null, null);

        assertThat(result.total().units()).isEqualTo(9);
        assertThat(result.total().revenueCad()).isEqualByComparingTo("2410.00");
        assertThat(result.total().costCad()).isEqualByComparingTo("1408.00");
        assertThat(result.total().grossProfitCad()).isEqualByComparingTo("1002.00");
        assertThat(result.total().marginPct()).isEqualByComparingTo("41.6");
        assertThat(result.onHand().lots()).isEqualTo(2);
        assertThat(result.onHand().units()).isEqualTo(3);
        assertThat(result.onHand().costCad()).isEqualByComparingTo("312.00");
    }

    @Test
    @DisplayName("Breakdowns by kind, channel and month, most profitable first")
    void breakdowns() {
        LedgerSummary result = summary.summarize(null, null);

        assertThat(result.byKind()).extracting(LedgerSummary.Line::key).containsExactly("SEALED_CASE", "GRADED");
        assertThat(result.byKind().get(1).marginPct()).isEqualByComparingTo("20.0");
        assertThat(result.byChannel()).extracting(LedgerSummary.Line::key)
                .containsExactly("FACEBOOK_MARKETPLACE", "CARD_SHOW");
        assertThat(result.byMonth()).extracting(LedgerSummary.Line::key).containsExactly("2026-09", "2026-10");
        // Days listed per sale: 2, 31 (case) and 4 (slab)
        assertThat(result.total().medianDaysListed()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("A date range keeps only sales inside it")
    void dateRange() {
        LedgerSummary october = summary.summarize(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(october.total().units()).isEqualTo(2);
        assertThat(october.total().revenueCad()).isEqualByComparingTo("500.00");
        assertThatThrownBy(() -> summary.summarize(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Median of an even count is the mean of the middle two")
    void median() {
        assertThat(LedgerSummaryService.median(List.of(1, 2, 4, 9))).isEqualByComparingTo("3.0");
        assertThat(LedgerSummaryService.median(List.of())).isNull();
    }
}
