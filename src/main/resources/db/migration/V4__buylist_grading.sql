-- V4__buylist_grading.sql
-- Graded slabs (e.g. "PSA 10") on buylist chat lines. Idempotent: production was baselined at version 1.
ALTER TABLE buylist_draft_lines ADD COLUMN IF NOT EXISTS grading VARCHAR(30);
ALTER TABLE buylist_submission_lines ADD COLUMN IF NOT EXISTS grading VARCHAR(30);
