-- V8__buylist_condition_rates.sql
-- Buy rates for raw cards by condition (near mint stays RAW_NEAR_MINT, unknown stays DEFAULT).
-- LP+ means near mint apart from one small white dot. Editable in admin buy rules.
-- Idempotent because production was baselined at version 1.

INSERT INTO buy_rules (priority, category_code, display_name, rate, active)
VALUES
    (3, 'RAW_LP_PLUS', 'LP+ raw single (one white dot)', 0.6500, true),
    (3, 'RAW_LP', 'Lightly played raw single', 0.6000, true),
    (3, 'RAW_MP', 'Moderately played raw single', 0.4000, true),
    (3, 'RAW_HP', 'Heavily played raw single', 0.2000, true),
    (3, 'RAW_DMG', 'Damaged raw single', 0.1000, true)
ON CONFLICT DO NOTHING;
