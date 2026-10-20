-- Fact: one market price per card per day, the latest fetched that day from any source.
CREATE OR REPLACE VIEW fact_price_daily AS
SELECT card_id, price_date AS date_key, source, price_usd, price_cad
FROM (
    SELECT
        card_id, price_date, source, price_usd, price_cad,
        row_number() OVER (PARTITION BY card_id, price_date ORDER BY fetched_at DESC, snapshot_id DESC) AS rn
    FROM stg_prices
) AS ranked
WHERE rn = 1
