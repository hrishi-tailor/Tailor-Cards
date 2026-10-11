package com.tailorcards.api.buylistchat.pricing;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.trade.service.TradeConfigService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic "estimated approval rating" for a submission: a guess, never a guarantee. Weighted
 * inputs (configurable): eligible value share, identification confidence, photo coverage on
 * high-value lines, liquidity (card_liquidity), bulk share, line count and graded prices resting on
 * few sales. Photos are optional, but without them the estimate is capped (maxWithoutPhotos).
 * The deal then scales it down for requests above our rates or adds a bonus for requests below.
 * Clamped to [min, max]; reasons are customer-safe and say what would raise the estimate.
 */
@Component
public class LikelihoodCalculator {

    public static final String LABEL = "Estimated approval rating, not a guarantee";
    public static final String DISCLAIMER = "This percentage is an estimated guess at how likely we are to accept your "
            + "request. It is not a guaranteed price or a guaranteed acceptance: final offers are confirmed after we "
            + "inspect your cards.";

    public record Result(int percent, List<String> reasons) {}

    /** Scores in [0, 1] (penalties as shares) that the weights turn into points. */
    private record Inputs(double eligibleShare, double identification, double photoCoverage, double liquidity,
                          double bulkShare, double lineLoad, double thinShare, boolean photosMissing) {}

    private final BuylistChatProperties.Likelihood weights;
    private final ScrapFilter scrapFilter;
    private final TradeConfigService tradeConfigService;

    public LikelihoodCalculator(BuylistChatProperties properties, ScrapFilter scrapFilter,
                                TradeConfigService tradeConfigService) {
        this.weights = properties.getLikelihood();
        this.scrapFilter = scrapFilter;
        this.tradeConfigService = tradeConfigService;
    }

    public Result calculate(List<LineFacts> lines, Map<Long, LineStatus> statuses) {
        return calculate(lines, statuses, 1.0, 0.0, null);
    }

    public Result calculate(List<LineFacts> lines, Map<Long, LineStatus> statuses, double dealFactor, String dealReason) {
        return calculate(lines, statuses, dealFactor, 0.0, dealReason);
    }

    /**
     * As {@link #calculate(List, Map)}, scaled by the deal factor (1 when the customer's request fits
     * our rates, lower when it asks for more) plus the deal bonus (points when it asks for less); the
     * deal message becomes the first reason.
     */
    public Result calculate(List<LineFacts> lines, Map<Long, LineStatus> statuses, double dealFactor, double dealBonus,
                            String dealReason) {
        if (lines.isEmpty()) {
            return new Result(weights.getMin(), List.of("Add some cards to see an estimate"));
        }
        Inputs in = inputs(lines, statuses);
        int percent = percent(in, dealFactor, dealBonus);

        // Reasons: the inputs costing the most points, in customer-safe words, with what would help
        int photoGain = percent(new Inputs(in.eligibleShare(), in.identification(), 1, in.liquidity(), in.bulkShare(),
                in.lineLoad(), in.thinShare(), false), dealFactor, dealBonus) - percent;
        int idGain = percent(new Inputs(in.eligibleShare(), 1, in.photoCoverage(), in.liquidity(), in.bulkShare(),
                in.lineLoad(), in.thinShare(), in.photosMissing()), dealFactor, dealBonus) - percent;
        List<Map.Entry<String, Double>> shortfalls = new ArrayList<>(List.of(
                Map.entry("Some items are below what we buy or need a closer look",
                        weights.getEligibleWeight() * (1 - in.eligibleShare())),
                Map.entry("Some cards couldn't be matched exactly: adding the set and card number helps" + gain(idGain),
                        weights.getIdentificationWeight() * (1 - in.identification())),
                Map.entry("Photos are optional, but adding them for your higher-value cards raises your estimate"
                        + gain(photoGain), (double) Math.max(photoGain, 0)),
                Map.entry("Some cards are slower for us to resell", weights.getLiquidityWeight() * (1 - in.liquidity())),
                Map.entry("A large share of the list is bulk", weights.getBulkPenalty() * in.bulkShare()),
                Map.entry("Long lists take longer to review", weights.getLineCountPenalty() * in.lineLoad()),
                Map.entry("Some graded cards have few recent sales, so we'll check their price by hand",
                        weights.getThinGradedDataPenalty() * in.thinShare())));
        List<String> reasons = new ArrayList<>();
        if (dealReason != null) {
            reasons.add(dealReason);
        }
        shortfalls.stream()
                .filter(e -> e.getValue() >= 2.0)
                .sorted(Comparator.comparing((Map.Entry<String, Double> e) -> e.getValue()).reversed())
                .limit(dealReason == null ? 3 : 2)
                .map(Map.Entry::getKey)
                .forEach(reasons::add);
        if (reasons.isEmpty()) {
            reasons.add("Your list is well identified and in demand");
        }
        return new Result(percent, List.copyOf(reasons));
    }

