-- Dimension: each purchase, with its unit cost. Kind codes match the app's ItemKind enum.
CREATE OR REPLACE VIEW dim_lot AS
SELECT
    lot_id,
    title,
    kind,
    CASE kind
        WHEN 'SINGLE'      THEN 'Single'
        WHEN 'GRADED'      THEN 'Graded slab'
        WHEN 'SEALED'      THEN 'Sealed product'
        WHEN 'SEALED_CASE' THEN 'Sealed case'
        WHEN 'LOT'         THEN 'Bulk lot'
        ELSE kind
    END                                         AS kind_name,
    card_id,
    quantity,
    total_cost_cad,
    round(total_cost_cad / quantity, 2)         AS unit_cost_cad,
    acquired_on,
    acquired_channel,
    listed_on,
    listed_price_cad
FROM stg_lots
