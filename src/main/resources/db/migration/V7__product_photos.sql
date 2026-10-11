-- V7__product_photos.sql
-- The seller's own photos of each product, in display order. The product's image_url stays the
-- default picture (normally the official card image); these are shown after it.
-- Idempotent because production was baselined at version 1.

CREATE TABLE IF NOT EXISTS product_photos (
    product_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    url VARCHAR(500) NOT NULL,
    CONSTRAINT pk_product_photos PRIMARY KEY (product_id, position),
    CONSTRAINT fk_product_photos_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
