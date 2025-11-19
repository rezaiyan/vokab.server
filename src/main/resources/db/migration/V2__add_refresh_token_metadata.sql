-- Ensure new metadata columns exist on refresh_tokens for device tracking.

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(255);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

