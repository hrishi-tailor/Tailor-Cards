package com.tailorcards.api.ledger;

import com.tailorcards.api.common.CsvRows;
import com.tailorcards.api.ledger.dto.ImportReport;
import com.tailorcards.api.ledger.dto.ImportReport.RowError;
import com.tailorcards.api.ledger.dto.LotRequest;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports purchases and sales from a CSV file (format in docs/ledger.md). Each row belongs to a lot through its
 * lot_ref: the lot's columns are read from the first row of that lot_ref, and any row may also carry one sale. A
 * dry run checks every row and reports what would be saved; a real run saves everything in one transaction, or
 * nothing if any row has an error.
 */
@Service
public class LedgerCsvImporter {

    static final int MAX_ROWS = 5_000;
    static final String REF_PREFIX = "csv:";

    private static final Map<String, String> HEADERS = Map.ofEntries(
            Map.entry("lot_ref", "lot_ref"), Map.entry("title", "title"), Map.entry("kind", "kind"),
            Map.entry("quantity", "quantity"), Map.entry("total_cost_cad", "total_cost_cad"),
            Map.entry("acquired_on", "acquired_on"), Map.entry("acquired_channel", "acquired_channel"),
            Map.entry("listed_on", "listed_on"), Map.entry("listed_price_cad", "listed_price_cad"),
            Map.entry("card_ref", "card_ref"), Map.entry("notes", "notes"),
            Map.entry("sold_on", "sold_on"), Map.entry("sold_quantity", "sold_quantity"),
            Map.entry("sale_unit_price_cad", "sale_unit_price_cad"), Map.entry("sale_channel", "sale_channel"),
            Map.entry("payment_method", "payment_method"), Map.entry("sale_notes", "sale_notes"));

    private static final Map<String, ItemKind> KINDS = Map.of(
            "single", ItemKind.SINGLE, "graded", ItemKind.GRADED, "slab", ItemKind.GRADED,
            "sealed", ItemKind.SEALED, "sealed_case", ItemKind.SEALED_CASE, "case", ItemKind.SEALED_CASE,
            "lot", ItemKind.LOT);

    private static final Map<String, Channel> CHANNELS = Map.ofEntries(
            Map.entry("card_show", Channel.CARD_SHOW), Map.entry("show", Channel.CARD_SHOW),
            Map.entry("facebook_marketplace", Channel.FACEBOOK_MARKETPLACE), Map.entry("facebook", Channel.FACEBOOK_MARKETPLACE),
            Map.entry("marketplace", Channel.FACEBOOK_MARKETPLACE), Map.entry("fb", Channel.FACEBOOK_MARKETPLACE),
            Map.entry("instagram", Channel.INSTAGRAM), Map.entry("ebay", Channel.EBAY),
            Map.entry("website", Channel.WEBSITE), Map.entry("tailorcards", Channel.WEBSITE),
            Map.entry("buylist", Channel.BUYLIST), Map.entry("other", Channel.OTHER));

    private static final Map<String, PaymentMethod> PAYMENTS = Map.of(
            "cash", PaymentMethod.CASH, "e_transfer", PaymentMethod.E_TRANSFER, "etransfer", PaymentMethod.E_TRANSFER,
            "interac", PaymentMethod.E_TRANSFER, "card", PaymentMethod.CARD, "paypal", PaymentMethod.PAYPAL,
            "trade", PaymentMethod.TRADE, "other", PaymentMethod.OTHER);

    private record PendingSale(int line, SaleRequest request) {
    }

    private static final class PendingLot {
        final String ref;
        final int line;
        LotRequest request;
        final List<PendingSale> sales = new ArrayList<>();

        PendingLot(String ref, int line) {
            this.ref = ref;
            this.line = line;
        }
    }

    private final LedgerService ledger;
    private final LedgerLotRepository lots;

    public LedgerCsvImporter(LedgerService ledger, LedgerLotRepository lots) {
        this.ledger = ledger;
        this.lots = lots;
    }

    @Transactional
    public ImportReport importCsv(byte[] content, boolean dryRun) {
        List<List<String>> rows = CsvRows.parse(CsvRows.decode(content));
        rows.removeIf(r -> r.stream().allMatch(String::isBlank));
        List<RowError> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            return report(false, 0, List.of(), 0, List.of(new RowError(1, "The file is empty.")));
        }
        Map<String, Integer> columns = columns(rows.getFirst(), errors);
        int dataRows = rows.size() - 1;
        if (dataRows > MAX_ROWS) {
            errors.add(new RowError(1, "A file can have at most " + MAX_ROWS + " rows."));
        }
        if (!errors.isEmpty()) {
            return report(false, dataRows, List.of(), 0, errors);
        }

