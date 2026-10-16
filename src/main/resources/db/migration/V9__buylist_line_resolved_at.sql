-- When each draft line finished resolving (card match and price), so the time from adding a card to it
-- being priced can be measured per card in production. NULL for lines resolved before this migration.
ALTER TABLE buylist_draft_lines ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP WITH TIME ZONE;
