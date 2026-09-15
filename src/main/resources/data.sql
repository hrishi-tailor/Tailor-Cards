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

-- Products: Singles (Category 1)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Charizard Holographic (Base Set)', 'Classic holographic Charizard card in near-mint condition.', 349.99, 'https://images.unsplash.com/photo-1613771404784-3a5686aa2be3', 3, 1, '4/102', 'Base Set', 'Near Mint', 'Raw', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Charizard Holographic (Base Set)');

UPDATE products
SET category_id = 1,
    card_number = '4/102',
    card_set = 'Base Set',
    condition = 'Near Mint',
    grading = 'Raw',
    status = 'AVAILABLE'
WHERE name = 'Charizard Holographic (Base Set)';

INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Black Lotus Art Commemorative', 'Vintage-style commemorative art card featuring iconic artwork.', 89.50, 'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f', 12, 1, 'PR-01', 'Vintage Masters', 'Near Mint', 'Raw', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Black Lotus Art Commemorative');

UPDATE products
SET category_id = 1,
    card_number = 'PR-01',
    card_set = 'Vintage Masters',
    condition = 'Near Mint',
    grading = 'Raw',
    status = 'AVAILABLE'
WHERE name = 'Black Lotus Art Commemorative';

INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Rookie Phenom Foil Baseball Card', 'Modern rookie card with premium foil finish and sharp corners.', 45.00, 'https://images.unsplash.com/photo-1579373903781-fd5c0c30c4cd', 25, 1, 'US175', 'Topps Update', 'Mint', 'Raw', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Rookie Phenom Foil Baseball Card');

UPDATE products
SET category_id = 1,
    card_number = 'US175',
    card_set = 'Topps Update',
    condition = 'Mint',
    grading = 'Raw',
    status = 'AVAILABLE'
WHERE name = 'Rookie Phenom Foil Baseball Card';

INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pikachu Illustrator Promo (PSA 10)', 'Ultra-rare promotional card certified Gem Mint 10 in tamper-proof slab.', 1499.99, 'https://images.unsplash.com/photo-1613771404721-1f92d799e49f', 1, 1, 'Promo', 'CoroCoro Comics Promo', 'Gem Mint', 'PSA 10', 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Pikachu Illustrator Promo (PSA 10)');

UPDATE products
SET category_id = 1,
    card_number = 'Promo',
    card_set = 'CoroCoro Comics Promo',
    condition = 'Gem Mint',
    grading = 'PSA 10',
    status = 'AVAILABLE'
WHERE name = 'Pikachu Illustrator Promo (PSA 10)';

INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT '1986 Basketball Legend (BGS 9.5)', 'Authenticated vintage basketball card in archival casing.', 599.00, 'https://images.unsplash.com/photo-1519766304817-4f37bda74a29', 0, 1, '57', '1986 Fleer', 'Gem Mint', 'BGS 9.5', 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '1986 Basketball Legend (BGS 9.5)');

UPDATE products
SET category_id = 1,
    card_number = '57',
    card_set = '1986 Fleer',
    condition = 'Gem Mint',
    grading = 'BGS 9.5',
    status = 'SOLD'
WHERE name = '1986 Basketball Legend (BGS 9.5)';

-- Products: Sealed (Category 2)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Base Set 1st Edition Booster Box', 'Factory-sealed original 1999 1st Edition booster box containing 36 booster packs.', 12500.00, 'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f', 1, 2, NULL, 'Base Set', 'Factory Sealed', NULL, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Base Set 1st Edition Booster Box');

UPDATE products
SET category_id = 2,
    card_number = NULL,
    card_set = 'Base Set',
    condition = 'Factory Sealed',
    grading = NULL,
    status = 'AVAILABLE'
WHERE name = 'Base Set 1st Edition Booster Box';

INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Modern Horizons II Collector Booster Box', 'Factory-sealed collector booster box containing 12 collector booster packs.', 289.99, 'https://images.unsplash.com/photo-1544717305-2782549b5136', 8, 2, NULL, 'Modern Horizons II', 'Factory Sealed', NULL, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Modern Horizons II Collector Booster Box');

UPDATE products
SET category_id = 2,
    card_number = NULL,
    card_set = 'Modern Horizons II',
    condition = 'Factory Sealed',
    grading = NULL,
    status = 'AVAILABLE'
WHERE name = 'Modern Horizons II Collector Booster Box';

INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Vintage Neo Genesis Booster Pack', 'Vintage unweighed sealed booster pack from the Neo Genesis expansion.', 320.00, 'https://images.unsplash.com/photo-1613771404784-3a5686aa2be3', 0, 2, NULL, 'Neo Genesis', 'Factory Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Vintage Neo Genesis Booster Pack');

UPDATE products
SET category_id = 2,
    card_number = NULL,
    card_set = 'Neo Genesis',
    condition = 'Factory Sealed',
    grading = NULL,
    status = 'SOLD'
WHERE name = 'Vintage Neo Genesis Booster Pack';
