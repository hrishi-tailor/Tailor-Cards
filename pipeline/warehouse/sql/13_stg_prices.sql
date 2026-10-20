-- Staging: market price snapshots with a usable price. price_date is the UTC date of the fetch (the nightly run
-- is at 3 AM Toronto, 7-8 AM UTC, so it is the same calendar day).
CREATE OR REPLACE VIEW stg_prices AS
SELECT
    id                                                  AS snapshot_id,
    trim(card_id)                                       AS card_id,
    CAST(price_usd AS DOUBLE)                           AS price_usd,
    CAST(price_cad AS DOUBLE)                           AS price_cad,
    source,
    -- Athena views can't hold a time-zone type, so the UTC instant is kept as a plain timestamp
    CAST(from_iso8601_timestamp(fetched_at) AS TIMESTAMP)  AS fetched_at,
    CAST(from_iso8601_timestamp(fetched_at) AS DATE)       AS price_date
FROM raw_price_snapshots
WHERE price_cad > 0
