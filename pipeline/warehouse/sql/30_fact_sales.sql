-- Fact: one row per ledger sale. Cost of goods is the lot's cost shared by units (as the app computes it), so a
-- giveaway carries its cost and lowers profit.
CREATE OR REPLACE VIEW fact_sales AS
SELECT
    s.sale_id,
    s.lot_id,
    l.card_id,
    s.sold_on                                                            AS date_key,
    s.channel,
    s.payment_method,
    l.kind,
    s.quantity                                                           AS units,
    round(s.quantity * s.unit_price_cad, 2)                              AS revenue_cad,
    round(l.total_cost_cad * s.quantity / l.quantity, 2)                 AS cost_cad,
    round(s.quantity * s.unit_price_cad, 2)
        - round(l.total_cost_cad * s.quantity / l.quantity, 2)           AS gross_profit_cad,
    date_diff('day', l.listed_on, s.sold_on)                             AS days_listed,
    date_diff('day', l.acquired_on, s.sold_on)                           AS days_held,
    s.unit_price_cad = 0                                                 AS is_giveaway
FROM stg_sales s
JOIN stg_lots l ON l.lot_id = s.lot_id
