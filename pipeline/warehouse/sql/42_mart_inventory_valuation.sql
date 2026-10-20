-- Mart: stock on hand at cost and at today's market price, per lot. Lots without a card id, or whose card has never
-- been priced, have no market value; price_age_days shows how old the price is.
CREATE OR REPLACE VIEW mart_inventory_valuation AS
WITH sold AS (
    SELECT lot_id, sum(units) AS units_sold
    FROM fact_sales
    GROUP BY lot_id
),
latest_price AS (
    SELECT card_id, date_key AS price_date, price_cad
    FROM (
        SELECT card_id, date_key, price_cad,
               row_number() OVER (PARTITION BY card_id ORDER BY date_key DESC) AS rn
        FROM fact_price_daily
    ) AS ranked
    WHERE rn = 1
)
SELECT
    l.lot_id,
    l.title,
    l.kind_name,
    l.card_id,
    l.quantity - coalesce(s.units_sold, 0)                                   AS units_on_hand,
    l.unit_cost_cad,
    round(l.total_cost_cad * (l.quantity - coalesce(s.units_sold, 0)) / l.quantity, 2) AS cost_on_hand_cad,
    p.price_cad                                                              AS market_price_cad,
    p.price_date,
    date_diff('day', p.price_date, current_date)                             AS price_age_days,
    round(p.price_cad * (l.quantity - coalesce(s.units_sold, 0)), 2)         AS market_value_cad,
    round(p.price_cad * (l.quantity - coalesce(s.units_sold, 0))
          - l.total_cost_cad * (l.quantity - coalesce(s.units_sold, 0)) / l.quantity, 2) AS unrealised_gain_cad
FROM dim_lot l
LEFT JOIN sold s ON s.lot_id = l.lot_id
LEFT JOIN latest_price p ON p.card_id = l.card_id
WHERE l.quantity - coalesce(s.units_sold, 0) > 0
