package com.tailorcards.api.buylistchat.pricing;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Decides each line's status from configured thresholds. Pure Java; the model never decides scrap.
 * Reasons are customer-safe: they never reveal the thresholds themselves.
 */
@Component
public class ScrapFilter {

    public record Verdict(LineStatus status, String reason) {}

    private final BuylistChatProperties.Scrap config;

    public ScrapFilter(BuylistChatProperties properties) {
        this.config = properties.getScrap();
    }

    public Verdict evaluate(LineFacts line) {
        if (line.bulk()) {
            return line.quantity() >= config.getBulkMinQuantity()
                    ? new Verdict(LineStatus.ELIGIBLE, "Bulk lot")
                    : new Verdict(LineStatus.BELOW_MINIMUM, "Bulk lot is too small for us to take");
        }
        switch (line.resolveState()) {
            case "PENDING":
                return new Verdict(LineStatus.PENDING, "Checking card data");
            case "NOT_FOUND":
                return new Verdict(LineStatus.UNIDENTIFIED, "We couldn't identify this card");
            case "AMBIGUOUS":
                return new Verdict(LineStatus.NEEDS_REVIEW, "Several cards match; we'll confirm which one");
            case "ERROR":
                return new Verdict(LineStatus.NEEDS_REVIEW, "Card data was unavailable; we'll check it by hand");
            default:
                break;
        }
        if (line.ungradedPriceForSlab()) {
            // No market data for this grade (only an ungraded price): priced by hand
            return new Verdict(LineStatus.NEEDS_REVIEW, "Graded cards are priced by hand; the price shown is for an ungraded copy");
        }
        if (line.category() != null && config.getExcludedCategories().stream()
                .anyMatch(excluded -> excluded.equalsIgnoreCase(line.category().trim()))) {
            return new Verdict(LineStatus.BELOW_MINIMUM, "Not something we're buying right now");
        }
        if (isDamaged(line.condition())) {
            return "EXCLUDE".equalsIgnoreCase(config.getDamagedPolicy())
                    ? new Verdict(LineStatus.BELOW_MINIMUM, "We're not taking damaged cards right now")
                    : new Verdict(LineStatus.NEEDS_REVIEW, "Damaged cards need a closer look");
        }
        if (line.unitMarketUsd() == null) {
            return new Verdict(LineStatus.NEEDS_REVIEW, "No market price available; we'll check it by hand");
        }
        if (line.unitMarketUsd().compareTo(config.getMinUnitUsd()) < 0
                || line.lineMarketUsd().compareTo(config.getMinLineUsd()) < 0) {
            return new Verdict(LineStatus.BELOW_MINIMUM, "Below our minimum");
        }
        if (!line.hasPhoto() && photoRequired(line)) {
            // Photos are optional: the line still counts, the estimate is capped until one is added
            return new Verdict(LineStatus.ELIGIBLE, "Photo optional: adding one can raise your estimate");
        }
        return new Verdict(LineStatus.ELIGIBLE, "Looks good");
    }

    /** Lines valuable enough that a photo raises the estimate (photos are never required). */
    public boolean photoRequired(LineFacts line) {
        return !line.bulk() && line.lineMarketUsd() != null
                && line.lineMarketUsd().compareTo(config.getPhotoRequiredAboveUsd()) >= 0;
    }

    static boolean isDamaged(String condition) {
        if (condition == null) {
            return false;
        }
        String c = condition.trim().toUpperCase(Locale.ROOT);
        return c.equals("DMG") || c.equals("DAMAGED") || c.equals("D");
    }
}
