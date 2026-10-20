-- Mart: revenue, cost and margin by week of sale (weeks start on Monday). Feeds the Monday email.
CREATE OR REPLACE VIEW mart_weekly_pnl AS
SELECT
    CAST(date_trunc('week', f.date_key) AS DATE)                     AS week_start,
    count(*)                                                         AS sales,
    sum(f.units)                                                     AS units,
    round(sum(f.revenue_cad), 2)                                     AS revenue_cad,
    round(sum(f.cost_cad), 2)                                        AS cost_cad,
    round(sum(f.gross_profit_cad), 2)                                AS gross_profit_cad,
    round(100 * sum(f.gross_profit_cad) / nullif(sum(f.revenue_cad), 0), 1) AS margin_pct
FROM fact_sales f
GROUP BY CAST(date_trunc('week', f.date_key) AS DATE)
