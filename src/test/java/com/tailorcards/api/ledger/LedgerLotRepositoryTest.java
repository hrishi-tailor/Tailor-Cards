package com.tailorcards.api.ledger;

import com.tailorcards.api.ledger.dto.LotRequest;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("Ledger lot queries")
class LedgerLotRepositoryTest {

    @Autowired
    private LedgerLotRepository lots;
    @Autowired
    private LedgerSaleRepository sales;

    private LotView lot(LedgerService ledger, String cardRef, int quantity) {
        return ledger.createLot(new LotRequest("Card " + cardRef, ItemKind.SINGLE, cardRef, quantity, new BigDecimal("50"),
                LocalDate.of(2026, 9, 1), Channel.CARD_SHOW, null, null, null, null), EntrySource.MANUAL);
    }

    @Test
    @DisplayName("Cards in stock: lots with a card id and units left, each id once")
    void cardRefsInStock() {
        LedgerService ledger = new LedgerService(lots, sales, Clock.systemUTC());
        lot(ledger, "sv8pt5-161", 2);
        lot(ledger, "sv8pt5-161", 1);
        LotView soldOut = lot(ledger, "swsh7-215", 1);
        LotView partlySold = lot(ledger, "sv3pt5-199", 3);
        lot(ledger, null, 1);
        lot(ledger, "  ", 1);
        ledger.recordSale(soldOut.id(), new SaleRequest(LocalDate.of(2026, 9, 2), 1, new BigDecimal("80"),
                Channel.CARD_SHOW, PaymentMethod.CASH, null, null), EntrySource.MANUAL);
        ledger.recordSale(partlySold.id(), new SaleRequest(LocalDate.of(2026, 9, 2), 2, new BigDecimal("80"),
                Channel.CARD_SHOW, PaymentMethod.CASH, null, null), EntrySource.MANUAL);

        assertThat(lots.cardRefsInStock()).containsExactlyInAnyOrder("sv8pt5-161", "sv3pt5-199");
    }
}
