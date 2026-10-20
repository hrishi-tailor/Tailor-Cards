-- Data-quality checks: one row per check, failing_rows = 0 when it passes. "error" means the numbers are wrong
-- (the nightly job reports it and alarms); "warning" means something needs a look.
CREATE OR REPLACE VIEW dq_results AS
WITH sold AS (
    SELECT lot_id, sum(quantity) AS units_sold FROM stg_sales GROUP BY lot_id
),
held AS (
    SELECT DISTINCT l.card_id
    FROM stg_lots l
    LEFT JOIN sold s ON s.lot_id = l.lot_id
    WHERE l.card_id IS NOT NULL AND l.quantity - coalesce(s.units_sold, 0) > 0
),
price_changes AS (
    SELECT card_id, date_key, price_cad,
           lag(price_cad) OVER (PARTITION BY card_id ORDER BY date_key) AS previous_cad
    FROM fact_price_daily
)
SELECT 'oversold_lot' AS check_name, 'error' AS severity,
       'More units sold from a lot than it holds' AS description,
       count(*) AS failing_rows, CAST(min(l.lot_id) AS VARCHAR) AS example
FROM stg_lots l JOIN sold s ON s.lot_id = l.lot_id
WHERE s.units_sold > l.quantity

UNION ALL
SELECT 'orphan_sale', 'error', 'Sale whose lot is missing',
       count(*), CAST(min(s.sale_id) AS VARCHAR)
FROM stg_sales s LEFT JOIN stg_lots l ON l.lot_id = s.lot_id
WHERE l.lot_id IS NULL

UNION ALL
SELECT 'sale_before_purchase', 'error', 'Sale dated before its lot was bought',
       count(*), CAST(min(s.sale_id) AS VARCHAR)
FROM stg_sales s JOIN stg_lots l ON l.lot_id = s.lot_id
WHERE s.sold_on < l.acquired_on

UNION ALL
SELECT 'future_sale', 'error', 'Sale dated after today',
       count(*), CAST(min(sale_id) AS VARCHAR)
FROM stg_sales
WHERE sold_on > current_date

UNION ALL
SELECT 'negative_amount', 'error', 'Negative cost or sale price',
       count(*), CAST(min(id) AS VARCHAR)
FROM (
    SELECT lot_id AS id FROM stg_lots WHERE total_cost_cad < 0
    UNION ALL
    SELECT sale_id FROM stg_sales WHERE unit_price_cad < 0
) AS negatives

UNION ALL
SELECT 'unpriced_snapshot', 'error', 'Price snapshot with no positive CAD price',
       count(*), CAST(min(id) AS VARCHAR)
FROM raw_price_snapshots
WHERE price_cad IS NULL OR price_cad <= 0

UNION ALL
SELECT 'duplicate_snapshot', 'warning', 'Same card, source and fetch time recorded more than once',
       count(*), min(card_id)
FROM (
    SELECT card_id, source, fetched_at
    FROM stg_prices
    GROUP BY card_id, source, fetched_at
    HAVING count(*) > 1
) AS duplicates

UNION ALL
SELECT 'exchange_rate_out_of_range', 'warning', 'CAD/USD ratio of a snapshot outside 1.20-1.60',
       count(*), CAST(min(snapshot_id) AS VARCHAR)
FROM stg_prices
WHERE price_usd > 0 AND (price_cad / price_usd < 1.2 OR price_cad / price_usd > 1.6)

UNION ALL
SELECT 'price_spike', 'warning', 'Card price moved more than 50% from its previous day',
       count(*), min(card_id)
FROM price_changes
WHERE previous_cad > 0 AND abs(price_cad - previous_cad) / previous_cad > 0.5

UNION ALL
SELECT 'stale_held_price', 'warning', 'Card in stock with no price in the last 3 days',
       count(*), min(h.card_id)
FROM held h
LEFT JOIN (SELECT card_id, max(date_key) AS last_priced FROM fact_price_daily GROUP BY card_id) AS p
       ON p.card_id = h.card_id
WHERE p.last_priced IS NULL OR p.last_priced < current_date - INTERVAL '3' DAY
