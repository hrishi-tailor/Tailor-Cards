package com.tailorcards.api.buylistchat.pricing;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.trade.service.TradeConfigService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic deal math for SELL / TRADE / PARTIAL buylists, in USD market terms:
 * cash offer = market x buy-rule rate per line (75% base, editable buy rules), trade credit =
 * market x the buylist trade rate (80%). The customer's request is compared with these as an
 * ask ratio (1.0 = exactly at our rates); above 1 the likelihood meter is scaled down, below 1 it
 * gets a bonus (asking for less than we would pay makes approval more likely).
 */
@Component
public class DealCalculator {

    public enum DealType { SELL, TRADE, PARTIAL }

    /** @param displayRate CAD per USD for amounts in customer messages; null shows USD */
    public record Input(DealType type, List<LineFacts> lines, Map<Long, LineStatus> statuses,
                        Map<Long, BigDecimal> requestedUnitUsd, BigDecimal storeTotalUsd, int storeCardCount,
                        BigDecimal requestedCashUsd, BigDecimal displayRate) {
        public Input(DealType type, List<LineFacts> lines, Map<Long, LineStatus> statuses,
                     Map<Long, BigDecimal> requestedUnitUsd, BigDecimal storeTotalUsd, int storeCardCount,
                     BigDecimal requestedCashUsd) {
            this(type, lines, statuses, requestedUnitUsd, storeTotalUsd, storeCardCount, requestedCashUsd, null);
        }
    }

    /**
     * @param askRatio customer's request / what our rules allow; null when there is nothing to compare
     * @param meterFactor multiplier for the likelihood (1 within rules, down to the configured floor)
     * @param meterBonus points added to the likelihood when the request is below our rates (0 otherwise)
     * @param overByUsd how far a SELL ask or TRADE pick exceeds our offer/credit, when it does
     */
    public record Result(DealType type, BigDecimal offerableMarketUsd, BigDecimal cashOfferUsd, BigDecimal tradeCreditUsd,
                         BigDecimal storeTotalUsd, BigDecimal requestedCashUsd, BigDecimal askTotalUsd,
                         BigDecimal askRatio, boolean withinRules, double meterFactor, double meterBonus, String message,
                         boolean needsStoreCards, BigDecimal overByUsd, String ratesText,
                         BigDecimal baseCashRate, BigDecimal tradeRate) {}

    private final TradeConfigService tradeConfigService;
    private final BuylistChatProperties.Likelihood weights;

    public DealCalculator(TradeConfigService tradeConfigService, BuylistChatProperties properties) {
        this.tradeConfigService = tradeConfigService;
        this.weights = properties.getLikelihood();
    }

