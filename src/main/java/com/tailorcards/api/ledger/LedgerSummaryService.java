package com.tailorcards.api.ledger;

import com.tailorcards.api.ledger.dto.LedgerSummary;
import com.tailorcards.api.ledger.dto.LedgerSummary.Line;
import com.tailorcards.api.ledger.dto.LedgerSummary.OnHand;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Totals and breakdowns of realised sales, and the cost of what's still on hand. */
@Service
public class LedgerSummaryService {

    private record Row(LedgerSale sale, SaleView view) {
    }

    private final LedgerService ledger;
    private final LedgerSaleRepository sales;

    public LedgerSummaryService(LedgerService ledger, LedgerSaleRepository sales) {
        this.ledger = ledger;
        this.sales = sales;
    }

    @Transactional(readOnly = true)
    public LedgerSummary summarize(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("'to' can't be before 'from'.");
        }
        List<Row> rows = sales.findAllWithLot().stream()
                .filter(s -> (from == null || !s.getSoldOn().isBefore(from)) && (to == null || !s.getSoldOn().isAfter(to)))
                .map(s -> new Row(s, ledger.view(s)))
                .toList();

        List<Line> byKind = group(rows, r -> r.sale().getLot().getKind().name(),
                Comparator.comparing(Line::grossProfitCad).reversed());
        List<Line> byChannel = group(rows, r -> r.sale().getChannel().name(),
                Comparator.comparing(Line::grossProfitCad).reversed());
        List<Line> byMonth = group(rows, r -> YearMonth.from(r.sale().getSoldOn()).toString(),
                Comparator.comparing(Line::key));

        return new LedgerSummary(from, to, line("TOTAL", rows), byKind, byChannel, byMonth, onHand());
    }

    private OnHand onHand() {
        int lotsLeft = 0;
        int unitsLeft = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (LotView lot : ledger.listLots()) {
            if (lot.unitsLeft() > 0) {
                lotsLeft++;
                unitsLeft += lot.unitsLeft();
                cost = cost.add(lot.totalCostCad().multiply(BigDecimal.valueOf(lot.unitsLeft()))
                        .divide(BigDecimal.valueOf(lot.quantity()), 2, RoundingMode.HALF_UP));
            }
        }
        return new OnHand(lotsLeft, unitsLeft, cost);
    }

    private static List<Line> group(List<Row> rows, Function<Row, String> key, Comparator<Line> order) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        rows.forEach(r -> groups.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r));
        return groups.entrySet().stream().map(e -> line(e.getKey(), e.getValue())).sorted(order).toList();
    }

    private static Line line(String key, List<Row> rows) {
        BigDecimal revenue = sum(rows, SaleView::revenueCad);
        BigDecimal cost = sum(rows, SaleView::costCad);
        BigDecimal profit = revenue.subtract(cost);
        BigDecimal margin = revenue.signum() == 0 ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
        int units = rows.stream().mapToInt(r -> r.view().quantity()).sum();
        return new Line(key, rows.size(), units, revenue, cost, profit, margin,
                median(rows.stream().map(r -> r.view().daysListed()).filter(d -> d != null).sorted().toList()));
    }

    private static BigDecimal sum(List<Row> rows, Function<SaleView, BigDecimal> value) {
        return rows.stream().map(r -> value.apply(r.view())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal median(List<Integer> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? BigDecimal.valueOf(sorted.get(mid))
                : BigDecimal.valueOf(sorted.get(mid - 1) + sorted.get(mid)).divide(BigDecimal.TWO, 1, RoundingMode.HALF_UP);
    }
}
