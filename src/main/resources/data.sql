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

-- =============================================================================
-- Sold Inventory Seed Records (Historical Sold Products)
-- =============================================================================
-- 1. Elite Trainer Box (Sealed - Prismatic Evolutions)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Elite Trainer Box', '1100', 220.00, '/images/prismatic-evolutions-etb.jpg', 0, 2, NULL, 'Prismatic Evolutions', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/prismatic-evolutions-etb.jpg');

-- 2. Elite Trainer Box (Sealed - Destined Rivals)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Elite Trainer Box', NULL, 160.00, '/images/destined-rivals-etb.jpg', 0, 2, NULL, 'Destined Rivals', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/destined-rivals-etb.jpg');

-- 3. Pokemon Center Elite Trainer Box (Sealed - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pokemon Center Elite Trainer Box', NULL, 360.00, '/images/phantasmal-flames-pc-etb-1.jpg', 0, 2, NULL, 'Phantasmal Flames', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/phantasmal-flames-pc-etb-1.jpg');

-- 4. Pokemon Center Elite Trainer Box (Sealed - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pokemon Center Elite Trainer Box', 'second unit sold', 350.00, '/images/phantasmal-flames-pc-etb-2.jpg', 0, 2, NULL, 'Phantasmal Flames', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/phantasmal-flames-pc-etb-2.jpg');

-- 5. Mewtwo (Single - Scarlet & Violet 151 (Promo))
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mewtwo', 'consolidated 4 copies sold across listings', 60.00, '/images/mewtwo-sv151-052.jpg', 0, 1, '52', 'Scarlet & Violet 151 (Promo)', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mewtwo-sv151-052.jpg');

-- 6. Tirtouga (Single - Black Bolt)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Tirtouga', NULL, 45.00, '/images/tirtouga-106-086-black-bolt.jpg', 0, 1, '106/086', 'Black Bolt', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/tirtouga-106-086-black-bolt.jpg');

-- 7. Zoroark VSTAR (Hisuian) (Single - Crown Zenith (Galarian Gallery))
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Zoroark VSTAR (Hisuian)', 'verify set', 65.00, '/images/zoroark-vstar-hisuian-gg56.jpg', 0, 1, 'GG56/GG70', 'Crown Zenith (Galarian Gallery)', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/zoroark-vstar-hisuian-gg56.jpg');

-- 8. Pokemon Center Elite Trainer Box (Sealed - Chaos Rising)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pokemon Center Elite Trainer Box', NULL, 145.00, '/images/chaos-rising-pc-etb.jpg', 0, 2, NULL, 'Chaos Rising', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/chaos-rising-pc-etb.jpg');

-- 9. Sealed Collection Lot (Lot - Mixed)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Sealed Collection Lot', 'bundle of multiple sealed items', 1050.00, '/images/sealed-collection-lot.jpg', 0, 1, NULL, 'Mixed', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/sealed-collection-lot.jpg');

-- 10. Mega Charizard X ex / Oricorio ex (2-pack) (Sealed - Universal Promo Card)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mega Charizard X ex / Oricorio ex (2-pack)', NULL, 65.00, '/images/mega-charizard-x-oricorio-upc-2pack.jpg', 0, 2, NULL, 'Universal Promo Card', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mega-charizard-x-oricorio-upc-2pack.jpg');

-- 11. Mew ex (Single - Scarlet & Violet 151 (Promo))
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mew ex', NULL, 100.00, '/images/mew-ex-sv052.jpg', 0, 1, 'SV052', 'Scarlet & Violet 151 (Promo)', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mew-ex-sv052.jpg');

-- 12. Chaos Rising Mini Lot (Lot - Chaos Rising)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Chaos Rising Mini Lot', NULL, 45.00, '/images/chaos-rising-mini-lot.jpg', 0, 1, NULL, 'Chaos Rising', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/chaos-rising-mini-lot.jpg');

-- 13. Metang (Single - Chaos Rising)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Metang', NULL, 10.00, '/images/metang-094-chaos-rising.jpg', 0, 1, '94', 'Chaos Rising', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/metang-094-chaos-rising.jpg');

-- 14. Emma (Single - Chaos Rising)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Emma', 'supporter/trainer card', 10.00, '/images/emma-107-chaos-rising.jpg', 0, 1, '107', 'Chaos Rising', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/emma-107-chaos-rising.jpg');