    public Result calculate(Input in) {
        List<BuyRule> rules = tradeConfigService.getActiveBuyRules();
        BigDecimal tradeRate = tradeConfigService.getParameter(TradeConfigService.PARAM_BUYLIST_TRADE_CREDIT_RATE);
        BigDecimal baseRate = rate(rules, "DEFAULT");
        String ratesText = ratesText(rules, tradeRate);

        BigDecimal market = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal askSell = BigDecimal.ZERO;
        for (LineFacts line : in.lines()) {
            if (!offerable(line, in.statuses().get(line.lineId()))) {
                continue;
            }
            BigDecimal lineMarket = line.lineMarketUsd();
            BigDecimal lineRate = rate(rules, categoryFor(line));
            BigDecimal lineCash = lineMarket.multiply(lineRate);
            market = market.add(lineMarket);
            cash = cash.add(lineCash);
            credit = credit.add(lineMarket.multiply(creditRate(line, lineRate, tradeRate, baseRate)));
            BigDecimal requested = in.requestedUnitUsd().get(line.lineId());
            askSell = askSell.add(requested != null && requested.signum() > 0
                    ? requested.multiply(BigDecimal.valueOf(line.quantity())) : lineCash);
        }
        BigDecimal store = in.storeTotalUsd() == null ? BigDecimal.ZERO : in.storeTotalUsd();
        DealType type = in.type() == null ? DealType.SELL : in.type();
        boolean needsStore = type != DealType.SELL && in.storeCardCount() == 0;

        BigDecimal requestedCash = null;
        BigDecimal askTotal = null;
        BigDecimal ratio = null;
        BigDecimal overBy = null;
        String message;
        if (cash.signum() == 0) {
            message = "Add cards we can price to see what we can offer.";
        } else {
            switch (type) {
                case TRADE -> {
                    askTotal = store;
                    ratio = store.divide(credit, 3, RoundingMode.HALF_UP);
                    overBy = store.subtract(credit);
                }
                case PARTIAL -> {
                    // Cash on top of the shop cards; when not given, assume the fair remainder
                    BigDecimal shareUsedByTrade = store.divide(credit, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
                    requestedCash = in.requestedCashUsd() != null ? in.requestedCashUsd()
                            : cash.multiply(BigDecimal.ONE.subtract(shareUsedByTrade)).max(BigDecimal.ZERO);
                    askTotal = store.add(requestedCash);
                    ratio = store.divide(credit, 6, RoundingMode.HALF_UP)
                            .add(requestedCash.divide(cash, 6, RoundingMode.HALF_UP)).setScale(3, RoundingMode.HALF_UP);
                }
                default -> {
                    askTotal = askSell;
                    ratio = askSell.divide(cash, 3, RoundingMode.HALF_UP);
                    overBy = askSell.subtract(cash);
                }
            }
            if (needsStore) {
                message = "Pick cards from our shop for your trade.";
                ratio = null;
            } else if (ratio.compareTo(new BigDecimal("0.995")) < 0) {
                BigDecimal under = askTotal == null ? BigDecimal.ZERO : (type == DealType.TRADE ? credit : cash).subtract(askTotal);
                message = switch (type) {
                    case TRADE -> "Your shop picks are " + money(under, in.displayRate()) + " under your trade credit, which helps your chances.";
                    case PARTIAL -> "Your request is below our rates, which helps your chances.";
                    default -> "Your asking price is " + money(under, in.displayRate()) + " below our cash offer, which helps your chances.";
                };
            } else if (ratio.compareTo(new BigDecimal("1.005")) <= 0) {
                message = "Your request fits our rates.";
            } else {
                int pct = ratio.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
                message = switch (type) {
                    case TRADE -> "Your shop picks are " + money(overBy, in.displayRate()) + " over your trade credit.";
                    case PARTIAL -> "Your request is about " + pct + "% above our rates.";
                    default -> "Your asking price is " + money(overBy, in.displayRate()) + " above our cash offer.";
                };
            }
        }
        boolean within = ratio != null && ratio.compareTo(new BigDecimal("1.005")) <= 0;
        double factor = ratio == null ? 1.0 : meterFactor(ratio.doubleValue());
        double bonus = ratio == null ? 0.0 : meterBonus(ratio.doubleValue());
        return new Result(type, scale(market), scale(cash), scale(credit), scale(store), scale(requestedCash),
                scale(askTotal), ratio, within, factor, bonus, message, needsStore,
                overBy != null && overBy.signum() > 0 ? scale(overBy) : null, ratesText, baseRate, tradeRate);
    }

    /** 1.0 at or under our rates; falls linearly to the floor as the ask goes over by the configured span. */
    double meterFactor(double ratio) {
        if (ratio <= 1.005) {
            return 1.0;
        }
        double factor = 1.0 - (ratio - 1.0) / Math.max(0.01, weights.getOverAskSpan());
        return Math.max(weights.getOverAskFloor(), factor);
    }

    /** 0 at or over our rates; rises linearly to the configured bonus as the ask goes under by the configured span. */
    public double meterBonus(double ratio) {
        if (ratio >= 0.995) {
            return 0.0;
        }
        return weights.getUnderAskBonus() * Math.min(1.0, (1.0 - ratio) / Math.max(0.01, weights.getUnderAskSpan()));
    }

    /** What we would pay in cash for one copy of this line at our rates; null when we would not make an offer on it. */
    public BigDecimal unitCashOffer(LineFacts line, LineStatus status, List<BuyRule> rules) {
        if (!offerable(line, status)) {
            return null;
        }
        return scale(line.unitMarketUsd().multiply(rate(rules, categoryFor(line))));
    }

    public List<BuyRule> activeRules() {
        return tradeConfigService.getActiveBuyRules();
    }

    /** Lines we would make an offer on: eligible or only missing a photo, priced, not an unpriced slab. */
    static boolean offerable(LineFacts line, LineStatus status) {
        return !line.bulk() && line.unitMarketUsd() != null && !line.ungradedPriceForSlab()
                && (status == LineStatus.ELIGIBLE || status == LineStatus.NEEDS_PHOTO);
    }

    /** Raw played-condition tiers, each with its own buy rule. */
    private static final Map<String, String> CONDITION_CATEGORIES = Map.of(
            "LP+", "RAW_LP_PLUS", "LP", "RAW_LP", "MP", "RAW_MP", "HP", "RAW_HP", "DMG", "RAW_DMG");

    /**
     * Store-credit rate for a line: the trade rate (80%), except played and damaged cards, which get their
     * cash rate plus the same trade bonus (trade rate minus base cash rate), capped at the trade rate.
     */
    static BigDecimal creditRate(LineFacts line, BigDecimal cashRate, BigDecimal tradeRate, BigDecimal baseRate) {
        if (!CONDITION_CATEGORIES.containsValue(categoryFor(line))) {
            return tradeRate;
        }
        BigDecimal bonus = tradeRate.subtract(baseRate).max(BigDecimal.ZERO);
        return cashRate.add(bonus).min(tradeRate);
    }

    /**
     * Buy-rule category: PSA 10 / Black Label, GRADED_<COMPANY>_<GRADE> (e.g. GRADED_PSA_9) for other slabs,
     * sealed, near mint, the played tiers (LP+, LP, MP, HP, damaged), or DEFAULT for unknown condition.
     */
    static String categoryFor(LineFacts line) {
        if (line.graded()) {
            String g = line.grading().toUpperCase(Locale.ROOT).trim();
            return g.equals("PSA 10") || g.contains("BLACK LABEL") ? "PSA10_BGS_BLACK_LABEL"
                    : "GRADED_" + g.replaceAll("[^A-Z0-9]+", "_");
        }
        if (line.sealed()) {
            return "SEALED";
        }
        String condition = line.condition() == null ? "" : line.condition().toUpperCase(Locale.ROOT);
        if ("NM".equals(condition)) {
            return "RAW_NEAR_MINT";
        }
        return CONDITION_CATEGORIES.getOrDefault(condition, "DEFAULT");
    }

    private static BigDecimal rate(List<BuyRule> rules, String category) {
        return rules.stream().filter(r -> category.equalsIgnoreCase(r.getCategoryCode())).map(BuyRule::getRate).findFirst()
                .orElseGet(() -> rules.stream().filter(r -> "DEFAULT".equalsIgnoreCase(r.getCategoryCode()))
                        .map(BuyRule::getRate).findFirst().orElse(new BigDecimal("0.75")));
    }

    /** Customer-facing summary of the rates, built from the live buy rules and trade rate. */
    static String ratesText(List<BuyRule> rules, BigDecimal tradeRate) {
        BigDecimal base = rate(rules, "DEFAULT");
        List<String> extras = new ArrayList<>();
        // Highest rate first, so the text reads the same however the rules are ordered
        List<BuyRule> sorted = rules.stream().sorted(java.util.Comparator.comparing(BuyRule::getRate).reversed()).toList();
        for (BuyRule rule : sorted) {
            if ("DEFAULT".equalsIgnoreCase(rule.getCategoryCode()) || rule.getRate().compareTo(base) == 0) {
                continue;
            }
            String label = switch (rule.getCategoryCode().toUpperCase(Locale.ROOT)) {
                case "RAW_NEAR_MINT" -> "near mint";
                case "SEALED" -> "sealed product";
                case "RAW_LP_PLUS" -> "LP+ (one white dot)";
                case "RAW_LP" -> "lightly played";
                case "RAW_MP" -> "moderately played";
                case "RAW_HP" -> "heavily played";
                case "RAW_DMG" -> "damaged";
                case "PSA10_BGS_BLACK_LABEL" -> "PSA 10 / BGS Black Label";
                default -> rule.getCategoryCode().toUpperCase(Locale.ROOT).startsWith("GRADED_")
                        // GRADED_PSA_9 -> PSA 9, GRADED_BGS_9_5 -> BGS 9.5
                        ? rule.getCategoryCode().substring(7).toUpperCase(Locale.ROOT).replaceFirst("_", " ").replace('_', '.')
                        : rule.getDisplayName();
            };
            extras.add(pct(rule.getRate()) + " for " + label);
        }
        boolean playedTiers = rules.stream().anyMatch(r -> CONDITION_CATEGORIES.containsValue(r.getCategoryCode().toUpperCase(Locale.ROOT)));
        BigDecimal bonus = tradeRate.subtract(base).max(BigDecimal.ZERO);
        return "We pay " + pct(base) + " of market value in cash" + (extras.isEmpty() ? "" : " (" + String.join(", ", extras) + ")")
                + ", or " + pct(tradeRate) + " in store credit toward cards in our shop"
                + (playedTiers && bonus.signum() > 0 ? " (played and damaged cards: their cash rate plus " + pct(bonus) + ")" : "")
                + ".";
    }

    private static String pct(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    /** "$12.34" in USD, or "$16.97 CAD" when a CAD-per-USD rate is given (customers see CAD). */
    static String money(BigDecimal usd, BigDecimal cadPerUsd) {
        if (cadPerUsd != null && cadPerUsd.signum() > 0) {
            return "$" + usd.multiply(cadPerUsd).setScale(2, RoundingMode.HALF_UP).toPlainString() + " CAD";
        }
        return "$" + usd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
