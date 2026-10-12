package com.tailorcards.api.trade.service;

import java.math.BigDecimal;

public interface ExchangeRateService {

    /**
     * Get the current USD to CAD exchange rate (e.g., 1.3800 CAD per 1 USD).
     */
    BigDecimal getUsdToCadRate();

    /**
     * Convert an amount in USD to CAD using the current exchange rate.
     */
    BigDecimal convertUsdToCad(BigDecimal amountUsd);

    /**
     * Get the current EUR to CAD exchange rate (CAD per 1 EUR), used for Cardmarket prices.
     * Implementations without a live source return a conservative fallback.
     */
    default BigDecimal getEurToCadRate() {
        return new BigDecimal("1.5000");
    }

    /**
     * Convert an amount in EUR to CAD using the current exchange rate.
     */
    default BigDecimal convertEurToCad(BigDecimal amountEur) {
        if (amountEur == null) {
            return null;
        }
        return amountEur.multiply(getEurToCadRate()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Convert an amount in EUR to USD using the EUR/CAD and USD/CAD exchange rates.
     */
    default BigDecimal convertEurToUsd(BigDecimal amountEur) {
        if (amountEur == null) {
            return null;
        }
        BigDecimal usdToCad = getUsdToCadRate();
        if (usdToCad == null || usdToCad.signum() == 0) {
            return null;
        }
        return amountEur.multiply(getEurToCadRate()).divide(usdToCad, 2, java.math.RoundingMode.HALF_UP);
    }
}
