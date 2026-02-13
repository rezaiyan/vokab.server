-- Remove AI extraction usage tracking column
-- This simplifies the user feature access model to just premium/not-premium

ALTER TABLE users DROP COLUMN IF EXISTS ai_extraction_usage_count;
