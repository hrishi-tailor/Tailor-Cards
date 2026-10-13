package com.tailorcards.api.buylistchat.resolution;

import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.service.ExchangeRateService;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Raw card market price in USD: TCGplayer market when TCGdex has one, else the Cardmarket 30-day average (or trend)
 * converted from EUR. Some cards, Trainer Gallery prints among them, only have Cardmarket prices.
 */
public final class CardPricing {

    public static final String CARDMARKET = "CARDMARKET";
    private static final BigDecimal FALLBACK_EUR_CAD = new BigDecimal("1.5000");
    private static final BigDecimal FALLBACK_USD_CAD = new BigDecimal("1.3800");

    private CardPricing() {
    }

    /** The Cardmarket EUR price used for the fallback: the 30-day average, else the trend; null when neither. */
    public static BigDecimal cardmarketEur(CardMarketPrice card) {
        return card.eurAvg30() != null ? card.eurAvg30() : card.eurTrend();
    }

    /** Cardmarket price converted to USD; null when Cardmarket has no price either. */
    public static BigDecimal cardmarketUsd(CardMarketPrice card, ExchangeRateService exchangeRates) {
        BigDecimal eur = cardmarketEur(card);
        if (eur == null || eur.signum() <= 0) {
            return null;
        }
        BigDecimal usd = exchangeRates != null ? exchangeRates.convertEurToUsd(eur) : null;
        return usd != null ? usd : eur.multiply(FALLBACK_EUR_CAD).divide(FALLBACK_USD_CAD, 2, RoundingMode.HALF_UP);
    }

    /** TCGplayer market price, else the converted Cardmarket price. */
    public static BigDecimal marketUsd(CardMarketPrice card, ExchangeRateService exchangeRates) {
        return card.marketPriceUsd() != null ? card.marketPriceUsd() : cardmarketUsd(card, exchangeRates);
    }
}
