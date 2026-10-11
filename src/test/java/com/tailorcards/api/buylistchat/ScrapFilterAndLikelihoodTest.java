package com.tailorcards.api.buylistchat;

import com.tailorcards.api.buylistchat.pricing.LikelihoodCalculator;
import com.tailorcards.api.buylistchat.pricing.LineFacts;
import com.tailorcards.api.buylistchat.pricing.LineStatus;
import com.tailorcards.api.buylistchat.pricing.ScrapFilter;
import com.tailorcards.api.trade.service.TradeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Buylist scrap filter and likelihood meter")
class ScrapFilterAndLikelihoodTest {

    private BuylistChatProperties properties;
    private ScrapFilter filter;
    private LikelihoodCalculator likelihood;

    @BeforeEach
    void setUp() {
        properties = new BuylistChatProperties();
        filter = new ScrapFilter(properties);
        TradeConfigService config = mock(TradeConfigService.class);
        when(config.getCardLiquidityHaircut(anyString())).thenReturn(new BigDecimal("0.0000"));
        likelihood = new LikelihoodCalculator(properties, filter, config);
    }

    static LineFacts card(long id, String state, String price, int qty, boolean photo) {
        BigDecimal confidence = "RESOLVED".equals(state) ? new BigDecimal("0.95")
                : "AMBIGUOUS".equals(state) ? new BigDecimal("0.35") : BigDecimal.ZERO; // as CardResolutionService sets
        return new LineFacts(id, false, state, "Pokemon", "NM", qty, price == null ? null : new BigDecimal(price),
                confidence, photo, "base1-" + id);
    }

    @Test
    @DisplayName("Each status from configured thresholds")
    void statuses() {
        assertThat(filter.evaluate(card(1, "RESOLVED", "12.00", 1, false)).status()).isEqualTo(LineStatus.ELIGIBLE);
        assertThat(filter.evaluate(card(2, "RESOLVED", "0.40", 1, false)).status()).isEqualTo(LineStatus.BELOW_MINIMUM);
        assertThat(filter.evaluate(card(3, "RESOLVED", "2.00", 2, false)).status()).as("line under $5").isEqualTo(LineStatus.BELOW_MINIMUM);
        assertThat(filter.evaluate(card(4, "RESOLVED", "80.00", 1, false)).status()).as("photos are optional").isEqualTo(LineStatus.ELIGIBLE);
        assertThat(filter.evaluate(card(4, "RESOLVED", "80.00", 1, false)).reason()).contains("Photo optional");
        assertThat(filter.evaluate(card(5, "RESOLVED", "80.00", 1, true)).status()).isEqualTo(LineStatus.ELIGIBLE);
        assertThat(filter.evaluate(card(6, "NOT_FOUND", null, 1, false)).status()).isEqualTo(LineStatus.UNIDENTIFIED);
        assertThat(filter.evaluate(card(7, "AMBIGUOUS", "10.00", 1, false)).status()).isEqualTo(LineStatus.NEEDS_REVIEW);
        assertThat(filter.evaluate(card(8, "ERROR", null, 1, false)).status()).isEqualTo(LineStatus.NEEDS_REVIEW);
        assertThat(filter.evaluate(card(9, "PENDING", null, 1, false)).status()).isEqualTo(LineStatus.PENDING);
    }

    @Test
    @DisplayName("Missing price is 'no price' (needs review), never treated as zero")
    void missingPriceIsNotZero() {
        ScrapFilter.Verdict verdict = filter.evaluate(card(1, "RESOLVED", null, 3, false));
        assertThat(verdict.status()).isEqualTo(LineStatus.NEEDS_REVIEW);
        assertThat(verdict.reason()).contains("No market price");
        assertThat(card(1, "RESOLVED", null, 3, false).lineMarketUsd()).isNull();
    }

    @Test
    @DisplayName("Excluded categories, damaged policy and bulk lots are configurable")
    void configurableRules() {
        LineFacts energy = new LineFacts(1, false, "RESOLVED", "Energy", "NM", 10, new BigDecimal("3.00"), BigDecimal.ONE, false, "x-1");
        assertThat(filter.evaluate(energy).status()).isEqualTo(LineStatus.BELOW_MINIMUM);

        LineFacts damaged = new LineFacts(2, false, "RESOLVED", "Pokemon", "DMG", 1, new BigDecimal("30.00"), BigDecimal.ONE, false, "x-2");
        assertThat(filter.evaluate(damaged).status()).isEqualTo(LineStatus.NEEDS_REVIEW);
        properties.getScrap().setDamagedPolicy("EXCLUDE");
        assertThat(filter.evaluate(damaged).status()).isEqualTo(LineStatus.BELOW_MINIMUM);

        LineFacts bigBulk = new LineFacts(3, true, "RESOLVED", null, "UNKNOWN", 500, null, BigDecimal.ONE, false, null);
        LineFacts smallBulk = new LineFacts(4, true, "RESOLVED", null, "UNKNOWN", 20, null, BigDecimal.ONE, false, null);
        assertThat(filter.evaluate(bigBulk).status()).isEqualTo(LineStatus.ELIGIBLE);
        assertThat(filter.evaluate(smallBulk).status()).isEqualTo(LineStatus.BELOW_MINIMUM);

        properties.getScrap().setMinUnitUsd(new BigDecimal("20.00"));
        assertThat(filter.evaluate(card(5, "RESOLVED", "12.00", 1, false)).status()).isEqualTo(LineStatus.BELOW_MINIMUM);
    }