-- 15. Ampharos (Single - Chaos Rising)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Ampharos', NULL, 30.00, '/images/ampharos-090-chaos-rising.jpg', 0, 1, '90', 'Chaos Rising', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/ampharos-090-chaos-rising.jpg');

-- 16. Charizard Promo Lot (Lot - Mixed Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Charizard Promo Lot', NULL, 340.00, '/images/charizard-promo-lot.jpg', 0, 1, NULL, 'Mixed Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/charizard-promo-lot.jpg');

-- 17. Mega Charizard X EX (Single - Universal Promo Card)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mega Charizard X EX', NULL, 65.00, '/images/mega-charizard-x-ex-upc-023-1.jpg', 0, 1, '23', 'Universal Promo Card', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mega-charizard-x-ex-upc-023-1.jpg');

-- 18. Charizard GX (Single - SM Black Star Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Charizard GX', NULL, 75.00, '/images/charizard-gx-sm60.jpg', 0, 1, 'SM60', 'SM Black Star Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/charizard-gx-sm60.jpg');

-- 19. Charizard EX (Single - XY Black Star Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Charizard EX', NULL, 220.00, '/images/charizard-ex-xy121.jpg', 0, 1, 'XY121', 'XY Black Star Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/charizard-ex-xy121.jpg');

-- 20. Umbreon Prime (Single - Undaunted)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Umbreon Prime', NULL, 200.00, '/images/umbreon-prime-undaunted.jpg', 0, 1, NULL, 'Undaunted', 'HP/DMG', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/umbreon-prime-undaunted.jpg');

-- 21. Mewtwo VSTAR (Single - Pokemon GO)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mewtwo VSTAR', 'secret rare', 70.00, '/images/mewtwo-vstar-079-pokemon-go-1.jpg', 0, 1, '079/078', 'Pokemon GO', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mewtwo-vstar-079-pokemon-go-1.jpg');

-- 22. Zoroark (Single - White Flare)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Zoroark', NULL, 90.00, '/images/zoroark-143-white-flare-1.jpg', 0, 1, '143/086', 'White Flare', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/zoroark-143-white-flare-1.jpg');

-- 23. Pikachu (Sealed - SV Black Star Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pikachu', 'World Championships promo; sealed', 40.00, '/images/pikachu-190-sv-promo-sealed.jpg', 0, 2, '190', 'SV Black Star Promo', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/pikachu-190-sv-promo-sealed.jpg');

-- 24. Mega Gardevoir EX (Single - Mega Evolution Black Star Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mega Gardevoir EX', 'verify set name', 20.00, '/images/mega-gardevoir-ex-032.jpg', 0, 1, '32', 'Mega Evolution Black Star Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mega-gardevoir-ex-032.jpg');

-- 25. Mega Charizard EX (Single - Universal Promo Card)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mega Charizard EX', 'second unit', 70.00, '/images/mega-charizard-ex-upc-023-2.jpg', 0, 1, '23', 'Universal Promo Card', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mega-charizard-ex-upc-023-2.jpg');

-- 26. Mewtwo (Single - Scarlet & Violet 151 (Promo))
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mewtwo', 'second unit', 75.00, '/images/mewtwo-sv151-052-b.jpg', 0, 1, '52', 'Scarlet & Violet 151 (Promo)', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mewtwo-sv151-052-b.jpg');

-- 27. Mega Charizard EX (Single - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mega Charizard EX', NULL, 55.00, '/images/mega-charizard-ex-109-phantasmal-flames.jpg', 0, 1, '109/094', 'Phantasmal Flames', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mega-charizard-ex-109-phantasmal-flames.jpg');

-- 28. Eevee EX (Single - SV Black Star Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Eevee EX', 'verify set name', 40.00, '/images/eevee-ex-174.jpg', 0, 1, '174', 'SV Black Star Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/eevee-ex-174.jpg');

-- 29. Mewtwo VSTAR (Single - Pokemon GO)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mewtwo VSTAR', 'secret rare rainbow; second unit', 70.00, '/images/mewtwo-vstar-079-pokemon-go-2.jpg', 0, 1, '079/078', 'Pokemon GO', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mewtwo-vstar-079-pokemon-go-2.jpg');

-- 30. Zoroark (Single - White Flare)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Zoroark', 'second unit', 80.00, '/images/zoroark-143-white-flare-2.jpg', 0, 1, '143/086', 'White Flare', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/zoroark-143-white-flare-2.jpg');

