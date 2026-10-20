-- Staging: ledger purchases, cleaned. One row per lot.
CREATE OR REPLACE VIEW stg_lots AS
SELECT
    id                                   AS lot_id,
    trim(title)                          AS title,
    upper(kind)                          AS kind,
    nullif(trim(card_ref), '')           AS card_id,
    quantity,
    CAST(total_cost_cad AS DOUBLE)       AS total_cost_cad,
    acquired_on,
    coalesce(acquired_channel, 'OTHER')  AS acquired_channel,
    listed_on,
    CAST(listed_price_cad AS DOUBLE)     AS listed_price_cad,
    source
FROM raw_ledger_lots