        Map<String, PendingLot> pending = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            readRow(new Cells(rows.get(i), columns, i + 1, errors), pending);
        }
        LocalDate today = LocalDate.now(LedgerService.STORE_ZONE);
        pending.values().forEach(lot -> checkLot(lot, today, errors));

        List<PendingLot> toCreate = new ArrayList<>();
        int alreadyImported = 0;
        for (PendingLot lot : pending.values()) {
            if (lots.findByExternalRef(REF_PREFIX + lot.ref).isPresent()) {
                alreadyImported++;
            } else {
                toCreate.add(lot);
            }
        }
        errors.sort((a, b) -> Integer.compare(a.line(), b.line()));
        boolean save = !dryRun && errors.isEmpty();
        if (save) {
            for (PendingLot lot : toCreate) {
                LotView saved = ledger.createLot(lot.request, EntrySource.CSV_IMPORT);
                for (PendingSale sale : lot.sales) {
                    ledger.recordSale(saved.id(), sale.request(), EntrySource.CSV_IMPORT);
                }
            }
        }
        return report(save, dataRows, toCreate, alreadyImported, errors);
    }

    private static void readRow(Cells row, Map<String, PendingLot> pending) {
        String ref = row.text("lot_ref", 60);
        if (ref == null) {
            row.error("lot_ref is required.");
            return;
        }
        PendingLot lot = pending.get(ref);
        if (lot == null) {
            lot = new PendingLot(ref, row.line);
            pending.put(ref, lot);
            lot.request = readLot(row, ref);
        }
        if (row.text("sold_on", 20) != null) {
            SaleRequest sale = readSale(row, ref);
            if (sale != null) {
                lot.sales.add(new PendingSale(row.line, sale));
            }
        }
    }

    private static LotRequest readLot(Cells row, String ref) {
        int before = row.errors.size();
        String title = row.text("title", 200);
        if (title == null) {
            row.error("title is required on the first row of lot " + ref + ".");
        }
        ItemKind kind = row.choice("kind", KINDS, true);
        Integer quantity = row.integer("quantity", true);
        BigDecimal cost = row.money("total_cost_cad", true);
        LocalDate acquiredOn = row.date("acquired_on");
        Channel acquiredChannel = row.choice("acquired_channel", CHANNELS, false);
        LocalDate listedOn = row.date("listed_on");
        BigDecimal listedPrice = row.money("listed_price_cad", false);
        if (row.errors.size() > before) {
            return null;
        }
        return new LotRequest(title, kind, row.text("card_ref", 100), quantity, cost, acquiredOn, acquiredChannel,
                listedOn, listedPrice, row.text("notes", 500), REF_PREFIX + ref);
    }

    private static SaleRequest readSale(Cells row, String ref) {
        int before = row.errors.size();
        LocalDate soldOn = row.date("sold_on");
        Integer quantity = row.integer("sold_quantity", true);
        BigDecimal price = row.money("sale_unit_price_cad", true);
        Channel channel = row.choice("sale_channel", CHANNELS, true);
        PaymentMethod payment = row.choice("payment_method", PAYMENTS, false);
        if (row.errors.size() > before) {
            return null;
        }
        return new SaleRequest(soldOn, quantity, price, channel, payment, row.text("sale_notes", 500),
                REF_PREFIX + ref + ":" + row.line);
    }

    /** The checks LedgerService makes on save, made here first so every problem is reported with its line. */
    private static void checkLot(PendingLot lot, LocalDate today, List<RowError> errors) {
        if (lot.request == null) {
            return;
        }
        LotRequest r = lot.request;
        if ((r.acquiredOn() != null && r.acquiredOn().isAfter(today)) || (r.listedOn() != null && r.listedOn().isAfter(today))) {
            errors.add(new RowError(lot.line, "Purchase and listing dates can't be in the future."));
        }
        if (r.acquiredOn() != null && r.listedOn() != null && r.listedOn().isBefore(r.acquiredOn())) {
            errors.add(new RowError(lot.line, "The listing date can't be before the purchase date."));
        }
        int sold = 0;
        for (PendingSale sale : lot.sales) {
            sold += sale.request().quantity();
            if (sale.request().soldOn().isAfter(today)) {
                errors.add(new RowError(sale.line(), "The sale date can't be in the future."));
            }
            if (r.acquiredOn() != null && sale.request().soldOn().isBefore(r.acquiredOn())) {
                errors.add(new RowError(sale.line(), "The sale date can't be before the purchase date."));
            }
        }
        if (sold > r.quantity()) {
            errors.add(new RowError(lot.line, "Lot " + lot.ref + " sells " + sold + " units but has " + r.quantity() + "."));
        }
    }

    private static Map<String, Integer> columns(List<String> header, List<RowError> errors) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            String key = HEADERS.get(name);
            if (key != null) {
                columns.putIfAbsent(key, i);
            } else if (!name.isEmpty()) {
                errors.add(new RowError(1, "Unknown column \"" + header.get(i).trim() + "\"."));
            }
        }
        for (String required : List.of("lot_ref", "title", "kind", "quantity", "total_cost_cad")) {
            if (!columns.containsKey(required)) {
                errors.add(new RowError(1, "Missing column " + required + "."));
            }
        }
        return columns;
    }

    private static ImportReport report(boolean saved, int rows, List<PendingLot> toCreate, int alreadyImported,
                                       List<RowError> errors) {
        int sales = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (PendingLot lot : toCreate) {
            for (PendingSale sale : lot.sales) {
                sales++;
                revenue = revenue.add(sale.request().unitPriceCad().multiply(BigDecimal.valueOf(sale.request().quantity())));
                if (lot.request != null) {
                    cost = cost.add(lot.request.totalCostCad().multiply(BigDecimal.valueOf(sale.request().quantity()))
                            .divide(BigDecimal.valueOf(lot.request.quantity()), 2, RoundingMode.HALF_UP));
                }
            }
        }
        return new ImportReport(saved, rows, toCreate.size(), sales, alreadyImported,
                revenue.setScale(2, RoundingMode.HALF_UP), cost, errors);
    }

    /** One data row, read by column name, collecting errors with the row's line number. */
    private static final class Cells {
        final List<String> values;
        final Map<String, Integer> columns;
        final int line;
        final List<RowError> errors;

        Cells(List<String> values, Map<String, Integer> columns, int line, List<RowError> errors) {
            this.values = values;
            this.columns = columns;
            this.line = line;
            this.errors = errors;
        }

        void error(String message) {
            errors.add(new RowError(line, message));
        }

        String text(String column, int max) {
            Integer index = columns.get(column);
            if (index == null || index >= values.size()) {
                return null;
            }
            String value = values.get(index).replaceAll("\\p{Cntrl}", " ").trim();
            if (value.isEmpty()) {
                return null;
            }
            if (value.length() > max) {
                error(column + " is longer than " + max + " characters.");
                return value.substring(0, max);
            }
            return value;
        }

        Integer integer(String column, boolean required) {
            String value = text(column, 10);
            if (value == null) {
                if (required) {
                    error(column + " is required.");
                }
                return null;
            }
            if (!value.matches("\\d{1,6}") || Integer.parseInt(value) < 1) {
                error(column + " must be a whole number of at least 1 (got \"" + value + "\").");
                return null;
            }
            return Integer.parseInt(value);
        }

        BigDecimal money(String column, boolean required) {
            String value = text(column, 20);
            if (value == null) {
                if (required) {
                    error(column + " is required.");
                }
                return null;
            }
            String plain = value.replaceAll("(?i)^ca\\$|\\$|,", "").trim();
            try {
                BigDecimal amount = new BigDecimal(plain);
                if (amount.signum() < 0 || amount.scale() > 2) {
                    error(column + " must be an amount of at least 0 with at most 2 decimals (got \"" + value + "\").");
                    return null;
                }
                return amount;
            } catch (NumberFormatException e) {
                error(column + " must be an amount (got \"" + value + "\").");
                return null;
            }
        }

        LocalDate date(String column) {
            String value = text(column, 20);
            if (value == null) {
                return null;
            }
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                error(column + " must be a date like 2026-08-16 (got \"" + value + "\").");
                return null;
            }
        }

        <T> T choice(String column, Map<String, T> options, boolean required) {
            String value = text(column, 40);
            if (value == null) {
                if (required) {
                    error(column + " is required.");
                }
                return null;
            }
            T match = options.get(value.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
            if (match == null) {
                error(column + " \"" + value + "\" isn't one of " + String.join(", ", new java.util.TreeSet<>(options.keySet())) + ".");
            }
            return match;
        }
    }
}