    private static String gain(int points) {
        return points >= 1 ? " (up to +" + points + "%)" : "";
    }

    private int percent(Inputs in, double dealFactor, double dealBonus) {
        double score = weights.getBase()
                + weights.getEligibleWeight() * in.eligibleShare()
                + weights.getIdentificationWeight() * in.identification()
                + weights.getPhotoWeight() * in.photoCoverage()
                + weights.getLiquidityWeight() * in.liquidity()
                - weights.getBulkPenalty() * in.bulkShare()
                - weights.getLineCountPenalty() * in.lineLoad()
                - weights.getThinGradedDataPenalty() * in.thinShare();
        double base = Math.max(weights.getMin(), Math.min(weights.getMax(), score));
        int cap = in.photosMissing() ? Math.min(weights.getMax(), weights.getMaxWithoutPhotos()) : weights.getMax();
        return (int) Math.round(Math.max(weights.getMin(), Math.min(cap, base * dealFactor + dealBonus)));
    }

    private Inputs inputs(List<LineFacts> lines, Map<Long, LineStatus> statuses) {
        double pricedValue = 0;
        double eligibleValue = 0;
        double thinValue = 0;
        double confidenceWeighted = 0;
        double confidenceWeight = 0;
        double liquidityWeighted = 0;
        double liquidityWeight = 0;
        int highValueLines = 0;
        int highValueWithPhoto = 0;
        long totalQuantity = 0;
        long bulkQuantity = 0;

        for (LineFacts line : lines) {
            totalQuantity += line.quantity();
            if (line.bulk()) {
                bulkQuantity += line.quantity();
                continue;
            }
            BigDecimal lineValue = line.lineMarketUsd();
            double value = lineValue == null ? 0 : lineValue.doubleValue();
            pricedValue += value;
            LineStatus status = statuses.get(line.lineId());
            if (status == LineStatus.ELIGIBLE || status == LineStatus.NEEDS_PHOTO) {
                eligibleValue += value;
            }
            if (line.thinGradedData(weights.getMinGradedSales())) {
                thinValue += value;
            }
            double weight = Math.max(value, 1.0); // unpriced lines still count for identification
            double confidence = line.idConfidence() == null ? 0 : line.idConfidence().doubleValue();
            confidenceWeighted += confidence * weight;
            confidenceWeight += weight;
            if (line.cardId() != null && value > 0) {
                double haircut = tradeConfigService.getCardLiquidityHaircut(line.cardId()).doubleValue();
                double liquidity = 1 - Math.min(1, haircut / weights.getMaxLiquidityHaircut());
                liquidityWeighted += liquidity * value;
                liquidityWeight += value;
            }
            if (scrapFilter.photoRequired(line)) {
                highValueLines++;
                if (line.hasPhoto()) {
                    highValueWithPhoto++;
                }
            }
        }

        return new Inputs(
                pricedValue > 0 ? eligibleValue / pricedValue : 0,
                confidenceWeight > 0 ? confidenceWeighted / confidenceWeight : 0,
                highValueLines == 0 ? 1 : (double) highValueWithPhoto / highValueLines,
                liquidityWeight > 0 ? liquidityWeighted / liquidityWeight : 0.5,
                totalQuantity > 0 ? (double) bulkQuantity / totalQuantity : 0,
                Math.min(1, (double) lines.size() / Math.max(1, weights.getLineCountSoftCap())),
                pricedValue > 0 ? thinValue / pricedValue : 0,
                highValueWithPhoto < highValueLines);
    }
}
