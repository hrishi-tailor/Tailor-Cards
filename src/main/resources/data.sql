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
