-- Staging: ledger sales, cleaned. One row per sale (units of one lot at one price on one day).
CREATE OR REPLACE VIEW stg_sales AS
SELECT
    id                              AS sale_id,
    lot_id,
    sold_on,
    quantity,
    CAST(unit_price_cad AS DOUBLE)  AS unit_price_cad,
    channel,
    payment_method,
    source
FROM raw_ledger_sales
