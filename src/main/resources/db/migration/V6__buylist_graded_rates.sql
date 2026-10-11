-- V6__buylist_graded_rates.sql
-- Graded prices: number of recent sales behind each median; buy rates for common graded tiers.
-- Idempotent (IF NOT EXISTS / ON CONFLICT) because production was baselined at version 1.

ALTER TABLE buylist_draft_lines ADD COLUMN IF NOT EXISTS price_sample_size INTEGER;

-- Graded tiers for the buylist (category GRADED_<COMPANY>_<GRADE>); editable in admin buy rules
INSERT INTO buy_rules (priority, category_code, display_name, rate, active)
VALUES
    (1, 'GRADED_PSA_9', 'PSA 9', 0.7800, true),
    (1, 'GRADED_CGC_10', 'CGC 10', 0.8000, true)
ON CONFLICT DO NOTHING;
