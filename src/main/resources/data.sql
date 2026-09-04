-- Categories
INSERT INTO categories (name, description)
VALUES ('Trading Cards', 'Collectible and tournament trading cards')
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description)
VALUES ('Accessories', 'Protective sleeves, deck boxes, and binders')
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, description)
VALUES ('Graded Cards', 'Authenticated and professionally graded collectible cards')
ON CONFLICT (name) DO NOTHING;

-- Products: Trading Cards
INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT 'Charizard Holographic (Base Set)', 'Classic holographic Charizard card in near-mint condition.', 349.99, 'https://images.unsplash.com/photo-1613771404784-3a5686aa2be3', 3, c.id
FROM categories c WHERE c.name = 'Trading Cards'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Charizard Holographic (Base Set)');

INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT 'Black Lotus Art Commemorative', 'Vintage-style commemorative art card featuring iconic artwork.', 89.50, 'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f', 12, c.id
FROM categories c WHERE c.name = 'Trading Cards'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Black Lotus Art Commemorative');

INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT 'Rookie Phenom Foil Baseball Card', 'Modern rookie card with premium foil finish and sharp corners.', 45.00, 'https://images.unsplash.com/photo-1579373903781-fd5c0c30c4cd', 25, c.id
FROM categories c WHERE c.name = 'Trading Cards'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Rookie Phenom Foil Baseball Card');

-- Products: Accessories
INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT 'Pro-Matte Card Sleeves (100-Pack)', 'Standard size anti-glare protective sleeves ideal for deck building.', 12.99, 'https://images.unsplash.com/photo-1593305841991-05c297ba4575', 150, c.id
FROM categories c WHERE c.name = 'Accessories'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Pro-Matte Card Sleeves (100-Pack)');

INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT 'Magnetic Dual Deck Box', 'Durable faux leather deck box with dual compartments and magnetic snap lock.', 24.99, 'https://images.unsplash.com/photo-1544717305-2782549b5136', 40, c.id
FROM categories c WHERE c.name = 'Accessories'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Magnetic Dual Deck Box');

INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT '9-Pocket Premium Zip Binder', 'Side-loading 20-page portfolio binder holding up to 360 sleeved cards.', 34.50, 'https://images.unsplash.com/photo-1589829085413-56de8ae18c73', 30, c.id
FROM categories c WHERE c.name = 'Accessories'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = '9-Pocket Premium Zip Binder');

-- Products: Graded Cards
INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT 'Pikachu Illustrator Promo (PSA 10)', 'Ultra-rare promotional card certified Gem Mint 10 in tamper-proof slab.', 1499.99, 'https://images.unsplash.com/photo-1613771404721-1f92d799e49f', 1, c.id
FROM categories c WHERE c.name = 'Graded Cards'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = 'Pikachu Illustrator Promo (PSA 10)');

INSERT INTO products (name, description, price, image_url, stock, category_id)
SELECT '1986 Basketball Legend (BGS 9.5)', 'Authenticated sub-center vintage basketball card in archival casing.', 599.00, 'https://images.unsplash.com/photo-1519766304817-4f37bda74a29', 2, c.id
FROM categories c WHERE c.name = 'Graded Cards'
AND NOT EXISTS (SELECT 1 FROM products WHERE name = '1986 Basketball Legend (BGS 9.5)');