    private LikelihoodCalculator.Result run(List<LineFacts> lines) {
        Map<Long, LineStatus> statuses = new HashMap<>();
        lines.forEach(l -> statuses.put(l.lineId(), filter.evaluate(l).status()));
        return likelihood.calculate(lines, statuses);
    }

    @Test
    @DisplayName("Strong list scores high, weak list low; always clamped 5-95; deterministic")
    void likelihoodRangeAndDeterminism() {
        List<LineFacts> strong = List.of(card(1, "RESOLVED", "40.00", 1, false), card(2, "RESOLVED", "120.00", 1, true));
        List<LineFacts> weak = List.of(card(1, "NOT_FOUND", null, 1, false), card(2, "RESOLVED", "0.10", 1, false),
                new LineFacts(3, true, "RESOLVED", null, "UNKNOWN", 900, null, BigDecimal.ONE, false, null));

        LikelihoodCalculator.Result high = run(strong);
        LikelihoodCalculator.Result low = run(weak);

        assertThat(high.percent()).isBetween(85, 95);
        List<LineFacts> half = List.of(card(1, "RESOLVED", "40.00", 1, false), card(2, "NOT_FOUND", null, 1, false),
                card(3, "RESOLVED", "40.00", 1, false), card(4, "AMBIGUOUS", "40.00", 1, false));
        assertThat(run(half).percent()).isBetween(high.percent() > 90 ? 40 : 30, high.percent() - 10);
        assertThat(low.percent()).isBetween(5, 40);
        assertThat(run(strong)).isEqualTo(high);
        assertThat(low.reasons()).hasSizeBetween(1, 3);
        assertThat(String.join(" ", low.reasons())).doesNotContainPattern("\\$");  // no thresholds leak

        properties.getLikelihood().setBase(500);
        assertThat(run(strong).percent()).isEqualTo(95);
        properties.getLikelihood().setBase(-500);
        assertThat(run(strong).percent()).isEqualTo(5);
    }

    @Test
    @DisplayName("Photos are optional: without them the line still counts but the estimate is capped at 90%")
    void photoCoverage() {
        List<LineFacts> withPhoto = List.of(card(1, "RESOLVED", "200.00", 1, true));
        List<LineFacts> withoutPhoto = List.of(card(1, "RESOLVED", "200.00", 1, false));
        assertThat(run(withoutPhoto).percent()).isLessThan(run(withPhoto).percent());
        assertThat(run(withoutPhoto).percent()).isGreaterThanOrEqualTo(70); // still eligible
        assertThat(run(withoutPhoto).reasons()).anyMatch(r -> r.startsWith("Photos are optional") && r.contains("(up to +"));

        properties.getLikelihood().setBase(500);
        assertThat(run(withoutPhoto).percent()).isEqualTo(90);
        assertThat(run(withPhoto).percent()).isEqualTo(95);
        // Cheap cards never warrant a photo, so no cap
        assertThat(run(List.of(card(2, "RESOLVED", "20.00", 1, false))).percent()).isEqualTo(95);
    }

    @Test
    @DisplayName("Screenshot case: $283.77 NM card, no photo, asking $100 vs a $218.50 offer moves the meter up to the cap")
    void underAskMovesMeter() {
        List<LineFacts> lines = List.of(card(1, "RESOLVED", "283.77", 1, false));
        Map<Long, LineStatus> statuses = Map.of(1L, filter.evaluate(lines.get(0)).status());
        int atRates = likelihood.calculate(lines, statuses, 1.0, 0.0, "Your request fits our rates.").percent();
        LikelihoodCalculator.Result under = likelihood.calculate(lines, statuses, 1.0, 15.0,
                "Your asking price is $118.50 below our cash offer, which helps your chances.");
        assertThat(atRates).isBetween(70, 89);
        assertThat(under.percent()).isGreaterThan(atRates).isEqualTo(90); // capped: no photo
        assertThat(under.reasons().get(0)).contains("below our cash offer");
        assertThat(likelihood.calculate(lines, statuses, 0.5, 0.0, "over").percent()).isLessThan(atRates);
    }

    @Test
    @DisplayName("Graded prices from few recent sales lower the estimate and say why")
    void thinGradedData() {
        LineFacts solid = new LineFacts(1, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("40.00"),
                new BigDecimal("0.95"), false, "swsh9-154", "PSA 10", "GRADED", 5);
        LineFacts thin = new LineFacts(1, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("40.00"),
                new BigDecimal("0.95"), false, "swsh9-154", "PSA 10", "GRADED", 1);
        assertThat(run(List.of(thin)).percent()).isLessThan(run(List.of(solid)).percent());
        assertThat(run(List.of(thin)).reasons()).anyMatch(r -> r.contains("few recent sales"));
    }

    @Test
    @DisplayName("Graded slabs need review: the TCGdex price is for an ungraded copy")
    void gradedNeedsReview() {
        LineFacts slab = new LineFacts(1, false, "RESOLVED", "Pokemon", "UNKNOWN", 1, new BigDecimal("283.77"),
                new BigDecimal("0.95"), true, "swsh9-154", "PSA 10");
        ScrapFilter.Verdict verdict = filter.evaluate(slab);
        assertThat(verdict.status()).isEqualTo(LineStatus.NEEDS_REVIEW);
        assertThat(verdict.reason()).contains("ungraded");
    }
}
