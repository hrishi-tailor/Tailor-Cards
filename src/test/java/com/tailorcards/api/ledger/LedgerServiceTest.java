package com.tailorcards.api.ledger;

import com.tailorcards.api.exception.StockConflictException;
import com.tailorcards.api.ledger.dto.LotRequest;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleRequest;
import com.tailorcards.api.ledger.dto.SaleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("Ledger service")
class LedgerServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Autowired
    private LedgerLotRepository lots;
    @Autowired
    private LedgerSaleRepository sales;
    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZoneId.of("America/Toronto")).toInstant(),
                ZoneId.of("America/Toronto"));
        ledger = new LedgerService(lots, sales, clock);
    }

    private static LotRequest lot(String title, ItemKind kind, int quantity, String totalCost, String externalRef) {
        return new LotRequest(title, kind, null, quantity, new BigDecimal(totalCost), LocalDate.of(2026, 8, 10),
                Channel.CARD_SHOW, LocalDate.of(2026, 8, 16), new BigDecimal("230"), null, externalRef);
    }

    private static SaleRequest sale(LocalDate soldOn, int quantity, String unitPrice, String clientRef) {
        return new SaleRequest(soldOn, quantity, new BigDecimal(unitPrice), Channel.FACEBOOK_MARKETPLACE,
                PaymentMethod.E_TRANSFER, null, clientRef);
    }

    @Test
    @DisplayName("A case sold over several sales shares its cost by units, and a giveaway still carries its cost")
    void caseSoldInParts() {
        LotView created = ledger.createLot(lot("Prismatic Evolutions ETB case x2", ItemKind.SEALED_CASE, 20, "1832", null),
                EntrySource.MANUAL);
        ledger.recordSale(created.id(), sale(LocalDate.of(2026, 8, 16), 17, "230", null), EntrySource.MANUAL);
        ledger.recordSale(created.id(), sale(LocalDate.of(2026, 8, 18), 2, "220", null), EntrySource.MANUAL);
        SaleView giveaway = ledger.recordSale(created.id(), sale(LocalDate.of(2026, 8, 27), 1, "0", null), EntrySource.MANUAL);

        assertThat(giveaway.revenueCad()).isEqualByComparingTo("0.00");
        assertThat(giveaway.costCad()).isEqualByComparingTo("91.60");
        assertThat(giveaway.grossProfitCad()).isEqualByComparingTo("-91.60");
        assertThat(giveaway.daysListed()).isEqualTo(11);

        LotView lot = ledger.getLot(created.id());
        assertThat(lot.unitsSold()).isEqualTo(20);
        assertThat(lot.unitsLeft()).isZero();
        assertThat(lot.unitCostCad()).isEqualByComparingTo("91.60");
        assertThat(lot.revenueCad()).isEqualByComparingTo("4350.00");
        assertThat(lot.grossProfitCad()).isEqualByComparingTo("2518.00");
    }

    @Test
    @DisplayName("A sale can't take more units than the lot has left")
    void noOverselling() {
        LotView created = ledger.createLot(lot("Destined Rivals ETB", ItemKind.SEALED, 2, "300", null), EntrySource.MANUAL);
        ledger.recordSale(created.id(), sale(TODAY, 2, "160", null), EntrySource.MANUAL);

        assertThatThrownBy(() -> ledger.recordSale(created.id(), sale(TODAY, 1, "160", null), EntrySource.MANUAL))
                .isInstanceOf(StockConflictException.class)
                .hasMessageContaining("Only 0");
    }

    @Test
    @DisplayName("Sales dated in the future or before the purchase are refused")
    void saleDates() {
        LotView created = ledger.createLot(lot("Umbreon Prime", ItemKind.SINGLE, 1, "40", null), EntrySource.MANUAL);

        assertThatThrownBy(() -> ledger.recordSale(created.id(), sale(TODAY.plusDays(1), 1, "200", null), EntrySource.MANUAL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> ledger.recordSale(created.id(), sale(LocalDate.of(2026, 8, 1), 1, "200", null), EntrySource.MANUAL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("before the purchase");
    }

    @Test
    @DisplayName("Sending the same lot or sale again returns the first one instead of a duplicate")
    void retriesAreIdempotent() {
        LotView first = ledger.createLot(lot("Groudon EX", ItemKind.SINGLE, 1, "30", "show:abc"), EntrySource.SHOW_MODE);
        LotView again = ledger.createLot(lot("Groudon EX", ItemKind.SINGLE, 1, "30", "show:abc"), EntrySource.SHOW_MODE);
        assertThat(again.id()).isEqualTo(first.id());

        SaleView sold = ledger.recordSale(first.id(), sale(TODAY, 1, "200", "show:def"), EntrySource.SHOW_MODE);
        SaleView resent = ledger.recordSale(first.id(), sale(TODAY, 1, "200", "show:def"), EntrySource.SHOW_MODE);
        assertThat(resent.id()).isEqualTo(sold.id());
        assertThat(lots.count()).isEqualTo(1);
        assertThat(sales.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("A lot with sales can't be deleted or shrunk below what has sold")
    void soldLotsKeepTheirHistory() {
        LotView created = ledger.createLot(lot("Ascended Heroes ETB case", ItemKind.SEALED_CASE, 10, "1350", null),
                EntrySource.MANUAL);
        ledger.recordSale(created.id(), sale(TODAY, 4, "250", null), EntrySource.MANUAL);

        assertThatThrownBy(() -> ledger.deleteLot(created.id())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ledger.updateLot(created.id(),
                lot("Ascended Heroes ETB case", ItemKind.SEALED_CASE, 3, "1350", null)))
                .isInstanceOf(StockConflictException.class);

        LotView unsold = ledger.createLot(lot("Spare binder", ItemKind.LOT, 1, "5", null), EntrySource.MANUAL);
        ledger.deleteLot(unsold.id());
        assertThat(lots.existsById(unsold.id())).isFalse();
    }

    @Test
    @DisplayName("A listing date before the purchase date is refused")
    void listingAfterPurchase() {
        LotRequest request = new LotRequest("Celebrations ETB", ItemKind.SEALED, null, 1, new BigDecimal("315"),
                LocalDate.of(2026, 10, 16), null, LocalDate.of(2026, 10, 10), null, null, null);
        assertThatThrownBy(() -> ledger.createLot(request, EntrySource.MANUAL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("listing date");
    }
}
