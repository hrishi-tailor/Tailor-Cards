-- Dimension: every card the store lists, holds or prices. Named from the site's product when there is one,
-- else from the ledger lot's title.
CREATE OR REPLACE VIEW dim_card AS
WITH ids AS (
    SELECT card_id FROM stg_products WHERE card_id IS NOT NULL
    UNION
    SELECT card_id FROM stg_lots WHERE card_id IS NOT NULL
    UNION
    SELECT card_id FROM stg_prices
),
product AS (
    SELECT card_id, max(name) AS name, max(card_set) AS card_set, max(card_number) AS card_number
    FROM stg_products
    WHERE card_id IS NOT NULL
    GROUP BY card_id
),
lot AS (
    SELECT card_id, max(title) AS title
    FROM stg_lots
    WHERE card_id IS NOT NULL
    GROUP BY card_id
)
SELECT
    ids.card_id,
    coalesce(product.name, lot.title, ids.card_id)  AS card_name,
    product.card_set,
    product.card_number,
    product.card_id IS NOT NULL                     AS listed_on_site,
    lot.card_id IS NOT NULL                         AS in_ledger
FROM ids
LEFT JOIN product ON product.card_id = ids.card_id
LEFT JOIN lot ON lot.card_id = ids.card_id
