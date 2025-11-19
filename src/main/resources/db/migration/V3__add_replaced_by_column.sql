-- Add replaced_by column to refresh_tokens table if it doesn't exist
-- This migration is safe to run on existing production databases

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS replaced_by BIGINT;

