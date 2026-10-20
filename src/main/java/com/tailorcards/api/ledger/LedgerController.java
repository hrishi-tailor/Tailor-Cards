package com.tailorcards.api.ledger;

import com.tailorcards.api.audit.AuditLog;
import com.tailorcards.api.ledger.dto.ImportReport;
import com.tailorcards.api.ledger.dto.LedgerSummary;
import com.tailorcards.api.ledger.dto.LotRequest;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleRequest;
import com.tailorcards.api.ledger.dto.SaleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** The store's buy and sell ledger (ADMIN only, under /api/admin). Every change is written to the audit log. */
@RestController
@RequestMapping("/api/admin/ledger")
@Tag(name = "Admin - Ledger", description = "Purchases (lots), the sales drawn from them, and their results")
@SecurityRequirement(name = "basicAuth")
public class LedgerController {

    static final long MAX_IMPORT_BYTES = 2 * 1024 * 1024;

    private final LedgerService ledger;
    private final LedgerSummaryService summary;
    private final LedgerCsvImporter importer;
    private final AuditLog audit;

    public LedgerController(LedgerService ledger, LedgerSummaryService summary, LedgerCsvImporter importer,
                            AuditLog audit) {
        this.ledger = ledger;
        this.summary = summary;
        this.importer = importer;
        this.audit = audit;
    }

    @GetMapping("/lots")
    @Operation(summary = "All lots, newest first, each with its sales")
    public List<LotView> lots() {
        return ledger.listLots();
    }

    @GetMapping("/lots/{id}")
    @Operation(summary = "One lot with its sales")
    public LotView lot(@PathVariable Long id) {
        return ledger.getLot(id);
    }

    @PostMapping("/lots")
    @Operation(summary = "Record a purchase", description = "source is MANUAL (default) or SHOW_MODE. A repeated "
            + "externalRef returns the lot already recorded.")
    public ResponseEntity<LotView> createLot(@Valid @RequestBody LotRequest request,
                                             @RequestParam(defaultValue = "MANUAL") EntrySource source) {
        if (source == EntrySource.CSV_IMPORT) {
            throw new IllegalArgumentException("Use the import endpoint for CSV files.");
        }
        LotView created = ledger.createLot(request, source);
        audit.record("LEDGER_LOT_CREATE", "LEDGER_LOT", created.id(), null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/lots/{id}")
    @Operation(summary = "Correct a purchase", description = "Quantity can't go below the units already sold.")
    public LotView updateLot(@PathVariable Long id, @Valid @RequestBody LotRequest request) {
        String before = audit.snapshot(ledger.getLot(id));
        LotView updated = ledger.updateLot(id, request);
        audit.record("LEDGER_LOT_UPDATE", "LEDGER_LOT", id, before, updated);
        return updated;
    }

    @DeleteMapping("/lots/{id}")
    @Operation(summary = "Delete a purchase that has no sales")
    public ResponseEntity<Void> deleteLot(@PathVariable Long id) {
        String before = audit.snapshot(ledger.getLot(id));
        ledger.deleteLot(id);
        audit.record("LEDGER_LOT_DELETE", "LEDGER_LOT", id, before, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lots/{id}/sales")
    @Operation(summary = "Record units sold from a lot", description = "A unit price of 0 records a giveaway. A "
            + "repeated clientRef returns the sale already recorded.")
    public ResponseEntity<SaleView> recordSale(@PathVariable Long id, @Valid @RequestBody SaleRequest request,
                                               @RequestParam(defaultValue = "MANUAL") EntrySource source) {
        if (source == EntrySource.CSV_IMPORT) {
            throw new IllegalArgumentException("Use the import endpoint for CSV files.");
        }
        SaleView sale = ledger.recordSale(id, request, source);
        audit.record("LEDGER_SALE_CREATE", "LEDGER_SALE", sale.id(), null, sale);
        return ResponseEntity.status(HttpStatus.CREATED).body(sale);
    }

    @DeleteMapping("/sales/{id}")
    @Operation(summary = "Delete a sale recorded by mistake")
    public ResponseEntity<Void> deleteSale(@PathVariable Long id) {
        String before = audit.snapshot(ledger.getSale(id));
        ledger.deleteSale(id);
        audit.record("LEDGER_SALE_DELETE", "LEDGER_SALE", id, before, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @Operation(summary = "Import purchases and sales from a CSV file", description = "dryRun (default true) only "
            + "checks the file. A real run saves everything or, if any row has an error, nothing (422). Format in "
            + "docs/ledger.md.")
    public ResponseEntity<ImportReport> importCsv(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Choose a CSV file to import.");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("A ledger CSV can be at most 2 MB.");
        }
        ImportReport report = importer.importCsv(file.getBytes(), dryRun);
        if (report.saved()) {
            audit.record("LEDGER_IMPORT", "LEDGER_IMPORT", null, null, report);
        }
        return ResponseEntity.status(!dryRun && !report.ok() ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK).body(report);
    }

    @GetMapping("/summary")
    @Operation(summary = "Revenue, cost, profit and margin of sales in a date range, with breakdowns")
    public LedgerSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return summary.summarize(from, to);
    }
}
