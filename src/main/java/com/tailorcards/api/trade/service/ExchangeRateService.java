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
}
