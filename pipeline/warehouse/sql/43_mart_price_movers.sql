-- Mart: how each card in the ledger or on the site's price moved over the last 7 days (latest price against the latest price on
-- or before 7 days earlier).
CREATE OR REPLACE VIEW mart_price_movers AS
WITH latest AS (
    SELECT card_id, date_key, price_cad
    FROM (
        SELECT card_id, date_key, price_cad,
               row_number() OVER (PARTITION BY card_id ORDER BY date_key DESC) AS rn
        FROM fact_price_daily
    ) AS ranked
    WHERE rn = 1
),
week_ago AS (
    SELECT card_id, date_key, price_cad
    FROM (
        SELECT f.card_id, f.date_key, f.price_cad,
               row_number() OVER (PARTITION BY f.card_id ORDER BY f.date_key DESC) AS rn
        FROM fact_price_daily f
        JOIN latest l ON l.card_id = f.card_id
        WHERE f.date_key <= l.date_key - INTERVAL '7' DAY
    ) AS ranked
    WHERE rn = 1
)
SELECT
    c.card_id,
    c.card_name,
    c.in_ledger,
    c.listed_on_site,
    l.date_key                                                         AS price_date,
    l.price_cad,
    w.date_key                                                         AS compared_with,
    w.price_cad                                                        AS previous_price_cad,
    round(l.price_cad - w.price_cad, 2)                                AS change_cad,
    round(100 * (l.price_cad - w.price_cad) / nullif(w.price_cad, 0), 1) AS change_pct
FROM latest l
JOIN dim_card c ON c.card_id = l.card_id
LEFT JOIN week_ago w ON w.card_id = l.card_id
WHERE c.in_ledger OR c.listed_on_site
