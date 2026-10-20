-- Mart: what sells where. Profit, margin and the median days from listing to sale by item kind and channel.
CREATE OR REPLACE VIEW mart_kind_channel AS
SELECT
    l.kind_name,
    c.channel_name,
    c.channel_type,
    count(*)                                                         AS sales,
    sum(f.units)                                                     AS units,
    round(sum(f.revenue_cad), 2)                                     AS revenue_cad,
    round(sum(f.gross_profit_cad), 2)                                AS gross_profit_cad,
    round(100 * sum(f.gross_profit_cad) / nullif(sum(f.revenue_cad), 0), 1) AS margin_pct,
    approx_percentile(f.days_listed, 0.5)                            AS median_days_listed
FROM fact_sales f
JOIN dim_lot l ON l.lot_id = f.lot_id
JOIN dim_channel c ON c.channel = f.channel
GROUP BY l.kind_name, c.channel_name, c.channel_type
