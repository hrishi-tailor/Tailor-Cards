-- Mart: revenue, cost and margin by month of sale.
CREATE OR REPLACE VIEW mart_monthly_pnl AS
SELECT
    d.month_start,
    count(*)                                                         AS sales,
    sum(f.units)                                                     AS units,
    round(sum(f.revenue_cad), 2)                                     AS revenue_cad,
    round(sum(f.cost_cad), 2)                                        AS cost_cad,
    round(sum(f.gross_profit_cad), 2)                                AS gross_profit_cad,
    round(100 * sum(f.gross_profit_cad) / nullif(sum(f.revenue_cad), 0), 1) AS margin_pct,
    sum(CASE WHEN f.is_giveaway THEN f.units ELSE 0 END)             AS units_given_away
FROM fact_sales f
JOIN dim_date d ON d.date_key = f.date_key
GROUP BY d.month_start
