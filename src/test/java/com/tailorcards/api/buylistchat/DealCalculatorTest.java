package com.tailorcards.api.buylistchat;

import com.tailorcards.api.buylistchat.pricing.DealCalculator;
import com.tailorcards.api.buylistchat.pricing.DealCalculator.DealType;
import com.tailorcards.api.buylistchat.pricing.LineFacts;
import com.tailorcards.api.buylistchat.pricing.LineStatus;
import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.trade.service.TradeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Deal calculator: sell 75% base / trade 80%")
class DealCalculatorTest {

    private DealCalculator calculator;
    private List<BuyRule> rules = List.of(
            rule("PSA10_BGS_BLACK_LABEL", "0.82"), rule("SEALED", "0.70"), rule("RAW_NEAR_MINT", "0.77"), rule("DEFAULT", "0.75"));

    @BeforeEach
    void setUp() {
        TradeConfigService config = mock(TradeConfigService.class);
        when(config.getActiveBuyRules()).thenAnswer(i -> rules);
        when(config.getParameter(TradeConfigService.PARAM_BUYLIST_TRADE_CREDIT_RATE)).thenReturn(new BigDecimal("0.80"));
        calculator = new DealCalculator(config, new BuylistChatProperties());
    }

    static BuyRule rule(String code, String rate) {
        return BuyRule.builder().categoryCode(code).displayName(code).rate(new BigDecimal(rate)).active(true).build();
    }

    // Two eligible lines: LP card $100 (75%) and NM card $200 (77%)
    private final LineFacts lp = new LineFacts(1, false, "RESOLVED", "Pokemon", "LP", 1, new BigDecimal("100.00"), BigDecimal.ONE, true, "a-1");
    private final LineFacts nm = new LineFacts(2, false, "RESOLVED", "Pokemon", "NM", 1, new BigDecimal("200.00"), BigDecimal.ONE, true, "a-2");
    private final Map<Long, LineStatus> eligible = Map.of(1L, LineStatus.ELIGIBLE, 2L, LineStatus.ELIGIBLE);

    private DealCalculator.Result run(DealType type, Map<Long, BigDecimal> asks, String store, int storeCount, String cash) {
        return calculator.calculate(new DealCalculator.Input(type, List.of(lp, nm), eligible, asks,
                store == null ? null : new BigDecimal(store), storeCount, cash == null ? null : new BigDecimal(cash)));
    }

    @Test
    @DisplayName("Offers follow the rules: cash 75/77/82%, trade credit 80%; no ask = exactly at our rates")
    void offers() {
        DealCalculator.Result r = run(DealType.SELL, Map.of(), null, 0, null);
        assertThat(r.cashOfferUsd()).isEqualByComparingTo("229.00");   // 75 + 154
        assertThat(r.tradeCreditUsd()).isEqualByComparingTo("240.00"); // 300 x 0.80
        assertThat(r.askRatio()).isEqualByComparingTo("1.000");
        assertThat(r.withinRules()).isTrue();
        assertThat(r.meterFactor()).isEqualTo(1.0);
        assertThat(r.ratesText()).isEqualTo("We pay 75% of market value in cash (82% for PSA 10 / BGS Black Label, "
                + "77% for near mint), or 80% in store credit toward cards in our shop.");
    }

    @Test
    @DisplayName("Sell: asking above the cash offer lowers the meter; asking below adds a bonus")
    void sellAsks() {
        DealCalculator.Result over = run(DealType.SELL, Map.of(1L, new BigDecimal("100"), 2L, new BigDecimal("180")), null, 0, null);
        assertThat(over.askTotalUsd()).isEqualByComparingTo("280.00");
        assertThat(over.askRatio()).isEqualByComparingTo("1.223");
        assertThat(over.withinRules()).isFalse();
        assertThat(over.overByUsd()).isEqualByComparingTo("51.00");
        assertThat(over.meterFactor()).isBetween(0.5, 0.6);
        assertThat(over.message()).contains("$51.00 above our cash offer");

        assertThat(over.meterBonus()).isZero();

        DealCalculator.Result under = run(DealType.SELL, Map.of(1L, new BigDecimal("70")), null, 0, null);
        assertThat(under.withinRules()).isTrue();
        assertThat(under.meterFactor()).isEqualTo(1.0);
        assertThat(under.meterBonus()).isBetween(1.0, 3.0); // 2% under: a small bonus
        assertThat(under.message()).isEqualTo("Your asking price is $5.00 below our cash offer, which helps your chances.");

        DealCalculator.Result exact = run(DealType.SELL, Map.of(), null, 0, null);
        assertThat(exact.meterBonus()).isZero();
        assertThat(exact.message()).isEqualTo("Your request fits our rates.");
    }

    @Test
    @DisplayName("Customer messages show CAD when a CAD-per-USD rate is given")
    void cadMessages() {
        DealCalculator.Result over = calculator.calculate(new DealCalculator.Input(DealType.SELL, List.of(lp, nm), eligible,
                Map.of(1L, new BigDecimal("100"), 2L, new BigDecimal("180")), null, 0, null, new BigDecimal("1.40")));
        assertThat(over.message()).isEqualTo("Your asking price is $71.40 CAD above our cash offer."); // $51 USD x 1.40
        assertThat(over.overByUsd()).isEqualByComparingTo("51.00"); // amounts stay USD
    }

