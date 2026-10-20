package com.tailorcards.api.ledger;

import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.exception.StockConflictException;
import com.tailorcards.api.ledger.dto.LotRequest;
import com.tailorcards.api.ledger.dto.LotView;
import com.tailorcards.api.ledger.dto.SaleRequest;
import com.tailorcards.api.ledger.dto.SaleView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Records purchases (lots) and the sales drawn from them. Rules: a lot can't sell more units than it has, nothing
 * sells before it was bought or later than today, a lot with sales can't be deleted, and an entry sent again with
 * the same external or client reference returns the existing record instead of a duplicate.
 */
@Service
public class LedgerService {

    /** Dates are the store's local dates: a sale at 11 pm in Toronto belongs to that day. */
    static final ZoneId STORE_ZONE = ZoneId.of("America/Toronto");

    private final LedgerLotRepository lots;
    private final LedgerSaleRepository sales;
    private final Clock clock;

    @Autowired
    public LedgerService(LedgerLotRepository lots, LedgerSaleRepository sales) {
        this(lots, sales, Clock.system(STORE_ZONE));
    }

    LedgerService(LedgerLotRepository lots, LedgerSaleRepository sales, Clock clock) {
        this.lots = lots;
        this.sales = sales;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LotView> listLots() {
        return lots.findAllByOrderByIdDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public LotView getLot(Long id) {
        return view(find(id));
    }

    @Transactional
    public LotView createLot(LotRequest request, EntrySource source) {
        String ref = blankToNull(request.externalRef());
        if (ref != null) {
            var existing = lots.findByExternalRef(ref);
            if (existing.isPresent()) {
                return view(existing.get());
            }
        }
        checkDates(request.acquiredOn(), request.listedOn());
        LedgerLot lot = LedgerLot.builder().source(source).externalRef(ref).build();
        apply(lot, request);
        return view(lots.save(lot));
    }

    @Transactional
    public LotView updateLot(Long id, LotRequest request) {
        LedgerLot lot = lots.findForUpdate(id).orElseThrow(() -> notFound(id));
        int sold = sales.unitsSold(id);
        if (request.quantity() < sold) {
            throw new StockConflictException(sold + " units of this lot are already sold; quantity can't be lower.");
        }
        checkDates(request.acquiredOn(), request.listedOn());
        LocalDate firstSale = sales.findByLotIdOrderBySoldOnAscIdAsc(id).stream()
                .map(LedgerSale::getSoldOn).findFirst().orElse(null);
        if (firstSale != null && request.acquiredOn() != null && request.acquiredOn().isAfter(firstSale)) {
            throw new IllegalArgumentException("The purchase date can't be after the first sale (" + firstSale + ").");
        }
        apply(lot, request);
        return view(lots.save(lot));
    }

    @Transactional
    public void deleteLot(Long id) {
        LedgerLot lot = find(id);
        if (sales.existsByLotId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This lot has sales. Delete its sales first.");
        }
        lots.delete(lot);
    }

    @Transactional
    public SaleView recordSale(Long lotId, SaleRequest request, EntrySource source) {
        String ref = blankToNull(request.clientRef());
        if (ref != null) {
            var existing = sales.findByClientRef(ref);
            if (existing.isPresent()) {
                return view(existing.get());
            }
        }
        // Locked until commit, so a second sale of the same lot waits and then sees this one
        LedgerLot lot = lots.findForUpdate(lotId).orElseThrow(() -> notFound(lotId));
        int left = lot.getQuantity() - sales.unitsSold(lotId);
        if (request.quantity() > left) {
            throw new StockConflictException("Only " + left + " of \"" + lot.getTitle() + "\" left to sell.");
        }
        if (request.soldOn().isAfter(today())) {
            throw new IllegalArgumentException("The sale date can't be in the future.");
        }
        if (lot.getAcquiredOn() != null && request.soldOn().isBefore(lot.getAcquiredOn())) {
            throw new IllegalArgumentException("The sale date can't be before the purchase date (" + lot.getAcquiredOn() + ").");
        }
        LedgerSale sale = LedgerSale.builder()
                .lot(lot)
                .soldOn(request.soldOn())
                .quantity(request.quantity())
                .unitPriceCad(request.unitPriceCad().setScale(2, RoundingMode.HALF_UP))
                .channel(request.channel())
                .paymentMethod(request.paymentMethod())
                .notes(blankToNull(request.notes()))
                .source(source)
                .clientRef(ref)
                .build();
        return view(sales.save(sale));
    }

    @Transactional
    public void deleteSale(Long id) {
        LedgerSale sale = sales.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger sale not found with id: " + id));
        sales.delete(sale);
    }

    /** The lot's cost shared out by units: total cost x units sold / units in the lot, to the cent. */
    static BigDecimal costOf(LedgerLot lot, int units) {
        return lot.getTotalCostCad().multiply(BigDecimal.valueOf(units))
                .divide(BigDecimal.valueOf(lot.getQuantity()), 2, RoundingMode.HALF_UP);
    }

    LotView view(LedgerLot lot) {
        List<SaleView> lotSales = lot.getId() == null ? List.of()
                : sales.findByLotIdOrderBySoldOnAscIdAsc(lot.getId()).stream().map(this::view).toList();
        int sold = lotSales.stream().mapToInt(SaleView::quantity).sum();
        BigDecimal revenue = lotSales.stream().map(SaleView::revenueCad).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = lotSales.stream().map(SaleView::grossProfitCad).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LotView(lot.getId(), lot.getTitle(), lot.getKind(), lot.getCardRef(), lot.getQuantity(), sold,
                lot.getQuantity() - sold, lot.getTotalCostCad(), lot.unitCostCad(), lot.getAcquiredOn(),
                lot.getAcquiredChannel(), lot.getListedOn(), lot.getListedPriceCad(), lot.getNotes(), lot.getSource(),
                revenue, profit, lot.getCreatedAt(), lotSales);
    }

    SaleView view(LedgerSale sale) {
        LedgerLot lot = sale.getLot();
        BigDecimal revenue = sale.revenueCad().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = costOf(lot, sale.getQuantity());
        Integer days = lot.getListedOn() == null ? null
                : (int) ChronoUnit.DAYS.between(lot.getListedOn(), sale.getSoldOn());
        return new SaleView(sale.getId(), lot.getId(), sale.getSoldOn(), sale.getQuantity(), sale.getUnitPriceCad(),
                revenue, cost, revenue.subtract(cost), days, sale.getChannel(), sale.getPaymentMethod(),
                sale.getNotes(), sale.getSource());
    }

    private void apply(LedgerLot lot, LotRequest request) {
        lot.setTitle(request.title().trim());
        lot.setKind(request.kind());
        lot.setCardRef(blankToNull(request.cardRef()));
        lot.setQuantity(request.quantity());
        lot.setTotalCostCad(request.totalCostCad().setScale(2, RoundingMode.HALF_UP));
        lot.setAcquiredOn(request.acquiredOn());
        lot.setAcquiredChannel(request.acquiredChannel());
        lot.setListedOn(request.listedOn());
        lot.setListedPriceCad(request.listedPriceCad() == null ? null
                : request.listedPriceCad().setScale(2, RoundingMode.HALF_UP));
        lot.setNotes(blankToNull(request.notes()));
    }

    private void checkDates(LocalDate acquiredOn, LocalDate listedOn) {
        LocalDate today = today();
        if ((acquiredOn != null && acquiredOn.isAfter(today)) || (listedOn != null && listedOn.isAfter(today))) {
            throw new IllegalArgumentException("Purchase and listing dates can't be in the future.");
        }
        if (acquiredOn != null && listedOn != null && listedOn.isBefore(acquiredOn)) {
            throw new IllegalArgumentException("The listing date can't be before the purchase date.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LedgerLot find(Long id) {
        return lots.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Ledger lot not found with id: " + id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
