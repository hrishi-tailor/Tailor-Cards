package com.tailorcards.api.ledger;

/** How a sale was paid. TRADE means cards were taken in place of money. */
public enum PaymentMethod {
    CASH,
    E_TRANSFER,
    CARD,
    PAYPAL,
    TRADE,
    OTHER
}
