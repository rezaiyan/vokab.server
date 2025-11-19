-- Add missing columns to refresh_tokens table for token rotation and device tracking.
-- This migration is safe to run on existing production databases.

-- Add family_id column (required for token rotation)
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS family_id VARCHAR(64);

-- Update existing rows to have a default family_id if they don't have one
-- This ensures backward compatibility with existing tokens
UPDATE refresh_tokens
SET family_id = 'legacy-' || id::text
WHERE family_id IS NULL;

-- Make family_id NOT NULL after setting defaults
ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

-- Add optional metadata columns for device tracking
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(255);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

-- Create index on family_id after the column is guaranteed to exist
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens(family_id);

