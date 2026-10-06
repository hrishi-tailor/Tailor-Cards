-- =============================================================================
-- update_images.sql
-- Alters products table to add images text[] and maps product photos
-- =============================================================================

-- 1. Add images and version columns if they do not already exist
ALTER TABLE products ADD COLUMN IF NOT EXISTS images text[];
ALTER TABLE products ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;

-- 2. Map each product ID to its verified array of relative image paths
UPDATE products SET images = ARRAY['/images/12-front.jpg', '/images/12-back.jpg', '/images/12-left.jpg', '/images/12-right.jpg'] WHERE id = 12;
UPDATE products SET images = ARRAY['/images/13-front.jpg', '/images/13-back.jpg'] WHERE id = 13;
UPDATE products SET images = ARRAY['/images/14-front.jpg', '/images/14-back.jpg'] WHERE id = 14;
UPDATE products SET images = ARRAY['/images/16-front.jpg', '/images/16-back.jpg'] WHERE id = 16;
UPDATE products SET images = ARRAY['/images/17-front.jpg', '/images/17-back.jpg'] WHERE id = 17;
UPDATE products SET images = ARRAY['/images/18-front.jpg', '/images/18-back.jpg'] WHERE id = 18;
UPDATE products SET images = ARRAY['/images/19-front.jpg', '/images/19-back.jpg'] WHERE id = 19;
UPDATE products SET images = ARRAY['/images/21-front.jpg', '/images/21-back.jpg'] WHERE id = 21;
UPDATE products SET images = ARRAY['/images/22-front.jpg', '/images/22-back.jpg'] WHERE id = 22;
UPDATE products SET images = ARRAY['/images/24-front.jpg', '/images/24-back.jpg'] WHERE id = 24;
UPDATE products SET images = ARRAY['/images/25-front.jpg', '/images/25-back.jpg'] WHERE id = 25;
UPDATE products SET images = ARRAY['/images/26-front.jpg', '/images/26-back.jpg'] WHERE id = 26;
UPDATE products SET images = ARRAY['/images/28-front.jpg', '/images/28-back.jpg'] WHERE id = 28;
UPDATE products SET images = ARRAY['/images/29-front.jpg', '/images/29-back.jpg'] WHERE id = 29;
UPDATE products SET images = ARRAY['/images/30-front.jpg', '/images/30-back.jpg'] WHERE id = 30;
UPDATE products SET images = ARRAY['/images/31-front.jpg', '/images/31-back.jpg'] WHERE id = 31;
UPDATE products SET images = ARRAY['/images/32-front.jpg', '/images/32-back.jpg'] WHERE id = 32;
UPDATE products SET images = ARRAY['/images/33-front.jpg', '/images/33-back.jpg'] WHERE id = 33;
UPDATE products SET images = ARRAY['/images/34-front.jpg', '/images/34-back.jpg'] WHERE id = 34;
UPDATE products SET images = ARRAY['/images/35-front.jpg', '/images/35-back.jpg'] WHERE id = 35;
UPDATE products SET images = ARRAY['/images/36-front.jpg', '/images/36-back.jpg'] WHERE id = 36;
UPDATE products SET images = ARRAY['/images/37-front.jpg', '/images/37-back.jpg'] WHERE id = 37;
UPDATE products SET images = ARRAY['/images/38-front.jpg', '/images/38-back.jpg'] WHERE id = 38;
UPDATE products SET images = ARRAY['/images/39-front.jpg', '/images/39-back.jpg'] WHERE id = 39;
UPDATE products SET images = ARRAY['/images/40-front.jpg'] WHERE id = 40;
UPDATE products SET images = ARRAY['/images/41-front.jpg', '/images/41-back.jpg'] WHERE id = 41;
UPDATE products SET images = ARRAY['/images/42-front.jpg', '/images/42-back.jpg'] WHERE id = 42;
UPDATE products SET images = ARRAY['/images/43-front.jpg', '/images/43-back.jpg'] WHERE id = 43;
UPDATE products SET images = ARRAY['/images/44-front.jpg', '/images/44-back.jpg'] WHERE id = 44;
UPDATE products SET images = ARRAY['/images/45-front.jpg', '/images/45-back.jpg'] WHERE id = 45;
UPDATE products SET images = ARRAY['/images/46-front.jpg', '/images/46-back.jpg'] WHERE id = 46;
UPDATE products SET images = ARRAY['/images/47-front.jpg', '/images/47-back.jpg'] WHERE id = 47;
UPDATE products SET images = ARRAY['/images/48-front.jpg', '/images/48-back.jpg'] WHERE id = 48;
UPDATE products SET images = ARRAY['/images/50-front.jpg', '/images/50-back.jpg'] WHERE id = 50;
UPDATE products SET images = ARRAY['/images/51-front.jpg', '/images/51-back.jpg'] WHERE id = 51;
UPDATE products SET images = ARRAY['/images/52-front.jpg', '/images/52-back.jpg'] WHERE id = 52;
UPDATE products SET images = ARRAY['/images/53-front.jpg', '/images/53-back.jpg'] WHERE id = 53;
UPDATE products SET images = ARRAY['/images/54-front.jpg', '/images/54-back.jpg'] WHERE id = 54;
UPDATE products SET images = ARRAY['/images/55-front.jpg', '/images/55-back.jpg'] WHERE id = 55;
UPDATE products SET images = ARRAY['/images/56-front.jpg', '/images/56-back.jpg'] WHERE id = 56;
UPDATE products SET images = ARRAY['/images/57-front.jpg', '/images/57-back.jpg'] WHERE id = 57;
UPDATE products SET images = ARRAY['/images/58-front.jpg', '/images/58-back.jpg'] WHERE id = 58;
UPDATE products SET images = ARRAY['/images/59-front.jpg', '/images/59-back.jpg'] WHERE id = 59;
UPDATE products SET images = ARRAY['/images/60-front.jpg', '/images/60-back.jpg'] WHERE id = 60;
UPDATE products SET images = ARRAY['/images/61-front.jpg'] WHERE id = 61;
UPDATE products SET images = ARRAY['/images/62-front.jpg'] WHERE id = 62;
UPDATE products SET images = ARRAY['/images/63-front.jpg', '/images/63-back.jpg'] WHERE id = 63;
UPDATE products SET images = ARRAY['/images/64-front.jpg', '/images/64-back.jpg'] WHERE id = 64;
UPDATE products SET images = ARRAY['/images/65-front.jpg', '/images/65-back.jpg'] WHERE id = 65;
UPDATE products SET images = ARRAY['/images/66-front.jpg', '/images/66-back.jpg'] WHERE id = 66;
UPDATE products SET images = ARRAY['/images/67-front.jpg', '/images/67-back.jpg'] WHERE id = 67;
UPDATE products SET images = ARRAY['/images/69-front.jpg', '/images/69-back.jpg'] WHERE id = 69;
UPDATE products SET images = ARRAY['/images/70-front.jpg', '/images/70-back.jpg'] WHERE id = 70;

-- 3. Synchronize primary image_url to the primary front image for cards with newly mapped images
UPDATE products SET image_url = images[1] WHERE images IS NOT NULL AND array_length(images, 1) > 0;
