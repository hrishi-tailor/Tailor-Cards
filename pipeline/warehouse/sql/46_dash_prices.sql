-- Dashboard extract: daily price history of every card the store holds or lists, with its name.
CREATE OR REPLACE VIEW dash_prices AS
SELECT
    p.date_key                                                       AS price_date,
    p.card_id,
    c.card_name,
    c.in_ledger,
    c.listed_on_site,
    p.source,
    p.price_usd,
    p.price_cad
FROM fact_price_daily p
JOIN dim_card c ON c.card_id = p.card_id
WHERE c.in_ledger OR c.listed_on_site
