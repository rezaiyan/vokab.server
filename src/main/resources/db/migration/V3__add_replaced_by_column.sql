-- Add all missing columns to refresh_tokens table for complete entity compatibility
-- This migration is safe to run on existing production databases

-- Add revoked_at column if it doesn't exist
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

-- Add created_at column if it doesn't exist
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Add revoked column if it doesn't exist
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS revoked BOOLEAN NOT NULL DEFAULT FALSE;

-- Add replaced_by column if it doesn't exist
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS replaced_by BIGINT;

