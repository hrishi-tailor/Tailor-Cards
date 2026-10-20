package com.tailorcards.api.ledger;

import com.tailorcards.api.ledger.dto.ImportReport;
import com.tailorcards.api.ledger.dto.ImportReport.RowError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("Ledger CSV import")
class LedgerCsvImporterTest {

    private static final String HEADER = "lot_ref,title,kind,quantity,total_cost_cad,acquired_on,acquired_channel,"
            + "listed_on,listed_price_cad,card_ref,notes,sold_on,sold_quantity,sale_unit_price_cad,sale_channel,"
            + "payment_method,sale_notes\n";

    /** Two Prismatic cases sold over three rows (one ETB given away) and a Groudon bought cheap. */
    private static final String VALID = HEADER
            + "PRE-1,Prismatic Evolutions ETB (2 cases),case,20,\"$1,832\",2026-08-14,card show,2026-08-16,230,,,"
            + "2026-08-16,17,230,facebook,e-transfer,\n"
            + "PRE-1,,,,,,,,,,,2026-08-18,2,220,facebook,e-transfer,\n"
            + "PRE-1,,,,,,,,,,,2026-08-27,1,0,show,,given to a kid\n"
            + "GRD-1,Team Magma's Groudon EX 15/34,single,1,30,,,2026-04-24,200,,,2026-04-24,1,200,marketplace,cash,\n";

    @Autowired
    private LedgerLotRepository lots;
    @Autowired
    private LedgerSaleRepository sales;
    private LedgerCsvImporter importer;

    @BeforeEach
    void setUp() {
        importer = new LedgerCsvImporter(new LedgerService(lots, sales), lots);
    }

    private ImportReport run(String csv, boolean dryRun) {
        return importer.importCsv(csv.getBytes(StandardCharsets.UTF_8), dryRun);
    }

    @Test
    @DisplayName("A dry run reports what would be saved and saves nothing")
    void dryRun() {
        ImportReport report = run(VALID, true);

        assertThat(report.ok()).isTrue();
        assertThat(report.saved()).isFalse();
        assertThat(report.rows()).isEqualTo(4);
        assertThat(report.lots()).isEqualTo(2);
        assertThat(report.sales()).isEqualTo(4);
        assertThat(report.revenueCad()).isEqualByComparingTo("4550.00");
        assertThat(report.costCad()).isEqualByComparingTo("1862.00");
        assertThat(lots.count()).isZero();
    }

    @Test
    @DisplayName("A real run saves lots and sales, and importing the same file again adds nothing")
    void saveThenReimport() {
        assertThat(run(VALID, false).saved()).isTrue();
        assertThat(lots.count()).isEqualTo(2);
        assertThat(sales.count()).isEqualTo(4);
        LedgerLot prismatic = lots.findByExternalRef("csv:PRE-1").orElseThrow();
        assertThat(prismatic.getSource()).isEqualTo(EntrySource.CSV_IMPORT);
        assertThat(prismatic.getKind()).isEqualTo(ItemKind.SEALED_CASE);
        assertThat(prismatic.getAcquiredChannel()).isEqualTo(Channel.CARD_SHOW);

        ImportReport again = run(VALID, false);
        assertThat(again.lots()).isZero();
        assertThat(again.lotsAlreadyImported()).isEqualTo(2);
        assertThat(lots.count()).isEqualTo(2);
        assertThat(sales.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Every bad row is reported with its line, and a real run with errors saves nothing")
    void errorsByLine() {
        String csv = HEADER
                + "A-1,Mew ex,single,1,abc,,,,,,,,,,,,\n"
                + "A-2,Mewtwo promo,single,1,40,2026-05-19,,,,,,2026-05-18,1,75,facebook,,\n"
                + "A-3,ETB,sealed,1,100,,,,,,,2026-05-10,2,250,facebook,,\n"
                + "A-4,Binder,lot,1,10,,,,,,,2026-05-10,1,20,flea market,,\n"
                + ",No ref,single,1,1,,,,,,,,,,,,\n";
        ImportReport report = run(csv, false);

        assertThat(report.saved()).isFalse();
        assertThat(report.errors()).extracting(RowError::line).containsExactly(2, 3, 4, 5, 6);
        assertThat(report.errors().get(0).message()).contains("total_cost_cad must be an amount");
        assertThat(report.errors().get(1).message()).contains("before the purchase date");
        assertThat(report.errors().get(2).message()).contains("sells 2 units but has 1");
        assertThat(report.errors().get(3).message()).contains("sale_channel \"flea market\"");
        assertThat(report.errors().get(4).message()).contains("lot_ref is required");
        assertThat(lots.count()).isZero();
    }

    @Test
    @DisplayName("Missing and unknown columns are reported before any row is read")
    void headerProblems() {
        ImportReport report = run("lot_ref,title,price\nA,Mew,10\n", true);
        assertThat(report.errors()).extracting(RowError::message)
                .contains("Unknown column \"price\".", "Missing column kind.", "Missing column quantity.",
                        "Missing column total_cost_cad.");
    }
}
