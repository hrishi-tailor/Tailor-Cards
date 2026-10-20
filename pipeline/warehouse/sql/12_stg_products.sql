-- Staging: products listed on the site. One row per product.
CREATE OR REPLACE VIEW stg_products AS
SELECT
    id                               AS product_id,
    trim(name)                       AS name,
    category,
    card_set,
    card_number,
    condition,
    grading,
    CAST(price AS DOUBLE)            AS list_price_cad,
    stock,
    CAST(cost_basis AS DOUBLE)       AS cost_basis_cad,
    nullif(trim(pokemontcg_id), '')  AS card_id,
    status
FROM raw_products