-- 31. Dragonite EX (Single - Ascended Heroes)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Dragonite EX', 'mega attack rare; verify set', 90.00, '/images/dragonite-ex-271-217.jpg', 0, 1, '271/217', 'Ascended Heroes', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/dragonite-ex-271-217.jpg');

-- 32. First Partner Illustration Collection (Sealed - Scarlet & Violet 151)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'First Partner Illustration Collection', 'verify set', 70.00, '/images/first-partner-illustration-collection-1.jpg', 0, 2, NULL, 'Scarlet & Violet 151', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/first-partner-illustration-collection-1.jpg');

-- 33. Elite Trainer Box (Sealed - Ascended Heroes)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Elite Trainer Box', NULL, 250.00, '/images/ascended-heroes-etb.jpg', 0, 2, NULL, 'Ascended Heroes', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/ascended-heroes-etb.jpg');

-- 34. Mega Emboar EX Collection Box (Sealed - Ascended Heroes)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mega Emboar EX Collection Box', 'verify exact product name', 70.00, '/images/mega-emboar-ex-box-ascended-heroes.jpg', 0, 2, NULL, 'Ascended Heroes', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mega-emboar-ex-box-ascended-heroes.jpg');

-- 35. Tarragon (Single - Perfect Order)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Tarragon', 'verify set', 10.00, '/images/tarragon-116-088.jpg', 0, 1, '116/088', 'Perfect Order', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/tarragon-116-088.jpg');

-- 36. Pikachu (Single - McDonald's Promo 2013)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pikachu', NULL, 7.00, '/images/pikachu-mcdonalds-2013.jpg', 0, 1, NULL, 'McDonald''s Promo 2013', 'DMG', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/pikachu-mcdonalds-2013.jpg');

-- 37. Exeggutor (Dr. Ooyama) (Single - Evolutions)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Exeggutor (Dr. Ooyama)', 'verify set', 5.00, '/images/exeggutor-dr-ooyama.jpg', 0, 1, NULL, 'Evolutions', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/exeggutor-dr-ooyama.jpg');

-- 38. Marshadow & Incineroar GX (Single - Sun and Moon Base Set)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Marshadow & Incineroar GX', 'tag team; verify set/condition', 10.00, '/images/marshadow-incineroar-gx.jpg', 0, 1, NULL, 'Sun and Moon Base Set', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/marshadow-incineroar-gx.jpg');

-- 39. Gardevoir GX (Single - World Championship 2017 Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Gardevoir GX', NULL, 10.00, '/images/gardevoir-gx-worlds-2017.jpg', 0, 1, NULL, 'World Championship 2017 Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/gardevoir-gx-worlds-2017.jpg');

-- 40. BREAK Mini Lot (Lot - Mixed)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'BREAK Mini Lot', NULL, 50.00, '/images/break-mini-lot.jpg', 0, 1, NULL, 'Mixed', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/break-mini-lot.jpg');

-- 41. Marshadow GX (Single - Sun and Moon Base Set)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Marshadow GX', 'verify set/condition', 5.00, '/images/marshadow-gx.jpg', 0, 1, NULL, 'Sun and Moon Base Set', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/marshadow-gx.jpg');

-- 42. First Partner Illustration Collection (Sealed - Scarlet & Violet 151)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'First Partner Illustration Collection', 'verify set; second unit', 70.00, '/images/first-partner-illustration-collection-2.jpg', 0, 2, NULL, 'Scarlet & Violet 151', 'Sealed', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/first-partner-illustration-collection-2.jpg');

-- 43. Mew EX (Single - Scarlet & Violet 151 (Promo))
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mew EX', NULL, 150.00, '/images/mew-ex-151-upc-psa9.jpg', 0, 1, NULL, 'Scarlet & Violet 151 (Promo)', 'NM', 'PSA 9', 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mew-ex-151-upc-psa9.jpg');

-- 44. Pikachu V (Single - Lost Origins)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pikachu V', 'verify set (Trainer Gallery insert)', 140.00, '/images/pikachu-v-tg16-tg30.jpg', 0, 1, 'TG16/TG30', 'Lost Origins', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/pikachu-v-tg16-tg30.jpg');

-- 45. Mew ex (Single - Scarlet & Violet 151 (Promo))
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Mew ex', '2 units sold same listing', 160.00, '/images/mew-ex-151-upc-psa9-batch.jpg', 0, 1, NULL, 'Scarlet & Violet 151 (Promo)', 'NM', 'PSA 9', 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/mew-ex-151-upc-psa9-batch.jpg');

