-- Categories: Only Singles (ID: 1) and Sealed (ID: 2)
INSERT INTO categories (id, name, description)
VALUES (1, 'Singles', 'Individual collectible trading cards, singles, and graded slabs')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;

INSERT INTO categories (id, name, description)
VALUES (2, 'Sealed', 'Factory sealed booster boxes, packs, and bundles')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;

-- Reassign any existing products referencing obsolete categories to Singles (1)
UPDATE products SET category_id = 1 WHERE category_id NOT IN (1, 2);

-- Clean up any obsolete categories
DELETE FROM categories WHERE id NOT IN (1, 2);

-- Synchronize the categories sequence
SELECT setval(pg_get_serial_sequence('categories', 'id'), (SELECT COALESCE(MAX(id), 2) FROM categories));

-- Clean up old accessory products if present
DELETE FROM cart_items WHERE product_id IN (SELECT id FROM products WHERE name IN ('Pro-Matte Card Sleeves (100-Pack)', 'Magnetic Dual Deck Box', '9-Pocket Premium Zip Binder'));
DELETE FROM products WHERE name IN ('Pro-Matte Card Sleeves (100-Pack)', 'Magnetic Dual Deck Box', '9-Pocket Premium Zip Binder');

-- Clean up legacy mock/starter products if present
DELETE FROM cart_items WHERE product_id IN (SELECT id FROM products WHERE name IN (
    'Charizard Holographic (Base Set)',
    'Black Lotus Art Commemorative',
    'Rookie Phenom Foil Baseball Card',
    'Pikachu Illustrator Promo (PSA 10)',
    '1986 Basketball Legend (BGS 9.5)',
    'Base Set 1st Edition Booster Box',
    'Modern Horizons II Collector Booster Box',
    'Vintage Neo Genesis Booster Pack'
));
DELETE FROM products WHERE name IN (
    'Charizard Holographic (Base Set)',
    'Black Lotus Art Commemorative',
    'Rookie Phenom Foil Baseball Card',
    'Pikachu Illustrator Promo (PSA 10)',
    '1986 Basketball Legend (BGS 9.5)',
    'Base Set 1st Edition Booster Box',
    'Modern Horizons II Collector Booster Box',
    'Vintage Neo Genesis Booster Pack'
);

-- Ensure products without verified images are removed
DELETE FROM cart_items WHERE product_id IN (SELECT id FROM products WHERE images IS NULL);
DELETE FROM products WHERE images IS NULL;

-- Trade Assistant: Buy Rules (Cash Payouts)
INSERT INTO buy_rules (priority, category_code, display_name, rate, active)
VALUES
    (1, 'PSA10_BGS_BLACK_LABEL', 'PSA 10 or BGS Black Label', 0.8200, true),
    (2, 'SEALED', 'Sealed product', 0.7000, true),
    (3, 'RAW_NEAR_MINT', 'Near-mint raw single', 0.7700, true),
    (4, 'DEFAULT', 'Everything else', 0.7500, true)
ON CONFLICT (category_code) DO UPDATE SET rate = EXCLUDED.rate, priority = EXCLUDED.priority, active = EXCLUDED.active;

-- Trade Assistant: Configurable Trade Parameters
INSERT INTO trade_parameters (param_key, param_value, description)
VALUES
    ('VARIABLE_RESALE_FEE_FRACTION', 0.1200, 'Variable resale cost, platform and payment fees fraction (f)'),
    ('FIXED_HANDLING_FEE_PER_CARD', 0.5000, 'Fixed handling cost per incoming card in CAD (F)'),
    ('TARGET_PROFIT_MARGIN', 0.0800, 'Target profit margin on store cards given (g)'),
    ('DEFAULT_COST_BASIS_RATIO', 0.7700, 'Fallback cost basis to list price ratio for store cards (c)'),
    ('HARD_CAP_RATE', 0.9000, 'Hard ceiling on trade credit rate (cap)'),
    ('COUNTER_FLOOR_RATE', 0.5500, 'Minimum allowable rate to issue counter offer with cash top-up'),
    ('COUNTER_MAX_TOPUP_RATIO', 0.2500, 'Maximum cash top-up allowed as fraction of store list price'),
    ('OPENING_OFFER_DISCOUNT', 0.0300, 'Discount applied to r_max for opening customer offers'),
    ('CONSOLIDATION_THRESHOLD_COUNT', 3.0000, 'Minimum card count to trigger consolidation review'),
    ('CONSOLIDATION_MIN_LARGEST_RATIO', 0.2500, 'Minimum largest card value ratio to target; below this declines'),
    ('CONSOLIDATION_PENALTY_THRESHOLD', 0.5000, 'Threshold below which consolidation penalty applies'),
    ('CONSOLIDATION_PENALTY', 0.0500, 'Rate penalty subtracted from r_max for 25%-50% consolidation'),
    ('MAX_CUSTOMER_CARDS_THRESHOLD', 8.0000, 'Maximum cards before trade requires in-person / manual review'),
    ('DEFAULT_LIQUIDITY_HAIRCUT_HIGH', 0.0000, 'Liquidity haircut for high-velocity cards'),
    ('DEFAULT_LIQUIDITY_HAIRCUT_MEDIUM', 0.0300, 'Liquidity haircut for medium-velocity cards'),
    ('DEFAULT_LIQUIDITY_HAIRCUT_LOW', 0.0800, 'Liquidity haircut for low-velocity / niche cards')
ON CONFLICT (param_key) DO UPDATE SET param_value = EXCLUDED.param_value, description = EXCLUDED.description;

-- Trade Assistant: Sample Liquidity Tags
INSERT INTO card_liquidity (pokemontcg_id, liquidity_tier, haircut, notes)
VALUES
    ('base1-4', 'HIGH', 0.0000, 'Base Set Charizard Holo - Premier Grail'),
    ('base1-2', 'HIGH', 0.0000, 'Base Set Blastoise Holo'),
    ('base1-15', 'HIGH', 0.0000, 'Base Set Venusaur Holo'),
    ('sm35-78', 'HIGH', 0.0000, 'Mewtwo GX Secret Rare Shining Legends'),
    ('swsh7-215', 'HIGH', 0.0000, 'Umbreon VMAX Alt Art Evolving Skies')
ON CONFLICT (pokemontcg_id) DO UPDATE SET haircut = EXCLUDED.haircut, liquidity_tier = EXCLUDED.liquidity_tier;

