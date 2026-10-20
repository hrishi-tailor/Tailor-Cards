-- Dashboard extract: every sale with its names and calendar fields already joined, so Tableau needs no joins.
CREATE OR REPLACE VIEW dash_sales AS
SELECT
    f.sale_id,
    f.date_key                                                       AS sold_on,
    d.month_start,
    CAST(date_trunc('week', f.date_key) AS DATE)                     AS week_start,
    d.year,
    d.quarter,
    l.lot_id,
    l.title                                                          AS item,
    l.kind_name,
    coalesce(c.card_name, l.title)                                   AS card_name,
    ch.channel_name,
    ch.channel_type,
    f.payment_method,
    f.units,
    f.revenue_cad,
    f.cost_cad,
    f.gross_profit_cad,
    f.days_listed,
    f.days_held,
    f.is_giveaway
FROM fact_sales f
JOIN dim_date d ON d.date_key = f.date_key
JOIN dim_lot l ON l.lot_id = f.lot_id
JOIN dim_channel ch ON ch.channel = f.channel
LEFT JOIN dim_card c ON c.card_id = f.card_id