    @Test
    @DisplayName("Under-ask bonus grows linearly to the configured maximum at the configured span")
    void underAskBonus() {
        // $100 + $50 asked against a $229 offer: 34% under, past the 30% span
        DealCalculator.Result far = run(DealType.SELL, Map.of(1L, new BigDecimal("50"), 2L, new BigDecimal("100")), null, 0, null);
        assertThat(far.meterBonus()).isEqualTo(15.0);
        assertThat(calculator.meterBonus(0.85)).isCloseTo(7.5, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(calculator.meterBonus(1.0)).isZero();
        DealCalculator.Result trade = run(DealType.TRADE, Map.of(), "200.00", 1, null);
        assertThat(trade.meterBonus()).isGreaterThan(0);
        assertThat(trade.message()).contains("$40.00 under your trade credit");
    }

    @Test
    @DisplayName("Trade: shop cards within 80% credit fit; over it shows how much over")
    void trade() {
        assertThat(run(DealType.TRADE, Map.of(), "240.00", 1, null).withinRules()).isTrue();
        DealCalculator.Result over = run(DealType.TRADE, Map.of(), "300.00", 2, null);
        assertThat(over.askRatio()).isEqualByComparingTo("1.250");
        assertThat(over.overByUsd()).isEqualByComparingTo("60.00");
        assertThat(over.message()).contains("$60.00 over your trade credit");
        DealCalculator.Result none = run(DealType.TRADE, Map.of(), "0", 0, null);
        assertThat(none.needsStoreCards()).isTrue();
        assertThat(none.askRatio()).isNull();
    }

    @Test
    @DisplayName("Partial: half the credit in shop cards plus fair cash for the rest is exactly at our rates")
    void partial() {
        DealCalculator.Result fair = run(DealType.PARTIAL, Map.of(), "120.00", 1, null);
        assertThat(fair.requestedCashUsd()).isEqualByComparingTo("114.50"); // half of 229
        assertThat(fair.askRatio()).isEqualByComparingTo("1.000");
        DealCalculator.Result greedy = run(DealType.PARTIAL, Map.of(), "120.00", 1, "200.00");
        assertThat(greedy.askRatio().doubleValue()).isGreaterThan(1.3);
        assertThat(greedy.message()).contains("above our rates");
    }

    @Test
    @DisplayName("Graded PSA 10 with a graded price uses 82%; an ungraded-priced slab is left out")
    void graded() {
        LineFacts psa10 = new LineFacts(3, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("3000.00"),
                BigDecimal.ONE, true, "me02-125", "PSA 10", "GRADED");
        LineFacts rawPriced = new LineFacts(4, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("661.14"),
                BigDecimal.ONE, true, "me02-125", "PSA 10", "RAW");
        DealCalculator.Result r = calculator.calculate(new DealCalculator.Input(DealType.SELL, List.of(psa10, rawPriced),
                Map.of(3L, LineStatus.ELIGIBLE, 4L, LineStatus.NEEDS_REVIEW), Map.of(), null, 0, null));
        assertThat(r.cashOfferUsd()).isEqualByComparingTo("2460.00");
        assertThat(r.offerableMarketUsd()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("Other grades use a GRADED_<COMPANY>_<GRADE> rule when one exists, otherwise the base rate")
    void gradedTiers() {
        rules = List.of(rule("PSA10_BGS_BLACK_LABEL", "0.82"), rule("GRADED_PSA_9", "0.78"), rule("GRADED_CGC_10", "0.80"),
                rule("RAW_NEAR_MINT", "0.77"), rule("DEFAULT", "0.75"));
        LineFacts psa9 = new LineFacts(5, false, "RESOLVED", "Pokemon", "UNKNOWN", 2, new BigDecimal("100.00"),
                BigDecimal.ONE, false, "x-1", "PSA 9", "GRADED");
        LineFacts cgc10 = new LineFacts(6, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("100.00"),
                BigDecimal.ONE, false, "x-2", "CGC 10", "GRADED");
        LineFacts bgs95 = new LineFacts(7, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("100.00"),
                BigDecimal.ONE, false, "x-3", "BGS 9.5", "GRADED");
        assertThat(calculator.unitCashOffer(psa9, LineStatus.ELIGIBLE, rules)).isEqualByComparingTo("78.00");
        assertThat(calculator.unitCashOffer(cgc10, LineStatus.ELIGIBLE, rules)).isEqualByComparingTo("80.00");
        assertThat(calculator.unitCashOffer(bgs95, LineStatus.ELIGIBLE, rules)).isEqualByComparingTo("75.00");
        assertThat(calculator.unitCashOffer(nm, LineStatus.ELIGIBLE, rules)).isEqualByComparingTo("154.00");
        assertThat(calculator.unitCashOffer(nm, LineStatus.NEEDS_REVIEW, rules)).isNull();
        assertThat(calculator.calculate(new DealCalculator.Input(DealType.SELL, List.of(psa9), Map.of(5L, LineStatus.ELIGIBLE),
                Map.of(), null, 0, null)).ratesText()).contains("78% for PSA 9", "80% for CGC 10");
    }

    @Test
    @DisplayName("Meter factor falls to the floor as the ask goes far over the rules")
    void meterFactor() {
        assertThat(run(DealType.SELL, Map.of(1L, new BigDecimal("1000"), 2L, new BigDecimal("1000")), null, 0, null)
                .meterFactor()).isEqualTo(0.15);
    }
}
