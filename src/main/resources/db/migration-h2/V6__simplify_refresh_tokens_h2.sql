-- Simplify refresh tokens by removing unnecessary metadata (H2 specific)
-- Keep only essential fields for token management

ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS family_id;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS replaced_by;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS device_id;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS user_agent;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS ip_address;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS revoked_at;

-- Drop the family_id index
DROP INDEX IF EXISTS idx_refresh_tokens_family_id;