-- 46. Pikachu (Single - Generations: Radiant Collection)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Pikachu', 'full art; verify set', 80.00, '/images/pikachu-full-art-rc29.jpg', 0, 1, 'RC29/RC32', 'Generations: Radiant Collection', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/pikachu-full-art-rc29.jpg');

-- 47. Groudon EX (Single - Team Magma's)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Groudon EX', 'verify exact set name', 160.00, '/images/groudon-ex-team-magma-1.jpg', 0, 1, NULL, 'Team Magma''s', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/groudon-ex-team-magma-1.jpg');

-- 48. Venusaur EX (Single - Promo)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Venusaur EX', 'verify set', 175.00, '/images/venusaur-ex.jpg', 0, 1, NULL, 'Promo', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/venusaur-ex.jpg');

-- 49. Groudon EX (Single - Team Magma's)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Groudon EX', 'second unit; verify set', 320.00, '/images/groudon-ex-team-magma-2.jpg', 0, 1, NULL, 'Team Magma''s', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/groudon-ex-team-magma-2.jpg');

-- 50. Clefairy (Single - Perfect Order)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Clefairy', NULL, 55.00, '/images/clefairy-perfect-order.jpg', 0, 1, NULL, 'Perfect Order', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/clefairy-perfect-order.jpg');

-- 51. Eevee (Single - Pokemon GO)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Eevee', 'radiant rare', 35.00, '/images/radiant-eevee-pokemon-go.jpg', 0, 1, NULL, 'Pokemon GO', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/radiant-eevee-pokemon-go.jpg');

-- 52. Alcremie VMAX (Single - Shining Fates)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Alcremie VMAX', NULL, 35.00, '/images/alcremie-vmax-073-shining-fates.jpg', 0, 1, '73', 'Shining Fates', 'NM', 'CGC 10', 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/alcremie-vmax-073-shining-fates.jpg');

-- 53. Zeraora V (Single - Chilling Reign)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Zeraora V', NULL, 220.00, '/images/zeraora-v-chilling-reign-psa10-1.jpg', 0, 1, NULL, 'Chilling Reign', 'NM', 'PSA 10', 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/zeraora-v-chilling-reign-psa10-1.jpg');

-- 54. Piplup (Single - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Piplup', NULL, 20.00, '/images/piplup-098-phantasmal-flames.jpg', 0, 1, '98', 'Phantasmal Flames', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/piplup-098-phantasmal-flames.jpg');

-- 55. Charizard EX (Single - Flashfire)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Charizard EX', 'verify set', 375.00, '/images/charizard-ex-108-106.jpg', 0, 1, '108/106', 'Flashfire', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/charizard-ex-108-106.jpg');

-- 56. Wigglytuff (Single - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Wigglytuff', 'card number not recorded', 10.00, '/images/wigglytuff-phantasmal-flames.jpg', 0, 1, NULL, 'Phantasmal Flames', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/wigglytuff-phantasmal-flames.jpg');

-- 57. Phantasmal Flames Rare Set (Bundle) (Lot - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Phantasmal Flames Rare Set (Bundle)', NULL, 40.00, '/images/phantasmal-flames-rare-bundle.jpg', 0, 1, NULL, 'Phantasmal Flames', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/phantasmal-flames-rare-bundle.jpg');

-- 58. Zacian (Single - Phantasmal Flames)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Zacian', NULL, 5.00, '/images/zacian-100-phantasmal-flames.jpg', 0, 1, '100', 'Phantasmal Flames', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/zacian-100-phantasmal-flames.jpg');

-- 59. Scorbunny (Single - Ascended Heroes)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Scorbunny', NULL, 15.00, '/images/scorbunny-225-ascended-heroes.jpg', 0, 1, '225', 'Ascended Heroes', 'NM', NULL, 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/scorbunny-225-ascended-heroes.jpg');

-- 60. Zeraora V (Single - Chilling Reign)
INSERT INTO products (name, description, price, image_url, stock, category_id, card_number, card_set, condition, grading, status)
SELECT 'Zeraora V', 'separate batch sale of 3 units', 220.00, '/images/zeraora-v-chilling-reign-psa10-batch.jpg', 0, 1, NULL, 'Chilling Reign', 'NM', 'PSA 10', 'SOLD'
WHERE NOT EXISTS (SELECT 1 FROM products WHERE image_url = '/images/zeraora-v-chilling-reign-psa10-batch.jpg');
