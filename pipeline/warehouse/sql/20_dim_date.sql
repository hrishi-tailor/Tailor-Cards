-- Dimension: one row per calendar day from the earliest purchase, sale or price to today.
CREATE OR REPLACE VIEW dim_date AS
WITH bounds AS (
    SELECT
        least(
            coalesce((SELECT min(acquired_on) FROM stg_lots), current_date),
            coalesce((SELECT min(sold_on) FROM stg_sales), current_date),
            coalesce((SELECT min(price_date) FROM stg_prices), current_date)
        )            AS first_day,
        current_date AS last_day
)
SELECT
    CAST(t.day AS DATE)                                  AS date_key,
    year(t.day)                                          AS year,
    quarter(t.day)                                       AS quarter,
    month(t.day)                                         AS month,
    CAST(date_trunc('month', t.day) AS DATE)             AS month_start,
    day_of_week(t.day)                                   AS day_of_week,
    day_of_week(t.day) IN (6, 7)                         AS is_weekend
FROM bounds
CROSS JOIN UNNEST(sequence(bounds.first_day, bounds.last_day, INTERVAL '1' DAY)) AS t (day)
