-- Bookkeeping notes from the sales-history import ("verify set", "second unit", a stray "1100") landed in the
-- public description and showed in the shop's quick view. Move them to an admin-only column that the API never
-- returns, so the record keeps them and customers don't see them.
ALTER TABLE products ADD COLUMN IF NOT EXISTS admin_notes VARCHAR(1000);

UPDATE products
SET admin_notes = description,
    description = NULL
WHERE description ~* '(verify|second unit|consolidated|units sold|not recorded|supporter/trainer card)'
   OR description ~ '^\s*[0-9]+\s*$';
